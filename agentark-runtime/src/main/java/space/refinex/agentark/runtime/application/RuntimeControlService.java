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

import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.port.AgentExecutionEngine;

import java.util.Objects;
import java.util.Optional;

/**
 * 确保取消权威事务先提交，再向 Provider 发送可重试的资源释放通知。
 *
 * @author refinex
 */
public final class RuntimeControlService {

    /**
     * 短事务协调器。
     */
    private final RuntimeExecutionCoordinator coordinator;

    /**
     * Provider 中立执行引擎。
     */
    private final AgentExecutionEngine executionEngine;

    /**
     * 创建运行控制服务。
     *
     * @param coordinator     短事务协调器
     * @param executionEngine Provider 执行引擎
     */
    public RuntimeControlService(
        RuntimeExecutionCoordinator coordinator, AgentExecutionEngine executionEngine) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.executionEngine = Objects.requireNonNull(
            executionEngine, "executionEngine must not be null");
    }

    /**
     * 幂等取消 Run；只有首次写入取消事实后才通知 Provider。
     *
     * @param runId      Run 标识
     * @param reasonCode 稳定原因
     * @return 首次取消为 true，终态重放为 false
     */
    public boolean cancel(RunId runId, String reasonCode) {
        Optional<CancellationCommand> command = coordinator.cancel(runId, reasonCode);
        command.ifPresent(executionEngine::cancel);
        return command.isPresent();
    }
}
