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

package space.refinex.agentark.server.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import space.refinex.agentark.foundation.security.AudienceValidator;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.*;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 使用真实 Runtime、临时 RSA JWT 与确定性本地执行引擎启动浏览器 E2E。
 *
 * @author refinex
 */
public final class RuntimeE2eApplication {

    /** 禁止实例化测试启动器。 */
    private RuntimeE2eApplication() {
    }

    /**
     * 启动带 Test Classpath 配置的 Runtime。
     *
     * @param args Spring Boot 参数
     */
    public static void main(String[] args) {
        SpringApplication.from(AgentArkRuntimeApplication::main)
            .with(E2eRuntimeConfiguration.class)
            .run(args);
    }

    /**
     * 定义 Runtime E2E JWT Decoder 和只在测试 Classpath 存在的确定性 Engine。
     *
     * @author refinex
     */
    @TestConfiguration(proxyBeanMethods = false)
    public static class E2eRuntimeConfiguration {

        /**
         * 创建面向 Runtime Audience 的真实 RS256 Decoder。
         *
         * @return E2E JWT Decoder
         */
        @Bean
        @Primary
        public JwtDecoder e2eJwtDecoder() {
            return decoder("agentark-runtime");
        }

        /**
         * 创建会产生 Message、Usage、Approval 和 Checkpoint 的确定性测试执行引擎。
         *
         * @param signalSink      持久信号接收器
         * @param stateStore      Agent State Store
         * @param checkpointStore Checkpoint Store
         * @return Test-only 执行引擎
         */
        @Bean
        @Primary
        public AgentExecutionEngine e2eAgentExecutionEngine(
            ExecutionSignalSink signalSink,
            AgentStateStore stateStore,
            CheckpointStore checkpointStore) {
            return new E2eAgentExecutionEngine(signalSink, stateStore, checkpointStore);
        }
    }

    /**
     * 确定性执行引擎只服务浏览器 E2E，不连接模型、网络或生产 Provider。
     *
     * @author refinex
     */
    private static final class E2eAgentExecutionEngine implements AgentExecutionEngine {

        /** 持久执行信号接收器。 */
        private final ExecutionSignalSink signalSink;

        /** Provider 中立 Agent State Store。 */
        private final AgentStateStore stateStore;

        /** Provider 中立 Checkpoint Store。 */
        private final CheckpointStore checkpointStore;

        /**
         * 创建确定性引擎。
         *
         * @param signalSink      持久执行信号接收器
         * @param stateStore      Agent State Store
         * @param checkpointStore Checkpoint Store
         */
        private E2eAgentExecutionEngine(
            ExecutionSignalSink signalSink,
            AgentStateStore stateStore,
            CheckpointStore checkpointStore) {
            this.signalSink = signalSink;
            this.stateStore = stateStore;
            this.checkpointStore = checkpointStore;
        }

        /**
         * 产生可恢复 HITL 暂停，参数只以 Hash 持久化。
         */
        @Override
        public ExecutionResult execute(
            Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input) {
            signalSink.emit(session, run, new ExecutionSignal(
                "message.text.delta", RuntimePayload.inline("{\"text\":\"准备执行受控工具。\"}")));
            signalSink.emit(session, run, new ExecutionSignal(
                "model.call.completed", RuntimePayload.inline(
                "{\"inputTokens\":12,\"outputTokens\":8,\"durationMillis\":25}")));
            Checksum arguments = Checksum.sha256("{\"operation\":\"verify\"}");
            signalSink.emit(session, run, new ExecutionSignal(
                "approval.requested", RuntimePayload.inline(
                "{\"toolCalls\":[{\"toolCallId\":\"e2e-tool-call\","
                    + "\"toolName\":\"e2e.verify\",\"argumentHash\":\""
                    + arguments + "\"}]}")));
            persistCheckpoint(session, run);
            return new ExecutionResult(
                ExecutionOutcome.PAUSED, Optional.empty(), Optional.empty());
        }

        /**
         * 审批后产生最终流式文本并成功完成。
         */
        @Override
        public ExecutionResult resume(
            Session session, Run run, SnapshotDescriptor snapshot, ResumeCommand command) {
            signalSink.emit(session, run, new ExecutionSignal(
                "tool.call.completed", RuntimePayload.inline(
                "{\"toolName\":\"e2e.verify\",\"status\":\"SUCCEEDED\"}")));
            signalSink.emit(session, run, new ExecutionSignal(
                "message.text.delta", RuntimePayload.inline("{\"text\":\"审批通过，运行完成。\"}")));
            return new ExecutionResult(
                ExecutionOutcome.SUCCEEDED, Optional.empty(), Optional.empty());
        }

        /**
         * E2E 不模拟进程崩溃恢复，意外调用形成明确失败。
         */
        @Override
        public ExecutionResult recover(
            Session session, Run run, SnapshotDescriptor snapshot, Checkpoint checkpoint) {
            return new ExecutionResult(
                ExecutionOutcome.FAILED,
                Optional.of("E2E_RECOVERY_NOT_CONFIGURED"),
                Optional.of("E2E scenario only covers approval resume"));
        }

        /**
         * Test Engine 没有外部副作用，取消由 Runtime 权威状态机完成。
         */
        @Override
        public void cancel(CancellationCommand command) {
            // Test-only Engine 不持有需要关闭的外部资源。
        }

        /**
         * 写入已提交 State 和可恢复 Checkpoint，为 Approval Resume 提供证据。
         */
        private void persistCheckpoint(Session session, Run run) {
            Instant now = Instant.now();
            String stateJson = "{\"phase\":\"waiting-approval\"}";
            AgentStateVersion state = new AgentStateVersion(
                JobId.generate(), session.organizationId(), session.projectId(), session.id(),
                run.id(), "e2e-agent", "approval", 0, 1, RuntimePayload.inline(stateJson),
                Checksum.sha256(stateJson), false, run.fencingToken(), now);
            stateStore.append(state);
            stateStore.commit(state, run.fencingToken());
            checkpointStore.append(new Checkpoint(
                JobId.generate(), run.id(), 1, state.id(), state.stateVersion(), 1,
                Checksum.sha256("e2e-checkpoint"), true, run.fencingToken(), now));
        }
    }

    /**
     * 从临时 X.509 RSA 公钥创建 Decoder。
     *
     * @param audience 当前服务 Audience
     * @return 带 Issuer 与 Audience Validator 的 Decoder
     */
    private static JwtDecoder decoder(String audience) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required("AGENTARK_E2E_PUBLIC_KEY"));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                JwtValidators.createDefaultWithIssuer(required("AGENTARK_E2E_ISSUER")),
                new AudienceValidator(java.util.Set.of(audience))));
            return decoder;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("E2E RSA public key is invalid", exception);
        }
    }

    /**
     * 读取必需且非空的 E2E 环境变量，不输出其内容。
     *
     * @param name 环境变量名称
     * @return 非空值
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for E2E");
        }
        return value;
    }
}
