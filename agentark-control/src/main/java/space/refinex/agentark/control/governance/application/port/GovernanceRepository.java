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

package space.refinex.agentark.control.governance.application.port;

import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.kernel.id.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Control/Governance 对审计、计量、配额和 Evaluation 的唯一持久化边界。
 *
 * @author refinex
 */
public interface GovernanceRepository {

    /**
     * 幂等追加不可变审计事件。
     *
     * @param event 审计事件
     * @return 首次插入为 {@code true}，幂等重放为 {@code false}
     */
    boolean appendAudit(AuditEvent event);

    /**
     * 按时间与 UUID 倒序读取严格租户范围内审计事件。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param before         可选发生时间上界
     * @param beforeId       可选同一时刻 UUID 上界
     * @param limit          最大数量
     * @return 审计事件列表
     */
    List<AuditEvent> listAudit(OrganizationId organizationId, ProjectId projectId, Optional<Instant> before, Optional<EventId> beforeId, int limit);

    /**
     * 插入稳定价格表身份。
     *
     * @param table 价格表
     * @param actor 操作主体
     */
    void insertPriceTable(PriceTable table, String actor);

    /**
     * 插入不可变价格表版本。
     *
     * @param version 价格表版本
     * @param actor   操作主体
     */
    void insertPriceTableVersion(PriceTableVersion version, String actor);

    /**
     * 列出项目内价格表。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return 稳定价格表列表
     */
    List<PriceTable> listPriceTables(ProjectId projectId, int limit);

    /**
     * 列出指定价格表不可变版本。
     *
     * @param projectId    项目
     * @param priceTableId 价格表 UUIDv7
     * @param limit        最大数量
     * @return 版本列表
     */
    List<PriceTableVersion> listPriceTableVersions(ProjectId projectId, UUID priceTableId, int limit);

    /**
     * 幂等写入用量明细并更新日聚合。
     *
     * @param entry 用量明细
     * @return 首次写入为 {@code true}，幂等重放为 {@code false}
     */
    boolean ingestUsage(UsageLedgerEntry entry);

    /**
     * 读取项目治理用量明细。
     *
     * @param projectId 项目
     * @param before    可选时间上界
     * @param limit     最大数量
     * @return 用量明细
     */
    List<UsageLedgerEntry> listUsageLedger(ProjectId projectId, Optional<Instant> before, int limit);

    /**
     * 读取固定时间范围内的日级用量聚合。
     *
     * @param projectId 项目
     * @param from      开始时间，含
     * @param to        结束时间，不含
     * @param limit     最大数量
     * @return 用量聚合
     */
    List<UsageAggregate> listUsageAggregates(ProjectId projectId, Instant from, Instant to, int limit);

    /**
     * 插入 Quota Policy。
     *
     * @param policy Policy
     * @param actor  操作主体
     */
    void insertQuotaPolicy(QuotaPolicy policy, String actor);

    /**
     * 列出项目 Quota Policy。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return Policy 列表
     */
    List<QuotaPolicy> listQuotaPolicies(ProjectId projectId, int limit);

    /**
     * 在 Policy 行锁内执行并发安全预留；无匹配 Policy 时允许且不创建 Reservation。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param scopeType      作用域类型
     * @param scopeRef       作用域引用
     * @param metric         指标
     * @param idempotencyKey 幂等键
     * @param subjectRef     工作引用
     * @param amount         预留量
     * @param ttl            HELD 生存期
     * @param now            当前时间
     * @return Quota 决策
     */
    QuotaDecision reserveQuota(OrganizationId organizationId, ProjectId projectId, QuotaScopeType scopeType,
                               String scopeRef, QuotaMetric metric, String idempotencyKey, String subjectRef,
                               BigDecimal amount, Duration ttl, Instant now);

    /**
     * 幂等转换 Quota Reservation 终态。
     *
     * @param reservationId Reservation UUIDv7
     * @param target        COMMITTED 或 RELEASED
     * @param now           当前时间
     * @return 已转换或已经处于目标状态时为 {@code true}
     */
    boolean transitionReservation(UUID reservationId, ReservationStatus target, Instant now);

