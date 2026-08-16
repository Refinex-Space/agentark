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
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService.CreateTriggerCommand;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.port.TriggerRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 Trigger 定义的幂等登记、Cron Cursor 初始化和敏感配置拒绝。
 *
 * @author refinex
 */
class TriggerDefinitionServiceTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Trigger 定义测试实例。 */
    TriggerDefinitionServiceTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Cron Trigger、首个 Cursor 和 Outbox 在同一仓储调用中创建。 */
    @Test
    void createsCronTriggerWithInitialCursor() {
        TriggerRepository repository = mock(TriggerRepository.class);
        when(repository.findByKey(any(), any(), any())).thenReturn(Optional.empty());
        TriggerDefinitionService service = service(repository);

        TriggerDefinition trigger = service.create(command(Map.of("sessionId", "session-1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Optional<TriggerCursor>> cursor = ArgumentCaptor.forClass(Optional.class);
        ArgumentCaptor<SchedulerOutbox> outbox = ArgumentCaptor.forClass(SchedulerOutbox.class);
        verify(repository).insert(eq(trigger), cursor.capture(), outbox.capture());
        assertThat(cursor.getValue()).isPresent();
        assertThat(cursor.getValue().orElseThrow().triggerId()).isEqualTo(trigger.id());
        assertThat(cursor.getValue().orElseThrow().nextFireAt()).isAfter(NOW);
        assertThat(outbox.getValue().type()).isEqualTo("trigger.created");
    }

    /** 证明同租户同 Key 的相同定义被幂等复用而不重复插入。 */
    @Test
    void reusesSemanticallyEqualDefinition() {
        TriggerRepository repository = mock(TriggerRepository.class);
        TriggerDefinitionService service = service(repository);
        CreateTriggerCommand command = command(Map.of("sessionId", "session-1"));
        when(repository.findByKey(any(), any(), any())).thenReturn(Optional.empty());
        TriggerDefinition first = service.create(command);
        reset(repository);
        when(repository.findByKey(
            command.organizationId(), command.projectId(), command.key()))
            .thenReturn(Optional.of(first));

        assertThat(service.create(command)).isSameAs(first);
        verify(repository, never()).insert(any(), any(), any());
    }

    /** 证明并发唯一键竞争后会复用已经提交的相同定义。 */
    @Test
    void reusesConcurrentDefinitionAfterInsertConflict() {
        TriggerRepository repository = mock(TriggerRepository.class);
        CreateTriggerCommand command = command(Map.of("sessionId", "session-1"));
        AtomicReference<TriggerDefinition> concurrent = new AtomicReference<>();
        when(repository.findByKey(
            command.organizationId(), command.projectId(), command.key()))
            .thenReturn(Optional.empty())
            .thenAnswer(invocation -> Optional.ofNullable(concurrent.get()));
        doAnswer(invocation -> {
            concurrent.set(invocation.getArgument(0));
            throw new IllegalStateException("simulated unique key race");
        }).when(repository).insert(any(), any(), any());

        TriggerDefinition result = service(repository).create(command);

        assertThat(result).isSameAs(concurrent.get());
        verify(repository, times(2)).findByKey(
            command.organizationId(), command.projectId(), command.key());
    }

    /** 证明疑似 Secret、Token 或 Credential 的配置键不能进入 Trigger JSON。 */
    @Test
    void rejectsSensitiveConfigKeys() {
        TriggerRepository repository = mock(TriggerRepository.class);

        assertThatThrownBy(() -> service(repository).create(command(Map.of(
            "apiToken", "must-not-be-stored"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensitive");
    }

    /**
     * 创建使用固定时钟的 Trigger 服务。
     *
     * @param repository Trigger 仓储
     * @return Trigger 服务
     */
    private TriggerDefinitionService service(TriggerRepository repository) {
        return new TriggerDefinitionService(
            repository, new CronCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * 创建有效 Cron Trigger 命令。
     *
     * @param config 目标 Job Payload 配置
     * @return 创建命令
     */
    private CreateTriggerCommand command(Map<String, String> config) {
        return new CreateTriggerCommand(
            OrganizationId.generate(), ProjectId.generate(), "hourly-runtime-turn",
            TriggerType.CRON, Optional.of("0 0 * * * *"), Optional.of("UTC"), config,
            Optional.empty(), "runtime-turn/v1", JobType.RUNTIME_TURN);
    }
}
