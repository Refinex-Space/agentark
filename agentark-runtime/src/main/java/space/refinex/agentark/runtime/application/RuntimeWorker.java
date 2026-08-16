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

import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.AgentExecutionEngine;
import space.refinex.agentark.runtime.port.ExecutionLeaseCoordinator;
import space.refinex.agentark.runtime.port.SnapshotLoader;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 在事务外加载 Snapshot、编译并调用 Provider，把结果交回短事务协调器持久化。
 *
 * @author refinex
 */
public final class RuntimeWorker {

    /**
     * Runtime Instance 稳定 Key。
     */
    private final String instanceKey;

    /**
     * 单次 Claim Lease TTL。
     */
    private final Duration leaseTtl;

    /**
     * 短事务执行协调器。
     */
    private final RuntimeExecutionCoordinator coordinator;

    /**
     * Redis/MySQL 双层 Lease 协调器。
     */
    private final ExecutionLeaseCoordinator leaseCoordinator;

    /**
     * 固定 Snapshot Loader。
     */
    private final SnapshotLoader snapshotLoader;

    /**
     * Provider 中立执行引擎。
     */
    private final AgentExecutionEngine executionEngine;

    /**
     * 创建 Runtime Worker。
     *
     * @param instanceKey      Runtime Instance Key
     * @param leaseTtl         Claim Lease TTL
     * @param coordinator      短事务协调器
     * @param leaseCoordinator 双层 Lease 协调器
     * @param snapshotLoader   Snapshot Loader
     * @param executionEngine  Provider 中立执行引擎
     */
    public RuntimeWorker(
        String instanceKey,
        Duration leaseTtl,
        RuntimeExecutionCoordinator coordinator,
        ExecutionLeaseCoordinator leaseCoordinator,
        SnapshotLoader snapshotLoader,
        AgentExecutionEngine executionEngine) {
        if (instanceKey == null || instanceKey.isBlank()) {
            throw new IllegalArgumentException("instanceKey must not be blank");
        }
        this.instanceKey = instanceKey;
        this.leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        if (leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.leaseCoordinator = Objects.requireNonNull(
            leaseCoordinator, "leaseCoordinator must not be null");
        this.snapshotLoader = Objects.requireNonNull(
            snapshotLoader, "snapshotLoader must not be null");
        this.executionEngine = Objects.requireNonNull(
            executionEngine, "executionEngine must not be null");
    }

    /**
     * 尝试执行一个持久 Work Item；无任务、Redis 竞争失败或孤儿替换时返回空。
     *
     * @return 已持久化的执行结果
     */
    public Optional<ExecutionResult> runOnce() {
        Optional<ClaimedExecution> claimed = coordinator.claim(instanceKey, leaseTtl);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        ClaimedExecution execution = claimed.orElseThrow();
        Optional<ExecutionLeaseCoordinator.ExecutionLease> activated =
            leaseCoordinator.activate(execution.workItem(), leaseTtl);
        if (activated.isEmpty()) {
            return Optional.empty();
        }
        try (ExecutionLeaseCoordinator.ExecutionLease lease = activated.orElseThrow()) {
            lease.onLost(() -> executionEngine.cancel(new CancellationCommand(
                execution.turn().id(), execution.run().id(),
                execution.run().fencingToken(), "LEASE_LOST")));
            lease.requireCurrent();
            ExecutionResult result = execute(execution);
            lease.requireCurrent();
            coordinator.complete(execution, result);
            return Optional.of(result);
        }
    }

    /**
     * 在事务外加载并核对固定 Snapshot，然后按模式调用 Provider。
     *
     * @param claimed 已 Claim 执行上下文
     * @return Provider 中立结果；加载或编译失败转换为稳定失败
     */
    private ExecutionResult execute(ClaimedExecution claimed) {
        try {
            SnapshotDescriptor snapshot = snapshotLoader.load(claimed.session().revisionId());
            requireFixedSnapshot(claimed.session(), snapshot);
            return switch (claimed.mode()) {
                case INITIAL -> executionEngine.execute(
                    claimed.session(), claimed.run(), snapshot, claimed.turn().input());
                case RECOVER -> executionEngine.recover(
                    claimed.session(), claimed.run(), snapshot,
                    claimed.checkpoint().orElseThrow());
                case RESUME -> executionEngine.resume(
                    claimed.session(), claimed.run(), snapshot,
                    new ResumeCommand(
                        claimed.run().id(), claimed.decisions(), claimed.run().fencingToken()));
            };
        } catch (RuntimeException exception) {
            return new ExecutionResult(
                ExecutionOutcome.FAILED, Optional.of("RUNTIME_PREPARATION_FAILED"),
                Optional.of("Snapshot loading or provider preparation failed: "
                    + exception.getClass().getSimpleName()));
        }
    }

    /**
     * 校验加载结果仍与 Session 创建时固定的 Snapshot 完全一致。
     *
     * @param session  固定 Session
     * @param snapshot 加载结果
     */
    private void requireFixedSnapshot(Session session, SnapshotDescriptor snapshot) {
        if (!snapshot.revisionId().equals(session.revisionId())
            || !snapshot.snapshotId().equals(session.snapshotId())
            || !snapshot.contentHash().equals(session.snapshotHash())) {
            throw new IllegalStateException(
                "loaded snapshot differs from the session fixed snapshot");
        }
    }
}
