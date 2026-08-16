/*
 * Copyright 2026 refinex.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.TriggerRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 创建可幂等登记的持久 Trigger，并为 Cron 原子建立首个 Cursor。
 *
 * @author refinex
 */
public final class TriggerDefinitionService {

    /**
     * 配置键允许的稳定字符。
     */
    private static final Pattern CONFIG_KEY =
        Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");

    /**
     * 禁止配置键暗示包含敏感值。
     */
    private static final Pattern SENSITIVE_KEY =
        Pattern.compile("(?i).*(secret|token|password|credential|api.?key).*");

    /**
     * Trigger 权威仓储。
     */
    private final TriggerRepository repository;

    /**
     * Cron 时间计算器。
     */
    private final CronCalculator calculator;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * 创建 Trigger 定义服务。
     *
     * @param repository Trigger 仓储
     * @param calculator Cron 计算器
     * @param clock      UTC 时钟
     */
    public TriggerDefinitionService(
        TriggerRepository repository, CronCalculator calculator, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建或幂等复用语义完全相同的 Trigger。
     *
     * @param command 创建命令
     * @return 持久 Trigger
     */
    public TriggerDefinition create(CreateTriggerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Map<String, String> config = validateConfig(command.config());
        Optional<ZoneId> zoneId = command.zoneId().map(ZoneId::of);
        command.secretRef().ifPresent(SecretRef::parse);
        Optional<TriggerDefinition> existing = repository.findByKey(
            command.organizationId(), command.projectId(), command.key());
        if (existing.isPresent()) {
            TriggerDefinition value = existing.orElseThrow();
            if (sameDefinition(value, command, zoneId, config)) {
                return value;
            }
            throw new SchedulerException(
                "TRIGGER_KEY_CONFLICT", "trigger key already has a different definition");
        }
        Instant now = clock.instant();
        UUID triggerId = SchedulerUuidV7.generate(now);
        TriggerDefinition trigger = new TriggerDefinition(
            triggerId, command.organizationId(), command.projectId(), command.key(),
            command.type(), command.cronExpression(), zoneId, config, command.secretRef(),
            command.targetContract(), command.targetJobType(), TriggerStatus.ENABLED,
            0, now, now);
        Optional<TriggerCursor> cursor = command.type() == TriggerType.CRON
            ? Optional.of(new TriggerCursor(
            triggerId,
            calculator.next(
                command.cronExpression().orElseThrow(), zoneId.orElseThrow(), now),
            Optional.empty(), Optional.empty(), 0))
            : Optional.empty();
        SchedulerOutbox outbox = new SchedulerOutbox(
            SchedulerUuidV7.generate(now), "trigger", triggerId, "trigger.created",
            "{\"triggerId\":\"" + triggerId + "\",\"type\":\""
                + command.type().name() + "\"}",
            OutboxStatus.PENDING, now, 0, now);
        try {
            repository.insert(trigger, cursor, outbox);
            return trigger;
        } catch (RuntimeException exception) {
            TriggerDefinition concurrent = repository.findByKey(
                    command.organizationId(), command.projectId(), command.key())
                .orElseThrow(() -> exception);
            if (sameDefinition(concurrent, command, zoneId, config)) {
                return concurrent;
            }
            throw new SchedulerException(
                "TRIGGER_KEY_CONFLICT", "concurrent trigger has a different definition");
        }
    }

    /**
     * 校验配置不含敏感字段并形成稳定顺序。
     *
     * @param source 原始配置
     * @return 稳定配置
     */
    private Map<String, String> validateConfig(Map<String, String> source) {
        Objects.requireNonNull(source, "config must not be null");
        if (source.size() > 32) {
            throw new IllegalArgumentException("trigger config must contain at most 32 entries");
        }
        TreeMap<String, String> result = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !CONFIG_KEY.matcher(key).matches()
                || SENSITIVE_KEY.matcher(key).matches()
                || key.startsWith("_trigger")) {
                throw new IllegalArgumentException("trigger config key is invalid or sensitive");
            }
            if (value == null || value.length() > 16_384) {
                throw new IllegalArgumentException("trigger config value exceeds limit");
            }
            result.put(key, value);
        });
        return Map.copyOf(result);
    }

    /**
     * 比较影响点火行为的全部不可变字段。
     *
     * @param existing 已存在定义
     * @param command  新命令
     * @param zoneId   已解析时区
     * @param config   已校验配置
     * @return 语义相同时为 true
     */
    private boolean sameDefinition(
        TriggerDefinition existing,
        CreateTriggerCommand command,
        Optional<ZoneId> zoneId,
        Map<String, String> config) {
        return existing.type() == command.type()
            && existing.cronExpression().equals(command.cronExpression())
            && existing.zoneId().equals(zoneId)
            && existing.config().equals(config)
            && existing.secretRef().equals(command.secretRef())
            && existing.targetContract().equals(command.targetContract())
            && existing.targetJobType() == command.targetJobType();
    }

    /**
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            项目内稳定 Key
     * @param type           Trigger 类型
     * @param cronExpression Cron 表达式
     * @param zoneId         IANA 时区
     * @param config         不含敏感值的目标 Job Payload 字段
     * @param secretRef      Webhook 验签 SecretRef
     * @param targetContract 目标 Payload Contract
     * @param targetJobType  目标 Job 类型
     * @author refinex
     */
    public record CreateTriggerCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        TriggerType type,
        Optional<String> cronExpression,
        Optional<String> zoneId,
        Map<String, String> config,
        Optional<String> secretRef,
        String targetContract,
        JobType targetJobType) {

        /**
         * 校验命令容器不为 null，类型专属约束由领域对象统一执行。
         */
        public CreateTriggerCommand {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(type, "type must not be null");
            cronExpression = Objects.requireNonNull(
                cronExpression, "cronExpression must not be null");
            zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
            config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
            secretRef = Objects.requireNonNull(secretRef, "secretRef must not be null");
        }
    }
}
