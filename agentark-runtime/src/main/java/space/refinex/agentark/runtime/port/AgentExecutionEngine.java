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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

/**
 * 定义 Provider Adapter 必须实现的单次 Run 执行、恢复和取消边界。
 *
 * @author refinex
 */
public interface AgentExecutionEngine {

    /**
     * 执行已持久 Claim 且 Snapshot 固定的 Run；实现不得自行写 Runtime 权威表。
     *
     * @param session  固定 Revision/Snapshot 的 Session
     * @param run      当前 Run Attempt
     * @param snapshot 通过 Internal Contract 校验后的 Snapshot
     * @param input    Turn 输入
     * @return 供应商中立执行结果
     */
    ExecutionResult execute(
        Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input);

    /**
     * 恢复已持久暂停且审批通过的同一 Run。
     *
     * @param session  固定 Revision/Snapshot 的 Session
     * @param run      PAUSED Run
     * @param snapshot 已校验 Snapshot
     * @param command  绑定 Approval 与参数 Hash 的恢复命令
     * @return 供应商中立执行结果
     */
    ExecutionResult resume(
        Session session, Run run, SnapshotDescriptor snapshot, ResumeCommand command);

    /**
     * 通知 Provider 取消外部执行；权威取消事实必须已由 Runtime 持久化。
     *
     * @param command 绑定当前 Run 与 Fencing Token 的取消命令
     */
    void cancel(CancellationCommand command);
}
