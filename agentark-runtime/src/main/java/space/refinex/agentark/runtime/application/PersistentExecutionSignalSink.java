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

package space.refinex.agentark.runtime.application;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.refinex.agentark.kernel.id.EventId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 Provider 中立信号先持久化为 Event，并把 Approval Requested 同事务转换为 HITL 事实。
 *
 * @author refinex
 */
public class PersistentExecutionSignalSink implements ExecutionSignalSink {

    /**
     * 默认 Approval 有效期。
     */
    private static final Duration APPROVAL_TTL = Duration.ofMinutes(15);

    /**
     * Runtime 聚合仓储。
     */
    private final RuntimeRepository repository;

    /**
     * Runtime Event 事实仓储。
     */
    private final RuntimeEventStore eventStore;

    /**
     * 追加式原始用量记录端口。
     */
    private final UsageRecorder usageRecorder;

    /**
     * Approval 应用服务。
     */
    private final RuntimeApplicationService runtimeService;

    /**
     * 大载荷外置端口。
     */
    private final RuntimePayloadExternalizer payloadExternalizer;

    /**
     * 事务后 Event 通知端口。
     */
    private final RuntimeEventNotifier eventNotifier;

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建持久执行信号接收器。
     *
     * @param repository          Runtime 聚合仓储
     * @param eventStore          Event Store
     * @param usageRecorder       原始用量记录端口
     * @param runtimeService      Runtime 应用服务
     * @param payloadExternalizer 大载荷外置端口
     * @param eventNotifier       Event 通知端口
     * @param objectMapper        JSON 解析器
     * @param clock               UTC 时钟
     */
    public PersistentExecutionSignalSink(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        UsageRecorder usageRecorder,
        RuntimeApplicationService runtimeService,
        RuntimePayloadExternalizer payloadExternalizer,
        RuntimeEventNotifier eventNotifier,
        ObjectMapper objectMapper,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.usageRecorder = Objects.requireNonNull(
            usageRecorder, "usageRecorder must not be null");
        this.runtimeService = Objects.requireNonNull(
            runtimeService, "runtimeService must not be null");
        this.payloadExternalizer = Objects.requireNonNull(
            payloadExternalizer, "payloadExternalizer must not be null");
        this.eventNotifier = Objects.requireNonNull(
            eventNotifier, "eventNotifier must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 以当前 Fencing Token 追加 Event；Approval Requested 同事务创建参数 Hash 固定的 Approval。
     *
     * @param session 当前 Session
     * @param run     当前 Run
     * @param signal  Provider 中立信号
     */
    @Override
    @Transactional
    public void emit(Session session, Run run, ExecutionSignal signal) {
        Turn turn = repository.findTurn(run.turnId())
            .orElseThrow(() -> new RuntimeNotFoundException("turn is not available"));
        Instant now = Instant.now(clock);
        String json = signal.payload().inlineJson().orElseThrow(() ->
            new IllegalArgumentException("provider signal must use inline JSON before persistence"));
        RuntimeEvent event = eventStore.append(
            EventId.generate(), session.organizationId(), session.projectId(), session.id(),
            turn.id(), run.id(), signal.type(), 1, run.id().asString().replace("-", ""),
            payloadExternalizer.externalize(json), now, run.fencingToken());
        if ("approval.requested".equals(signal.type())) {
            persistApprovals(run, session, json);
        }
        if ("model.call.completed".equals(signal.type())) {
            persistUsage(run, event, json, now);
        }
        afterCommit(() -> eventNotifier.publish(event.sessionId(), event.sessionSequence()));
    }

    /**
     * 将 AgentScope 已脱敏的 Model Usage 转换为 Provider 中立原始计量事实。
     *
     * @param run   当前 Run
     * @param event 证明用量的已持久 Event
     * @param json  Model Call Completed JSON
     * @param now   用量发生时刻
     */
    private void persistUsage(Run run, RuntimeEvent event, String json, Instant now) {
        JsonNode root = read(json);
        if (root.get("inputTokens") == null && root.get("outputTokens") == null
            && root.get("durationMillis") == null) {
            return;
        }
        String replyId = optionalText(root, "replyId");
        usageRecorder.record(new UsageRecord(
            EventId.generate(), run.id(), event.id(), run.runtimeProvider(), Optional.empty(),
            Optional.empty(), replyId.isBlank() ? Optional.empty() : Optional.of(replyId),
            nonNegativeLong(root, "inputTokens"), nonNegativeLong(root, "outputTokens"),
            nonNegativeLong(root, "durationMillis"), false, Optional.empty(), now));
    }

    /**
     * 从已脱敏信号读取 Tool Call ID、名称与参数 Hash，禁止读取原始参数。
     *
     * @param run     当前 Run
     * @param session 固定 Session
     * @param json    Approval Requested JSON
     */
    private void persistApprovals(Run run, Session session, String json) {
        JsonNode tools = read(json).get("toolCalls");
        if (tools == null || !tools.isArray() || tools.isEmpty()) {
            throw new IllegalArgumentException("approval request contains no tool calls");
        }
        tools.forEach(tool -> {
            String toolCallId = text(tool, "toolCallId");
            if (toolCallId.length() > 115) {
                throw new IllegalArgumentException("approval toolCallId is too long");
            }
            runtimeService.requestApproval(
                run.id(), text(tool, "toolName"), "TOOL_EXECUTE:" + toolCallId,
                new Checksum(text(tool, "argumentHash")),
                "snapshot:" + session.snapshotHash().hex().substring(0, 16), APPROVAL_TTL);
        });
    }

    /**
     * 解析已由 Provider Mapper 生成的 JSON。
     *
     * @param json JSON 文本
     * @return JSON 根节点
     */
    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("provider signal payload is invalid", exception);
        }
    }

    /**
     * 读取必需的非空文本字段。
     *
     * @param node JSON 节点
     * @param name 字段名
     * @return 文本值
     */
    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException("provider approval field is invalid: " + name);
        }
        return value.stringValue();
    }

    /**
     * 读取可缺失文本字段。
     *
     * @param node JSON 节点
     * @param name 字段名
     * @return 文本或空串
     */
    private String optionalText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isString() ? value.stringValue() : "";
    }

    /**
     * 读取可缺失的非负整数用量字段。
     *
     * @param node JSON 节点
     * @param name 字段名
     * @return 缺失时为零的非负值
     */
    private long nonNegativeLong(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null) {
            return 0;
        }
        if (!value.isIntegralNumber() || value.longValue() < 0) {
            throw new IllegalArgumentException("provider usage field is invalid: " + name);
        }
        return value.longValue();
    }

    /**
     * 在事务存在时提交后发布提示，否则立即发布。
     *
     * @param action 发布动作
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务成功提交后发送提示。
             */
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
