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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.ApprovalRepository;
import space.refinex.agentark.runtime.port.RuntimeEventStore;
import space.refinex.agentark.runtime.port.RuntimeRepository;
import space.refinex.agentark.runtime.port.RuntimeInstanceRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 提供 Runtime API 所需的只读聚合查询，Controller 不直接依赖数据库 Mapper。
 *
 * @author refinex
 */
public final class RuntimeQueryService {

    /**
     * Runtime 聚合仓储。
     */
    private final RuntimeRepository repository;

    /**
     * Runtime Event 事实仓储。
     */
    private final RuntimeEventStore eventStore;

    /**
     * Approval 仓储。
     */
    private final ApprovalRepository approvalRepository;

    /**
     * Runtime Instance 仓储。
     */
    private final RuntimeInstanceRepository instanceRepository;

    /**
     * 创建 Runtime 查询服务。
     *
     * @param repository         Runtime 聚合仓储
     * @param eventStore         Event Store
     * @param approvalRepository Approval 仓储
     * @param instanceRepository Runtime Instance 仓储
     */
    public RuntimeQueryService(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        ApprovalRepository approvalRepository,
        RuntimeInstanceRepository instanceRepository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.approvalRepository = Objects.requireNonNull(
            approvalRepository, "approvalRepository must not be null");
        this.instanceRepository = Objects.requireNonNull(
            instanceRepository, "instanceRepository must not be null");
    }

    /**
     * 读取 Session。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    public Session session(SessionId sessionId) {
        return repository.findSession(sessionId)
            .orElseThrow(() -> new RuntimeNotFoundException("session is not available"));
    }

    /**
     * 读取 Run。
     *
     * @param runId Run 标识
     * @return Run
     */
    public Run run(RunId runId) {
        return repository.findRun(runId)
            .orElseThrow(() -> new RuntimeNotFoundException("run is not available"));
    }

    /**
     * 读取 Run 所属 Turn。
     *
     * @param run Run
     * @return Turn
     */
    public Turn turn(Run run) {
        return repository.findTurn(run.turnId())
            .orElseThrow(() -> new RuntimeNotFoundException("turn is not available"));
    }

    /**
     * 按游标读取 Run Event。
     *
     * @param runId        Run 标识
     * @param afterSequence Session Sequence 游标
     * @param limit        最大数量
     * @return 有序 Event
     */
    public List<RuntimeEvent> events(RunId runId, long afterSequence, int limit) {
        run(runId);
        return eventStore.listRunAfter(runId, afterSequence, limit);
    }

    /**
     * 读取属于指定 Run 的单条 Event，防止通过 Event ID 猜测跨 Run 下载。
     *
     * @param runId   Run 标识
     * @param eventId Event 标识
     * @return 同 Run Event
     */
    public RuntimeEvent event(RunId runId, EventId eventId) {
        run(runId);
        RuntimeEvent event = eventStore.find(eventId)
            .orElseThrow(() -> new RuntimeNotFoundException("event is not available"));
        if (!event.runId().equals(runId)) {
            throw new RuntimeNotFoundException("event is not available");
        }
        return event;
    }

    /**
     * 读取有限 Runtime Instance 集合，Controller 只可输出聚合摘要。
     *
     * @param limit 最大数量
     * @return Runtime Instance 列表
     */
    public List<RuntimeInstance> instances(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("instance limit must be between 1 and 1000");
        }
        return instanceRepository.list(limit);
    }

    /**
     * 读取 Approval。
     *
     * @param approvalId Approval 标识
     * @return Approval
     */
    public Approval approval(ApprovalId approvalId) {
        return approvalRepository.find(approvalId)
            .orElseThrow(() -> new RuntimeNotFoundException("approval is not available"));
    }

    /**
     * 按项目、状态与游标读取 Approval。
     *
     * @param projectId 项目标识
     * @param status    可选状态
     * @param afterId   可选 UUIDv7 游标
     * @param limit     最大数量
     * @return Approval 列表
     */
    public List<Approval> approvals(
        ProjectId projectId,
        Optional<ApprovalStatus> status,
        Optional<ApprovalId> afterId,
        int limit) {
        return approvalRepository.list(projectId, status, afterId, limit);
    }
}
