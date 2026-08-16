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

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.port.TriggerRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 将到期 Cron Cursor 原子推进为幂等 Durable Job，绝不直接执行 Agent。
 *
 * @author refinex
 */
public final class CronTriggerService {

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
     * 目标 Job Payload 规范序列化器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 Cron 点火服务。
     *
     * @param repository Trigger 仓储
     * @param calculator Cron 计算器
     * @param clock      UTC 时钟
     * @param jsonMapper JSON 映射器
     */
    public CronTriggerService(
        TriggerRepository repository,
        CronCalculator calculator,
        Clock clock,
        JsonMapper jsonMapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 扫描一批到期 Cursor；并发实例通过 Cursor Version 和 Job 唯一键只允许一个成功点火。
     *
     * @param limit 单轮最大 Trigger 数
     * @return 成功创建 Job 数
     */
    public int fireDue(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("cron scan limit must be between 1 and 500");
        }
        Instant now = clock.instant();
        int fired = 0;
        for (TriggerRepository.DueTrigger due : repository.findDue(now, limit)) {
            TriggerDefinition trigger = due.trigger();
            TriggerCursor cursor = due.cursor();
            Instant scheduledAt = cursor.nextFireAt();
            Instant nextFireAt = calculator.next(
                trigger.cronExpression().orElseThrow(), trigger.zoneId().orElseThrow(), scheduledAt);
            String fireToken = trigger.id() + ":" + scheduledAt;
            TreeMap<String, String> payloadFields = new TreeMap<>(trigger.config());
            payloadFields.put("_triggerId", trigger.id().toString());
            payloadFields.put("_triggerScheduledAt", scheduledAt.toString());
            payloadFields.put("_triggerContract", trigger.targetContract());
            String payload = jsonMapper.writeValueAsString(payloadFields);
            RetryPolicy policy = new RetryPolicy(
                5, Duration.ofSeconds(5), Duration.ofMinutes(15), 2.0, 0.2,
                Duration.ofMinutes(5));
            Job job = new Job(
                JobId.generate(), trigger.organizationId(), trigger.projectId(),
                trigger.targetJobType(), "cron:" + fireToken, payload, Checksum.sha256(payload),
                JobStatus.READY, 0, scheduledAt, policy, IdempotencyCapability.PROVIDER_KEY,
                0, 0, now, now);
            if (repository.fire(
                trigger, cursor, job, scheduledAt, nextFireAt, fireToken,
                SchedulerApplicationService.outbox(job.id(), "job.accepted", now))) {
                fired++;
            }
        }
        return fired;
    }
}
