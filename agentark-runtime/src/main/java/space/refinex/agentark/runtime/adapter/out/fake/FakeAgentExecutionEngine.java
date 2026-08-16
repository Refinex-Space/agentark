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

package space.refinex.agentark.runtime.adapter.out.fake;

import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.AgentExecutionEngine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 按测试预设顺序返回执行结果的 Fake Engine，不连接模型、Tool、网络或 AgentScope。
 *
 * @author refinex
 */
public final class FakeAgentExecutionEngine implements AgentExecutionEngine {

    /**
     * 待返回的执行结果队列。
     */
    private final Deque<ExecutionResult> results = new ArrayDeque<>();

    /**
     * 最近一次取消命令。
     */
    private CancellationCommand lastCancellation;

    /**
     * 创建默认返回成功的 Fake Engine。
     */
    public FakeAgentExecutionEngine() {
    }

    /**
     * 追加下一次 execute 或 resume 应返回的结果。
     *
     * @param result 供应商中立结果
     * @return 当前 Fake，便于测试链式配置
     */
    public synchronized FakeAgentExecutionEngine enqueue(ExecutionResult result) {
        results.addLast(Objects.requireNonNull(result, "result must not be null"));
        return this;
    }

    /**
     * 返回预设结果；队列为空时返回成功。
     *
     * @param session  固定 Session
     * @param run      当前 Run
     * @param snapshot 固定 Snapshot
     * @param input    Turn 输入
     * @return 预设或默认成功结果
     */
    @Override
    public synchronized ExecutionResult execute(
        Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input) {
        validate(session, run, snapshot);
        Objects.requireNonNull(input, "input must not be null");
        return next();
    }

    /**
     * 返回预设恢复结果；调用方负责先证明 Approval 已批准。
     *
     * @param session  固定 Session
     * @param run      PAUSED Run
     * @param snapshot 固定 Snapshot
     * @param command  恢复命令
     * @return 预设或默认成功结果
     */
    @Override
    public synchronized ExecutionResult resume(
        Session session, Run run, SnapshotDescriptor snapshot, ResumeCommand command) {
        validate(session, run, snapshot);
        Objects.requireNonNull(command, "command must not be null");
        return next();
    }

    /**
     * 记录取消命令，不产生外部副作用。
     *
     * @param command 取消命令
     */
    @Override
    public synchronized void cancel(CancellationCommand command) {
        lastCancellation = Objects.requireNonNull(command, "command must not be null");
    }

    /**
     * 返回最近记录的取消命令。
     *
     * @return 最近取消命令；尚未取消时为 null
     */
    public synchronized CancellationCommand lastCancellation() {
        return lastCancellation;
    }

    /**
     * 返回队首结果或默认成功结果。
     *
     * @return 执行结果
     */
    private ExecutionResult next() {
        return results.isEmpty()
            ? new ExecutionResult(ExecutionOutcome.SUCCEEDED, java.util.Optional.empty(),
            java.util.Optional.empty())
            : results.removeFirst();
    }

    /**
     * 校验 Fake 调用仍遵循固定 Revision/Snapshot 关系。
     *
     * @param session  Session
     * @param run      Run
     * @param snapshot Snapshot
     */
    private void validate(Session session, Run run, SnapshotDescriptor snapshot) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (!session.id().equals(run.sessionId())
            || !session.revisionId().equals(snapshot.revisionId())
            || !session.snapshotId().equals(snapshot.snapshotId())
            || !session.snapshotHash().equals(snapshot.contentHash())) {
            throw new IllegalArgumentException("session, run and snapshot are inconsistent");
        }
    }
}
