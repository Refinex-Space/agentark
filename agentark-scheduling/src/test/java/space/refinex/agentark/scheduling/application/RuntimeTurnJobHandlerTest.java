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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.JobHandler.HandlerResult;
import space.refinex.agentark.scheduling.port.RuntimeInternalClient;
import space.refinex.agentark.scheduling.port.RuntimeInternalClient.RuntimeTurnCommand;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 Runtime Turn Handler 只构造版本化 Internal Command，并使用稳定 Provider 幂等键。
 *
 * @author refinex
 */
class RuntimeTurnJobHandlerTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Runtime Turn Handler 测试实例。 */
    RuntimeTurnJobHandlerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Job 映射为 Runtime Internal Command 并返回稳定 Run 引用。 */
    @Test
    void createsTurnThroughInternalClient() {
        RuntimeInternalClient client = mock(RuntimeInternalClient.class);
        when(client.createTurn(any())).thenReturn(
            CompletableFuture.completedFuture(RunId.generate().asString()));
        JsonMapper mapper = JsonMapper.builder().build();
        SessionId sessionId = SessionId.generate();
        String input = "{\"message\":\"hello\"}";
        String payload = mapper.writeValueAsString(Map.of(
            "sessionId", sessionId.asString(), "input", input, "priority", 7));
        ClaimedJob claim = claim(payload);
        RuntimeTurnJobHandler handler = new RuntimeTurnJobHandler(client, mapper);

        HandlerResult result = handler.handle(claim).toCompletableFuture().join();

        assertThat(result.successful()).isTrue();
        assertThat(result.resultRef()).hasValueSatisfying(value ->
            assertThat(value).startsWith("run:"));
        ArgumentCaptor<RuntimeTurnCommand> command =
            ArgumentCaptor.forClass(RuntimeTurnCommand.class);
        verify(client).createTurn(command.capture());
        assertThat(command.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(command.getValue().inputHash()).isEqualTo(Checksum.sha256(input));
        assertThat(command.getValue().idempotencyKey())
            .isEqualTo("scheduler-turn:" + claim.job().id().asString());
    }

    /** 证明 Cron Trigger 的字符串配置可以形成合法 Runtime 优先级。 */
    @Test
    void acceptsCronStringPriority() {
        RuntimeInternalClient client = mock(RuntimeInternalClient.class);
        when(client.createTurn(any())).thenReturn(
            CompletableFuture.completedFuture(RunId.generate().asString()));
        JsonMapper mapper = JsonMapper.builder().build();
        SessionId sessionId = SessionId.generate();
        String payload = mapper.writeValueAsString(Map.of(
            "sessionId", sessionId.asString(), "input", "{\"source\":\"cron\"}",
            "priority", "7"));
        RuntimeTurnJobHandler handler = new RuntimeTurnJobHandler(client, mapper);

        HandlerResult result = handler.handle(claim(payload)).toCompletableFuture().join();

        assertThat(result.successful()).isTrue();
        ArgumentCaptor<RuntimeTurnCommand> command =
            ArgumentCaptor.forClass(RuntimeTurnCommand.class);
        verify(client).createTurn(command.capture());
        assertThat(command.getValue().priority()).isEqualTo(7);
    }

    /**
     * 创建 Runtime Turn Claim。
     *
     * @param payload Job Payload
     * @return Claim
     */
    private ClaimedJob claim(String payload) {
        Job job = new Job(
            JobId.generate(), OrganizationId.generate(), ProjectId.generate(),
            JobType.RUNTIME_TURN, "runtime-turn-fixture", payload, Checksum.sha256(payload),
            JobStatus.CLAIMED, 0, NOW,
            new RetryPolicy(
                3, Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.0,
                Duration.ofSeconds(30)),
            IdempotencyCapability.PROVIDER_KEY, 1, 1, NOW, NOW);
        return new ClaimedJob(
            job, SchedulerUuidV7.generate(NOW), 1, "scheduler-test", 1,
            NOW.plusSeconds(30));
    }
}