    /**
     * 插入 Dataset、不可变版本和 Test Case。
     *
     * @param dataset Dataset
     * @param version Version
     * @param cases   Test Case
     * @param actor   操作主体
     */
    void insertDataset(EvaluationDataset dataset, DatasetVersion version, List<EvaluationTestCase> cases,
                       OrganizationId organizationId, ProjectId projectId, String actor);

    /**
     * 列出项目 Dataset。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return Dataset 列表
     */
    List<EvaluationDataset> listDatasets(ProjectId projectId, int limit);

    /**
     * 读取固定 Dataset Version。
     *
     * @param projectId 项目
     * @param versionId Version UUIDv7
     * @return Dataset Version
     */
    Optional<DatasetVersion> findDatasetVersion(ProjectId projectId, UUID versionId);

    /**
     * 读取固定 Dataset Version 的全部 Test Case。
     *
     * @param projectId 项目
     * @param versionId Version UUIDv7
     * @return Test Case 列表
     */
    List<EvaluationTestCase> listTestCases(ProjectId projectId, UUID versionId);

    /**
     * 插入 Evaluator 和不可变版本。
     *
     * @param evaluator      Evaluator
     * @param version        Version
     * @param organizationId 组织
     * @param projectId      项目
     * @param actor          操作主体
     */
    void insertEvaluator(Evaluator evaluator, EvaluatorVersion version, OrganizationId organizationId, ProjectId projectId, String actor);

    /**
     * 列出项目 Evaluator。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return Evaluator 列表
     */
    List<Evaluator> listEvaluators(ProjectId projectId, int limit);

    /**
     * 读取固定 Evaluator Version。
     *
     * @param projectId 项目
     * @param versionId Version UUIDv7
     * @return Evaluator Version
     */
    Optional<EvaluatorVersion> findEvaluatorVersion(ProjectId projectId, UUID versionId);

    /**
     * 原子插入 Evaluation Run 和只追加 Score。
     *
     * @param run            Evaluation Run
     * @param scores         Test Case Scores
     * @param organizationId 组织
     * @param projectId      项目
     * @param actor          操作主体
     */
    void insertEvaluationRun(EvaluationRun run, List<EvaluationScore> scores, OrganizationId organizationId, ProjectId projectId, String actor);

    /**
     * 按创建时间倒序列出 Evaluation Run。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return Evaluation Run 列表
     */
    List<EvaluationRun> listEvaluationRuns(ProjectId projectId, int limit);

    /**
     * 读取单个 Evaluation Run。
     *
     * @param projectId 项目
     * @param runId     Run UUIDv7
     * @return Evaluation Run
     */
    Optional<EvaluationRun> findEvaluationRun(ProjectId projectId, UUID runId);

    /**
     * 插入或使用乐观锁更新 Release Gate。
     *
     * @param gate            Gate
     * @param expectedVersion 新建时为空，更新时为预期版本
     * @param actor           操作主体
     * @param now             当前时间
     * @return 持久化 Gate
     */
    ReleaseGate saveReleaseGate(ReleaseGate gate, Optional<Long> expectedVersion, String actor, Instant now);

    /**
     * 列出项目 Release Gate。
     *
     * @param projectId 项目
     * @param limit     最大数量
     * @return Gate 列表
     */
    List<ReleaseGate> listReleaseGates(ProjectId projectId, int limit);

    /**
     * 判断目标 Revision 是否满足 Agent/Environment 的活动 Release Gate。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param agentId        Agent
     * @param environmentId  环境
     * @param revisionId     目标 Revision
     * @return Gate 决策
     */
    ReleaseGateDecision evaluateReleaseGate(OrganizationId organizationId, ProjectId projectId, AgentId agentId, EnvironmentId environmentId, RevisionId revisionId);

    /**
     * 返回平台治理概览计数和当前成本。
     *
     * @param projectId 项目
     * @param from      统计开始时间
     * @return 稳定键值概览
     */
    Map<String, Object> overview(ProjectId projectId, Instant from);
}
