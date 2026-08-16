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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;
import space.refinex.agentark.scheduling.port.SchedulerRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * 注册只使用 Job Type 白名单标签的队列深度与最老等待年龄指标。
 *
 * @author refinex
 */
public final class SchedulerMetrics {

    /**
     * Scheduler 仓储。
     */
    private final SchedulerRepository repository;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * 创建并注册 Scheduler 指标。
     *
     * @param repository Scheduler 仓储
     * @param registry   Micrometer Registry
     * @param clock      UTC 时钟
     */
    public SchedulerMetrics(
        SchedulerRepository repository, MeterRegistry registry, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        for (JobType type : JobType.values()) {
            Gauge.builder("agentark.scheduler.queue.depth", this,
                    metrics -> metrics.depth(type))
                .description("Scheduler 当前到期队列深度")
                .tag("job.type", type.name())
                .register(registry);
            Gauge.builder("agentark.scheduler.queue.oldest.age.seconds", this,
                    metrics -> metrics.oldestSeconds(type))
                .description("Scheduler 最老到期 Job 等待秒数")
                .tag("job.type", type.name())
                .register(registry);
        }
    }

    /**
     * 查询指定类型的到期队列深度。
     *
     * @param type Job 类型
     * @return 队列深度
     */
    private double depth(JobType type) {
        return repository.dueDepth(type, clock.instant());
    }

    /**
     * 查询指定类型最老到期 Job 的等待秒数。
     *
     * @param type Job 类型
     * @return 无 Job 时为 0
     */
    private double oldestSeconds(JobType type) {
        return repository.oldestAge(type, clock.instant())
            .map(java.time.Duration::toSeconds).orElse(0L);
    }
}
