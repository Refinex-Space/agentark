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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import space.refinex.agentark.runtime.port.RuntimeMetricsRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * 注册不含 Session、Run、User 或 Project Label 的 Runtime 权威状态 Gauge。
 *
 * @author refinex
 */
public final class RuntimeMetrics {

    /** Runtime 指标查询端口。 */
    private final RuntimeMetricsRepository repository;

    /** UTC 时钟。 */
    private final Clock clock;

    /**
     * 创建并注册 Runtime Gauge。
     *
     * @param repository Runtime 指标查询端口
     * @param registry   Meter Registry
     * @param clock      UTC 时钟
     */
    public RuntimeMetrics(
        RuntimeMetricsRepository repository, MeterRegistry registry, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        Gauge.builder("agentark.runtime.active.sessions", this, metrics -> metrics.repository.activeSessions())
            .description("Runtime 当前 ACTIVE Session 数量")
            .register(registry);
        Gauge.builder("agentark.runtime.active.runs", this, metrics -> metrics.repository.activeRuns())
            .description("Runtime 当前活跃 Run 数量")
            .register(registry);
        Gauge.builder("agentark.runtime.pending.approvals", this, metrics -> metrics.repository.pendingApprovals())
            .description("Runtime 当前待审批数量")
            .register(registry);
        Gauge.builder("agentark.runtime.outbox.lag.seconds", this,
                metrics -> metrics.repository.outboxLagSeconds(metrics.clock.instant()))
            .description("Runtime 最老 PENDING Outbox 年龄秒数")
            .register(registry);
    }
}
