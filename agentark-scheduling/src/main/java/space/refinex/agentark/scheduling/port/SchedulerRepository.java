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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Scheduler MySQL 权威 Job、Attempt、Lease、Dead Letter 与 Outbox 事务端口。
 *
 * @author refinex
 */
public interface SchedulerRepository {

    /**
     * 持久化新 Job；相同类型和业务键由数据库唯一约束保证只存在一个 Job。
     *
     * @param job    待持久化 Job
     * @param outbox 同事务接单事件
     */
    void insert(Job job, SchedulerOutbox outbox);

    /**
     * 按类型和业务键读取既有幂等结果。
     *
     * @param type        Job 类型
     * @param businessKey 业务键
     * @return 既有 Job
     */
    Optional<Job> findByBusinessKey(JobType type, String businessKey);

    /**
     * 按标识读取 Job。
     *
     * @param jobId Job 标识
     * @return Job
     */
    Optional<Job> find(JobId jobId);

    /**
     * 按 UUIDv7 顺序列出租户 Job，不返回未授权租户数据。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param afterId        不包含当前值的 Job 游标
     * @param limit          读取上限
     * @return Job 列表
     */
    List<Job> list(
        OrganizationId organizationId, ProjectId projectId, JobId afterId, int limit);

    /**
     * 原子领取一个到期 Job，递增 Fencing Token 并追加 Attempt。
     *
     * @param type      Worker Pool 可处理的 Job 类型
     * @param owner     Worker 实例 Key
     * @param now       当前时间
     * @param leaseTtl  Lease 有效期
     * @param attemptId 新 Attempt UUIDv7
     * @return 领取结果；无到期任务时为空
     */
    Optional<ClaimedJob> claim(
        JobType type, String owner, Instant now, Duration leaseTtl, UUID attemptId);

    /**
     * 仅由当前 Owner 和 Fencing Token 续租。
     *
     * @param jobId        Job 标识
     * @param owner        当前 Owner
     * @param fencingToken 当前 Fencing Token
     * @param leaseUntil   新到期时间
     * @return 是否续租成功
     */
    boolean renew(JobId jobId, String owner, long fencingToken, Instant leaseUntil);

    /**
     * 使用当前 Fencing Token 原子完成 Attempt、Job、Lease 和 Outbox。
     *
     * @param claim       当前 Claim
     * @param resultRef   可选结果引用
     * @param outbox      成功事件
     * @param completedAt 完成时间
     */
    void succeed(ClaimedJob claim, Optional<String> resultRef,
                 SchedulerOutbox outbox, Instant completedAt);

    /**
     * 使用当前 Fencing Token 记录失败并安排重试或生成 Dead Letter。
     *
     * @param claim         当前 Claim
     * @param attemptStatus Attempt 终态
     * @param errorCode     稳定错误码
     * @param nextStatus    RETRY_WAIT、TIMED_OUT 或 DEAD_LETTERED
     * @param availableAt   下次执行时间，终态时等于完成时间
     * @param deadLetter    DEAD_LETTERED 时必须提供的记录
     * @param outbox        状态事件
     * @param completedAt   完成时间
     */
    void fail(
        ClaimedJob claim,
        AttemptStatus attemptStatus,
        String errorCode,
        JobStatus nextStatus,
        Instant availableAt,
        Optional<DeadLetter> deadLetter,
        SchedulerOutbox outbox,
        Instant completedAt);

    /**
     * 取消尚未进入终态的租户 Job，并写入 Outbox。
     *
     * @param jobId          Job 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param actor          操作者
     * @param outbox         取消事件
     * @param cancelledAt    取消时间
     */
    void cancel(
        JobId jobId,
        OrganizationId organizationId,
        ProjectId projectId,
        String actor,
        SchedulerOutbox outbox,
        Instant cancelledAt);

    /**
     * 授权 Redrive OPEN Dead Letter，重新激活原 Job 并追加 Outbox。
     *
     * @param jobId          Job 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param actor          操作者
     * @param reason         审计原因
     * @param outbox         Redrive 事件
     * @param redrivenAt     Redrive 时间
     */
    void redrive(
        JobId jobId,
        OrganizationId organizationId,
        ProjectId projectId,
        String actor,
        String reason,
        SchedulerOutbox outbox,
        Instant redrivenAt);

    /**
     * 列出租户最近的 OPEN Dead Letter。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          最大返回数量
     * @return Dead Letter 列表
     */
    List<DeadLetter> listOpenDeadLetters(
        OrganizationId organizationId, ProjectId projectId, int limit);

    /**
     * 统计指定 Job 类型当前可执行或等待重试的队列深度。
     *
     * @param type Job 类型
     * @param now  当前时间
     * @return 到期队列深度
     */
    long dueDepth(JobType type, Instant now);

    /**
     * 返回指定类型最老到期 Job 的等待年龄。
     *
     * @param type Job 类型
     * @param now  当前时间
     * @return 最老年龄，无到期 Job 时为空
     */
    Optional<Duration> oldestAge(JobType type, Instant now);
}
