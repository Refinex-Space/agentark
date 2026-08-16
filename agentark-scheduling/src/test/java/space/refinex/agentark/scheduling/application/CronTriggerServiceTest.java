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
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.TriggerRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Cron 只推进 Cursor，并生成可由目标 Handler 解析的幂等 Job Payload。
 *
 * @author refinex
 */
class CronTriggerServiceTest {

    /** 固定计划点火时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Cron 点火测试实例。 */
    CronTriggerServiceTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明配置字段和受控 Trigger 元数据形成规范 Payload，且只调用原子 fire。 */
    @Test
    void createsDurableJobWithoutExecutingHandler() {
        TriggerRepository repository = mock(TriggerRepository.class);
        TriggerDefinition trigger = new TriggerDefinition(
            SchedulerUuidV7.generate(NOW), OrganizationId.generate(), ProjectId.generate(),
            "runtime-minute", TriggerType.CRON, Optional.of("0 * * * * *"),
            Optional.of(ZoneId.of("UTC")), Map.of("sessionId", "session-1"),
            Optional.empty(), "runtime-turn/v1", JobType.RUNTIME_TURN,
            TriggerStatus.ENABLED, 0, NOW.minusSeconds(60), NOW.minusSeconds(60));
        TriggerCursor cursor = new TriggerCursor(
            trigger.id(), NOW, Optional.empty(), Optional.empty(), 0);
        when(repository.findDue(NOW, 10)).thenReturn(List.of(
            new TriggerRepository.DueTrigger(trigger, cursor)));
        when(repository.fire(any(), any(), any(), any(), any(), anyString(), any()))
            .thenReturn(true);
        JsonMapper mapper = JsonMapper.builder().build();
        CronTriggerService service = new CronTriggerService(
            repository, new CronCalculator(), Clock.fixed(NOW, ZoneOffset.UTC), mapper);

        assertThat(service.fireDue(10)).isEqualTo(1);

        ArgumentCaptor<Job> job = ArgumentCaptor.forClass(Job.class);
        verify(repository).fire(
            eq(trigger), eq(cursor), job.capture(), eq(NOW), any(), anyString(), any());
        assertThat(mapper.readTree(job.getValue().payload()).get("sessionId").asText())
            .isEqualTo("session-1");
        assertThat(mapper.readTree(job.getValue().payload()).get("_triggerScheduledAt").asText())
            .isEqualTo(NOW.toString());
        assertThat(job.getValue().businessKey()).startsWith("cron:");
    }
}
