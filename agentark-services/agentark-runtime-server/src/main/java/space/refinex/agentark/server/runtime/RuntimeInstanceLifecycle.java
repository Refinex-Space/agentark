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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.runtime.domain.RuntimeModels.DrainStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeInstance;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;
import space.refinex.agentark.runtime.port.RuntimeInstanceRepository;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 注册 Runtime Instance、刷新心跳，并在进程关闭前进入排空状态。
 *
 * @author refinex
 */
public final class RuntimeInstanceLifecycle {

    /**
     * Runtime Instance 仓储。
     */
    private final RuntimeInstanceRepository repository;

    /**
     * Provider 能力目录。
     */
    private final RuntimeProviderCatalog providerCatalog;

    /**
     * Runtime 实例 Key。
     */
    private final String instanceKey;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 本进程启动时刻。
     */
    private final Instant startedAt;

    /**
     * 当前进程内排空状态，用于在数据库状态提交前阻止领取新工作。
     */
    private final AtomicReference<DrainStatus> drainStatus =
        new AtomicReference<>(DrainStatus.ACTIVE);

    /**
     * 创建 Runtime Instance 生命周期管理器。
     *
     * @param repository      Instance 仓储
     * @param providerCatalog Provider 能力目录
     * @param instanceKey     Instance Key
     * @param clock           UTC 时钟
     */
    public RuntimeInstanceLifecycle(
        RuntimeInstanceRepository repository,
        RuntimeProviderCatalog providerCatalog,
        String instanceKey,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.providerCatalog = Objects.requireNonNull(
            providerCatalog, "providerCatalog must not be null");
        if (instanceKey == null || instanceKey.isBlank()) {
            throw new IllegalArgumentException("instanceKey must not be blank");
        }
        this.instanceKey = instanceKey;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.startedAt = Instant.now(clock);
    }

    /**
     * 进程启动后注册 ACTIVE Runtime Instance。
     */
    @PostConstruct
    public void register() {
        drainStatus.set(DrainStatus.ACTIVE);
        RuntimeProviderMetadata provider = providerCatalog.current();
        repository.register(new RuntimeInstance(
            JobId.generate(), instanceKey, startedAt, Instant.now(clock),
            Map.of(
                "runtimeProvider", provider.providerId(),
                "compilerVersion", provider.compilerVersion()),
            DrainStatus.ACTIVE));
    }

    /**
     * 每十秒刷新 Runtime Instance 心跳。
     */
    @Scheduled(fixedDelayString = "${agentark.runtime.instance-heartbeat-delay:10s}")
    public void heartbeat() {
        if (!acceptingWork()) {
            return;
        }
        if (!repository.heartbeat(instanceKey, Instant.now(clock))) {
            register();
        }
    }

    /**
     * 判断本实例是否仍允许领取新的 Runtime Work Item。
     *
     * @return ACTIVE 状态为 true；DRAINING 或 DRAINED 为 false
     */
    public boolean acceptingWork() {
        return drainStatus.get() == DrainStatus.ACTIVE;
    }

    /**
     * 关闭前先标记 DRAINING，再标记 DRAINED，阻止编排器分配新工作。
     */
    @PreDestroy
    public void drain() {
        drainStatus.set(DrainStatus.DRAINING);
        Instant now = Instant.now(clock);
        repository.updateDrainStatus(instanceKey, DrainStatus.DRAINING, now);
        repository.updateDrainStatus(instanceKey, DrainStatus.DRAINED, Instant.now(clock));
        drainStatus.set(DrainStatus.DRAINED);
    }
}
