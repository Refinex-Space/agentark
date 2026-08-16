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

package space.refinex.agentark.runtime.adapter.out.engine;

import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.AgentExecutionEngine;

import java.util.Optional;

/**
 * 在 Provider 生产 SPI 尚未装配时返回明确失败，保证已接单 Run 不会静默丢失。
 *
 * @author refinex
 */
public final class UnavailableAgentExecutionEngine implements AgentExecutionEngine {

    /**
     * 首次执行返回 Provider 未装配错误。
     *
     * @param session  Session
     * @param run      Run
     * @param snapshot Snapshot
     * @param input    Turn 输入
     * @return 明确失败
     */
    @Override
    public ExecutionResult execute(
        Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input) {
        return unavailable();
    }

    /**
     * Approval 恢复返回 Provider 未装配错误。
     *
     * @param session  Session
     * @param run      Run
     * @param snapshot Snapshot
     * @param command  恢复命令
     * @return 明确失败
     */
    @Override
    public ExecutionResult resume(
        Session session, Run run, SnapshotDescriptor snapshot, ResumeCommand command) {
        return unavailable();
    }

    /**
     * Checkpoint 恢复返回 Provider 未装配错误。
     *
     * @param session    Session
     * @param run        Run
     * @param snapshot   Snapshot
     * @param checkpoint Checkpoint
     * @return 明确失败
     */
    @Override
    public ExecutionResult recover(
        Session session, Run run, SnapshotDescriptor snapshot, Checkpoint checkpoint) {
        return unavailable();
    }

    /**
     * 未创建 Provider 外部资源时取消无需执行额外动作。
     *
     * @param command 已持久取消命令
     */
    @Override
    public void cancel(CancellationCommand command) {
        // Provider 未装配，因此不存在可释放的外部资源。
    }

    /**
     * 创建稳定 Provider 未装配失败。
     *
     * @return Provider 未装配结果
     */
    private ExecutionResult unavailable() {
        return new ExecutionResult(
            ExecutionOutcome.FAILED, Optional.of("RUNTIME_PROVIDER_UNAVAILABLE"),
            Optional.of("Runtime provider production SPI is not configured"));
    }
}
