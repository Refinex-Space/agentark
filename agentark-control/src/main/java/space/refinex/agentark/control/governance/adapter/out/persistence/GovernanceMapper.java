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

package space.refinex.agentark.control.governance.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Governance 所属 MyBatis SQL；所有查询显式携带 Project Scope。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface GovernanceMapper {

    /**
     * 幂等插入不可变 Audit Event。
     *
     * @param row 审计数据库行
     * @return 首次插入影响 1 行，幂等重放影响 0 行
     */
    @Insert("""
        INSERT IGNORE INTO audit_event
            (id, source_event_id, source_plane, organization_id, project_id,
             principal_type, principal_ref, scope_type, scope_ref, action, result,
             resource_type, resource_ref, diff_summary_json, policy_version, role_version,
             trace_id, request_id, archive_object_uri, archive_content_hash,
             occurred_at, ingested_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{sourceEventId}, #{sourcePlane},
             #{organizationId,jdbcType=BINARY}, #{projectId,jdbcType=BINARY},
             #{principalType}, #{principalRef}, #{scopeType}, #{scopeRef}, #{action}, #{result},
             #{resourceType}, #{resourceRef}, #{diffSummaryJson}, #{policyVersion}, #{roleVersion},
             #{traceId}, #{requestId}, NULL, NULL, #{occurredAt}, #{ingestedAt})
        """)
    int insertAudit(AuditRow row);

    /**
     * 按时间与 UUID 倒序列出项目审计。
     *
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param before         可选时间上界
     * @param beforeId       可选 UUID 上界
     * @param limit          最大数量
     * @return 审计数据库行
     */
    @Select("""
        <script>
        SELECT id, source_event_id, source_plane, organization_id, project_id,
               principal_type, principal_ref, scope_type, scope_ref, action, result,
               resource_type, resource_ref, diff_summary_json, policy_version, role_version,
               trace_id, request_id, occurred_at, ingested_at
        FROM audit_event
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
        <if test="before != null">
          AND (occurred_at &lt; #{before}
               OR (occurred_at = #{before} AND id &lt; #{beforeId,jdbcType=BINARY}))
        </if>
        ORDER BY occurred_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """)
    List<AuditRow> listAudit(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("before") Instant before,
        @Param("beforeId") UUID beforeId,
        @Param("limit") int limit);

    /**
     * 插入稳定 Price Table。
     *
     * @param row   价格表行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO price_table
            (id, organization_id, project_id, price_key, name, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.priceKey}, #{row.name}, #{row.status},
             #{row.version}, #{row.createdAt}, #{actor}, #{row.updatedAt}, #{actor})
        """)
    void insertPriceTable(@Param("row") PriceTableRow row, @Param("actor") String actor);

    /**
     * 插入不可变 Price Table Version。
     *
     * @param row   价格版本行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO price_table_version
            (id, organization_id, project_id, price_table_id, version_number, currency,
             effective_from, entries_json, content_hash, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.priceTableId,jdbcType=BINARY},
             #{row.versionNumber}, #{row.currency}, #{row.effectiveFrom}, #{row.entriesJson},
             #{row.contentHash,jdbcType=BINARY}, #{row.createdAt}, #{actor})
        """)
    void insertPriceVersion(@Param("row") PriceVersionRow row, @Param("actor") String actor);

    /**
     * 列出项目价格表。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return 价格表行
     */
    @Select("""
        SELECT id, organization_id, project_id, price_key, name, status, version,
               created_at, updated_at
        FROM price_table
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<PriceTableRow> listPriceTables(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 列出不可变价格版本。
     *
     * @param projectId   项目 UUID
     * @param priceTableId 价格表 UUID
     * @param limit       最大数量
     * @return 价格版本行
     */
    @Select("""
        SELECT id, organization_id, project_id, price_table_id, version_number, currency,
               effective_from, entries_json, content_hash, created_at
        FROM price_table_version
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND price_table_id = #{priceTableId,jdbcType=BINARY}
        ORDER BY version_number DESC
        LIMIT #{limit}
        """)
    List<PriceVersionRow> listPriceVersions(
        @Param("projectId") UUID projectId,
        @Param("priceTableId") UUID priceTableId,
        @Param("limit") int limit);

    /**
     * 幂等插入 Usage Ledger。
     *
     * @param row 用量明细行
     * @return 首次插入影响 1 行，幂等重放影响 0 行
     */
    @Insert("""
        INSERT IGNORE INTO usage_ledger
            (id, source_plane, source_record_id, organization_id, project_id,
             agent_id, revision_id, deployment_id, session_id, turn_id, run_id,
             usage_type, provider, model, tool, input_tokens, output_tokens, cached_tokens,
             embedding_tokens, tool_calls, sandbox_duration_ms, estimated,
             price_table_version_id, currency, cost_amount, occurred_at, ingested_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{sourcePlane}, #{sourceRecordId},
             #{organizationId,jdbcType=BINARY}, #{projectId,jdbcType=BINARY},
             #{agentId,jdbcType=BINARY}, #{revisionId,jdbcType=BINARY},
             #{deploymentId,jdbcType=BINARY}, #{sessionId,jdbcType=BINARY},
             #{turnId,jdbcType=BINARY}, #{runId,jdbcType=BINARY}, #{usageType},
             #{provider}, #{model}, #{tool}, #{inputTokens}, #{outputTokens}, #{cachedTokens},
             #{embeddingTokens}, #{toolCalls}, #{sandboxDurationMs}, #{estimated},
             #{priceTableVersionId,jdbcType=BINARY}, #{currency}, #{costAmount},
             #{occurredAt}, #{ingestedAt})
        """)
    int insertUsage(UsageRow row);

    /**
     * 按 UTC 日窗口增量更新项目维度 Usage Aggregate。
     *
     * @param row 聚合增量行
     */
    @Insert("""
        INSERT INTO usage_aggregate
            (id, organization_id, project_id, granularity, period_start, period_end,
             dimension_type, dimension_ref, provider, model_key, input_tokens, output_tokens,
             cached_tokens, embedding_tokens, tool_calls, sandbox_duration_ms,
             estimated_records, source_records, cost_amount, currency,
             price_table_version_id, updated_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, 'DAY', #{periodStart}, #{periodEnd},
             'PROJECT', #{dimensionRef}, #{provider}, #{modelKey}, #{inputTokens},
             #{outputTokens}, #{cachedTokens}, #{embeddingTokens}, #{toolCalls},
             #{sandboxDurationMs}, #{estimatedRecords}, 1, #{costAmount}, #{currency},
             #{priceTableVersionId,jdbcType=BINARY}, #{updatedAt})
        ON DUPLICATE KEY UPDATE
             input_tokens = input_tokens + VALUES(input_tokens),
             output_tokens = output_tokens + VALUES(output_tokens),
             cached_tokens = cached_tokens + VALUES(cached_tokens),
             embedding_tokens = embedding_tokens + VALUES(embedding_tokens),
             tool_calls = tool_calls + VALUES(tool_calls),
             sandbox_duration_ms = sandbox_duration_ms + VALUES(sandbox_duration_ms),
             estimated_records = estimated_records + VALUES(estimated_records),
             source_records = source_records + 1,
             cost_amount = cost_amount + VALUES(cost_amount),
             price_table_version_id = IF(
                 price_table_version_id = VALUES(price_table_version_id),
                 price_table_version_id,
                 NULL),
             updated_at = VALUES(updated_at)
        """)
    void upsertUsageAggregate(UsageAggregateDelta row);

    /**
     * 列出项目用量明细。
     *
     * @param projectId 项目 UUID
     * @param before    可选时间上界
     * @param limit     最大数量
     * @return 用量明细行
     */
    @Select("""
        <script>
        SELECT id, source_plane, source_record_id, organization_id, project_id,
               agent_id, revision_id, deployment_id, session_id, turn_id, run_id,
               usage_type, provider, model, tool, input_tokens, output_tokens, cached_tokens,
               embedding_tokens, tool_calls, sandbox_duration_ms, estimated,
               price_table_version_id, currency, cost_amount, occurred_at, ingested_at
        FROM usage_ledger
        WHERE project_id = #{projectId,jdbcType=BINARY}
        <if test="before != null">AND occurred_at &lt; #{before}</if>
        ORDER BY occurred_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """)
    List<UsageRow> listUsage(
        @Param("projectId") UUID projectId,
        @Param("before") Instant before,
        @Param("limit") int limit);

    /**
     * 查询项目日级用量聚合。
     *
     * @param projectId 项目 UUID
     * @param from      开始时间
     * @param to        结束时间
     * @param limit     最大数量
     * @return 聚合行
     */
    @Select("""
        SELECT period_start, period_end, dimension_type, dimension_ref, provider,
               model_key, input_tokens, output_tokens, cached_tokens, embedding_tokens,
               tool_calls, sandbox_duration_ms, estimated_records, source_records,
               cost_amount, currency
        FROM usage_aggregate
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND period_start >= #{from} AND period_start < #{to}
        ORDER BY period_start DESC, id DESC
        LIMIT #{limit}
        """)
    List<UsageAggregateRow> listUsageAggregates(
        @Param("projectId") UUID projectId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("limit") int limit);

    /**
     * 插入 Quota Policy。
     *
     * @param row   Policy 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO quota_policy
            (id, organization_id, project_id, scope_type, scope_ref, metric, enforcement,
             limit_value, window_seconds, budget_action, effective_from, effective_until,
             status, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.scopeType}, #{row.scopeRef},
             #{row.metric}, #{row.enforcement}, #{row.limitValue}, #{row.windowSeconds},
             #{row.budgetAction}, #{row.effectiveFrom}, #{row.effectiveUntil}, #{row.status},
             #{row.version}, #{row.createdAt}, #{actor}, #{row.createdAt}, #{actor})
        """)
    void insertQuotaPolicy(@Param("row") QuotaPolicyRow row, @Param("actor") String actor);

    /**
     * 列出项目 Quota Policy。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return Policy 行
     */
    @Select("""
        SELECT id, organization_id, project_id, scope_type, scope_ref, metric,
               enforcement, limit_value, window_seconds, budget_action,
               effective_from, effective_until, status, version, created_at
        FROM quota_policy
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY effective_from DESC, id DESC
        LIMIT #{limit}
        """)
    List<QuotaPolicyRow> listQuotaPolicies(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 锁定当前匹配的活动 Quota Policy。
     *
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param metric         Metric
     * @param now            当前时间
     * @return Policy 行
     */
    @Select("""
        SELECT id, organization_id, project_id, scope_type, scope_ref, metric,
               enforcement, limit_value, window_seconds, budget_action,
               effective_from, effective_until, status, version, created_at
        FROM quota_policy
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND scope_type = #{scopeType} AND scope_ref = #{scopeRef}
          AND metric = #{metric} AND status = 'ACTIVE'
          AND effective_from <= #{now}
          AND (effective_until IS NULL OR effective_until > #{now})
        ORDER BY effective_from DESC, id DESC
        LIMIT 1
        FOR UPDATE
        """)
    Optional<QuotaPolicyRow> lockQuotaPolicy(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("scopeType") String scopeType,
        @Param("scopeRef") String scopeRef,
        @Param("metric") String metric,
        @Param("now") Instant now);

    /**
     * 回收指定 Policy 已到期的 HELD Reservation。
     *
     * @param policyId Policy UUID
     * @param now      当前时间
     * @return 回收数量
     */
    @Update("""
        UPDATE quota_reservation
        SET status = 'EXPIRED', version = version + 1, updated_at = #{now}
        WHERE policy_id = #{policyId,jdbcType=BINARY}
          AND status = 'HELD' AND expires_at <= #{now}
        """)
    int expireReservations(@Param("policyId") UUID policyId, @Param("now") Instant now);

    /**
     * 汇总指定 Policy 当前 HELD 预留量。
     *
     * @param policyId Policy UUID
     * @param now      当前时间
     * @return 非负预留量
     */
    @Select("""
        SELECT COALESCE(SUM(amount), 0)
        FROM quota_reservation
        WHERE policy_id = #{policyId,jdbcType=BINARY}
          AND status = 'HELD' AND expires_at > #{now}
        """)
    BigDecimal sumHeldReservations(
        @Param("policyId") UUID policyId, @Param("now") Instant now);

    /**
     * 查询时间窗口内已记录用量。
     *
     * @param projectId 项目 UUID
     * @param scopeType Scope 类型
     * @param scopeRef  Scope 引用
     * @param metric    Metric
     * @param from      窗口开始
     * @param to        窗口结束
     * @return 非负已用量
     */
    @Select("""
        <script>
        SELECT COALESCE(SUM(
          CASE #{metric}
            WHEN 'REQUEST_RATE' THEN 1
            WHEN 'INPUT_TOKEN' THEN input_tokens
            WHEN 'OUTPUT_TOKEN' THEN output_tokens
            WHEN 'COST' THEN cost_amount
            ELSE 0
          END), 0)
        FROM usage_ledger
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND occurred_at &gt;= #{from} AND occurred_at &lt; #{to}
        <if test="scopeType == 'DEPLOYMENT'">
          AND deployment_id = UUID_TO_BIN(#{scopeRef})
        </if>
        <if test="scopeType == 'MODEL'">
          AND model = #{scopeRef}
        </if>
        </script>
        """)
    BigDecimal sumWindowUsage(
        @Param("projectId") UUID projectId,
        @Param("scopeType") String scopeType,
        @Param("scopeRef") String scopeRef,
        @Param("metric") String metric,
        @Param("from") Instant from,
        @Param("to") Instant to);

    /**
     * 按幂等键读取 Reservation。
     *
     * @param policyId      Policy UUID
     * @param idempotencyKey 幂等键
     * @return Reservation 行
     */
    @Select("""
        SELECT id, policy_id, amount, status, expires_at, version
        FROM quota_reservation
        WHERE policy_id = #{policyId,jdbcType=BINARY}
          AND idempotency_key = #{idempotencyKey}
        """)
    Optional<ReservationRow> findReservationByKey(
        @Param("policyId") UUID policyId,
        @Param("idempotencyKey") String idempotencyKey);

    /**
     * 插入 HELD Reservation。
     *
     * @param row Reservation 行
     */
    @Insert("""
        INSERT INTO quota_reservation
            (id, organization_id, project_id, policy_id, idempotency_key, subject_ref,
             amount, status, expires_at, version, created_at, updated_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{policyId,jdbcType=BINARY},
             #{idempotencyKey}, #{subjectRef}, #{amount}, 'HELD', #{expiresAt},
             0, #{createdAt}, #{createdAt})
        """)
    void insertReservation(ReservationInsert row);

    /**
     * 幂等转换 Reservation 终态。
     *
     * @param id     Reservation UUID
     * @param target 目标状态
     * @param now    当前时间
     * @return 更新数量
     */
    @Update("""
        UPDATE quota_reservation
        SET status = #{target}, version = version + 1, updated_at = #{now}
        WHERE id = #{id,jdbcType=BINARY}
          AND status = 'HELD'
        """)
    int transitionReservation(
        @Param("id") UUID id, @Param("target") String target, @Param("now") Instant now);

    /**
     * 读取 Reservation 当前状态。
     *
     * @param id Reservation UUID
     * @return Reservation 行
     */
    @Select("""
        SELECT id, policy_id, amount, status, expires_at, version
        FROM quota_reservation
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<ReservationRow> findReservation(@Param("id") UUID id);

    /**
     * 插入 Evaluation Dataset。
     *
     * @param row   Dataset 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluation_dataset
            (id, organization_id, project_id, dataset_key, name, description, status,
             version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.datasetKey}, #{row.name},
             #{row.description}, #{row.status}, #{row.version}, #{row.createdAt},
             #{actor}, #{row.createdAt}, #{actor})
        """)
    void insertDataset(@Param("row") DatasetRow row, @Param("actor") String actor);

    /**
     * 插入不可变 Dataset Version。
     *
     * @param row   Version 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluation_dataset_version
            (id, organization_id, project_id, dataset_id, version_number, schema_json,
             content_hash, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.datasetId,jdbcType=BINARY},
             #{row.versionNumber}, #{row.schemaJson}, #{row.contentHash,jdbcType=BINARY},
             #{row.createdAt}, #{actor})
        """)
    void insertDatasetVersion(@Param("row") DatasetVersionRow row, @Param("actor") String actor);

    /**
     * 插入不可变 Test Case。
     *
     * @param row   Case 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluation_test_case
            (id, organization_id, project_id, dataset_version_id, case_key,
             input_object_uri, input_content_hash, expected_json, expected_content_hash,
             weight, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.datasetVersionId,jdbcType=BINARY},
             #{row.caseKey}, #{row.inputObjectUri}, #{row.inputContentHash,jdbcType=BINARY},
             #{row.expectedJson}, #{row.expectedContentHash,jdbcType=BINARY},
             #{row.weight}, #{row.createdAt}, #{actor})
        """)
    void insertTestCase(@Param("row") TestCaseRow row, @Param("actor") String actor);

    /**
     * 列出项目 Dataset。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return Dataset 行
     */
    @Select("""
        SELECT id, organization_id, project_id, dataset_key, name, description,
               status, version, created_at
        FROM evaluation_dataset
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<DatasetRow> listDatasets(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 读取固定 Dataset Version。
     *
     * @param projectId 项目 UUID
     * @param versionId Version UUID
     * @return Version 行
     */
    @Select("""
        SELECT id, organization_id, project_id, dataset_id, version_number,
               schema_json, content_hash, created_at
        FROM evaluation_dataset_version
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{versionId,jdbcType=BINARY}
        """)
    Optional<DatasetVersionRow> findDatasetVersion(
        @Param("projectId") UUID projectId, @Param("versionId") UUID versionId);

    /**
     * 列出固定 Dataset Version 的 Test Case。
     *
     * @param projectId 项目 UUID
     * @param versionId Version UUID
     * @return Case 行
     */
    @Select("""
        SELECT id, organization_id, project_id, dataset_version_id, case_key,
               input_object_uri, input_content_hash, expected_json, expected_content_hash,
               weight, created_at
        FROM evaluation_test_case
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND dataset_version_id = #{versionId,jdbcType=BINARY}
        ORDER BY id
        """)
    List<TestCaseRow> listTestCases(
        @Param("projectId") UUID projectId, @Param("versionId") UUID versionId);

    /**
     * 插入 Evaluator。
     *
     * @param row   Evaluator 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluator
            (id, organization_id, project_id, evaluator_key, name, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.evaluatorKey}, #{row.name},
             #{row.status}, #{row.version}, #{row.createdAt}, #{actor}, #{row.createdAt}, #{actor})
        """)
    void insertEvaluator(@Param("row") EvaluatorRow row, @Param("actor") String actor);

    /**
     * 插入不可变 Evaluator Version。
     *
     * @param row   Version 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluator_version
            (id, organization_id, project_id, evaluator_id, version_number,
             evaluator_type, config_json, content_hash, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.evaluatorId,jdbcType=BINARY},
             #{row.versionNumber}, #{row.evaluatorType}, #{row.configJson},
             #{row.contentHash,jdbcType=BINARY}, #{row.createdAt}, #{actor})
        """)
    void insertEvaluatorVersion(
        @Param("row") EvaluatorVersionRow row, @Param("actor") String actor);

    /**
     * 列出项目 Evaluator。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return Evaluator 行
     */
    @Select("""
        SELECT id, organization_id, project_id, evaluator_key, name, status, version, created_at
        FROM evaluator
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<EvaluatorRow> listEvaluators(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 读取固定 Evaluator Version。
     *
     * @param projectId 项目 UUID
     * @param versionId Version UUID
     * @return Version 行
     */
    @Select("""
        SELECT id, organization_id, project_id, evaluator_id, version_number,
               evaluator_type, config_json, content_hash, created_at
        FROM evaluator_version
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{versionId,jdbcType=BINARY}
        """)
    Optional<EvaluatorVersionRow> findEvaluatorVersion(
        @Param("projectId") UUID projectId, @Param("versionId") UUID versionId);

    /**
     * 插入终态 Evaluation Run。
     *
     * @param row   Run 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO evaluation_run
            (id, organization_id, project_id, candidate_revision_id, candidate_snapshot_id,
             dataset_version_id, evaluator_version_id, provider, model, threshold,
             baseline_run_id, status, total_score, regression_delta, failure_code,
             created_at, created_by, started_at, completed_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.candidateRevisionId,jdbcType=BINARY},
             #{row.candidateSnapshotId,jdbcType=BINARY}, #{row.datasetVersionId,jdbcType=BINARY},
             #{row.evaluatorVersionId,jdbcType=BINARY}, #{row.provider}, #{row.model},
             #{row.threshold}, #{row.baselineRunId,jdbcType=BINARY}, #{row.status},
             #{row.totalScore}, #{row.regressionDelta}, NULL, #{row.createdAt}, #{actor},
             #{row.createdAt}, #{row.completedAt})
        """)
    void insertEvaluationRun(@Param("row") EvaluationRunRow row, @Param("actor") String actor);

    /**
     * 插入只追加 Evaluation Score。
     *
     * @param row Score 行
     */
    @Insert("""
        INSERT INTO evaluation_score
            (id, organization_id, project_id, evaluation_run_id, test_case_id,
             metric_key, score, passed, details_json, created_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{evaluationRunId,jdbcType=BINARY},
             #{testCaseId,jdbcType=BINARY}, #{metricKey}, #{score}, #{passed},
             #{detailsJson}, #{createdAt})
        """)
    void insertEvaluationScore(EvaluationScoreRow row);

    /**
     * 列出项目 Evaluation Run。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return Run 行
     */
    @Select("""
        SELECT id, organization_id, project_id, candidate_revision_id, candidate_snapshot_id,
               dataset_version_id, evaluator_version_id, provider, model, threshold,
               baseline_run_id, status, total_score, regression_delta, created_at, completed_at
        FROM evaluation_run
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        """)
    List<EvaluationRunRow> listEvaluationRuns(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 读取 Evaluation Run。
     *
     * @param projectId 项目 UUID
     * @param runId     Run UUID
     * @return Run 行
     */
    @Select("""
        SELECT id, organization_id, project_id, candidate_revision_id, candidate_snapshot_id,
               dataset_version_id, evaluator_version_id, provider, model, threshold,
               baseline_run_id, status, total_score, regression_delta, created_at, completed_at
        FROM evaluation_run
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{runId,jdbcType=BINARY}
        """)
    Optional<EvaluationRunRow> findEvaluationRun(
        @Param("projectId") UUID projectId, @Param("runId") UUID runId);

    /**
     * 插入 Release Gate。
     *
     * @param row   Gate 行
     * @param actor 操作主体
     */
    @Insert("""
        INSERT INTO release_gate
            (id, organization_id, project_id, agent_id, environment_id,
             dataset_version_id, evaluator_version_id, threshold, enforcement,
             status, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.agentId,jdbcType=BINARY},
             #{row.environmentId,jdbcType=BINARY}, #{row.datasetVersionId,jdbcType=BINARY},
             #{row.evaluatorVersionId,jdbcType=BINARY}, #{row.threshold}, #{row.enforcement},
             #{row.status}, #{row.version}, #{now}, #{actor}, #{now}, #{actor})
        """)
    void insertReleaseGate(
        @Param("row") ReleaseGateRow row,
        @Param("actor") String actor,
        @Param("now") Instant now);

    /**
     * 乐观锁更新 Release Gate。
     *
     * @param row             Gate 行
     * @param expectedVersion 预期版本
     * @param actor           操作主体
     * @param now             当前时间
     * @return 更新数量
     */
    @Update("""
        UPDATE release_gate
        SET dataset_version_id = #{row.datasetVersionId,jdbcType=BINARY},
            evaluator_version_id = #{row.evaluatorVersionId,jdbcType=BINARY},
            threshold = #{row.threshold}, enforcement = #{row.enforcement},
            status = #{row.status}, version = version + 1, updated_at = #{now}, updated_by = #{actor}
        WHERE id = #{row.id,jdbcType=BINARY} AND project_id = #{row.projectId,jdbcType=BINARY}
          AND version = #{expectedVersion}
        """)
    int updateReleaseGate(
        @Param("row") ReleaseGateRow row,
        @Param("expectedVersion") long expectedVersion,
        @Param("actor") String actor,
        @Param("now") Instant now);

    /**
     * 读取 Release Gate。
     *
     * @param projectId 项目 UUID
     * @param gateId    Gate UUID
     * @return Gate 行
     */
    @Select("""
        SELECT id, organization_id, project_id, agent_id, environment_id,
               dataset_version_id, evaluator_version_id, threshold, enforcement,
               status, version
        FROM release_gate
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{gateId,jdbcType=BINARY}
        """)
    Optional<ReleaseGateRow> findReleaseGate(
        @Param("projectId") UUID projectId, @Param("gateId") UUID gateId);

    /**
     * 列出项目 Release Gate。
     *
     * @param projectId 项目 UUID
     * @param limit     最大数量
     * @return Gate 行
     */
    @Select("""
        SELECT id, organization_id, project_id, agent_id, environment_id,
               dataset_version_id, evaluator_version_id, threshold, enforcement,
               status, version
        FROM release_gate
        WHERE project_id = #{projectId,jdbcType=BINARY}
        ORDER BY id DESC
        LIMIT #{limit}
        """)
    List<ReleaseGateRow> listReleaseGates(
        @Param("projectId") UUID projectId, @Param("limit") int limit);

    /**
     * 读取 Agent/Environment 当前活动 Gate。
     *
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param agentId        Agent UUID
     * @param environmentId  Environment UUID
     * @return Gate 行
     */
    @Select("""
        SELECT id, organization_id, project_id, agent_id, environment_id,
               dataset_version_id, evaluator_version_id, threshold, enforcement,
               status, version
        FROM release_gate
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND agent_id = #{agentId,jdbcType=BINARY}
          AND status = 'ACTIVE'
          AND (environment_id IS NULL OR environment_id = #{environmentId,jdbcType=BINARY})
        ORDER BY environment_id IS NOT NULL DESC, id DESC
        LIMIT 1
        """)
    Optional<ReleaseGateRow> findActiveReleaseGate(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("agentId") UUID agentId,
        @Param("environmentId") UUID environmentId);

    /**
     * 查询目标 Revision 满足 Gate 的最新通过 Run。
     *
     * @param projectId         项目 UUID
     * @param revisionId        Revision UUID
     * @param datasetVersionId  Dataset Version UUID
     * @param evaluatorVersionId Evaluator Version UUID
     * @param threshold         Gate 阈值
     * @return 通过 Run UUID
     */
    @Select("""
        SELECT id
        FROM evaluation_run
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND candidate_revision_id = #{revisionId,jdbcType=BINARY}
          AND dataset_version_id = #{datasetVersionId,jdbcType=BINARY}
          AND evaluator_version_id = #{evaluatorVersionId,jdbcType=BINARY}
          AND status = 'PASSED' AND total_score >= #{threshold}
        ORDER BY completed_at DESC, id DESC
        LIMIT 1
        """)
    Optional<UUID> findPassingEvaluation(
        @Param("projectId") UUID projectId,
        @Param("revisionId") UUID revisionId,
        @Param("datasetVersionId") UUID datasetVersionId,
        @Param("evaluatorVersionId") UUID evaluatorVersionId,
        @Param("threshold") BigDecimal threshold);

    /**
     * 查询项目治理概览。
     *
     * @param projectId 项目 UUID
     * @param from      统计开始时间
     * @return 概览行
     */
    @Select("""
        SELECT
          (SELECT COUNT(*) FROM audit_event WHERE project_id = #{projectId,jdbcType=BINARY}
             AND occurred_at >= #{from}) AS audit_count,
          (SELECT COUNT(*) FROM quota_policy WHERE project_id = #{projectId,jdbcType=BINARY}
             AND status = 'ACTIVE') AS active_quota_count,
          (SELECT COUNT(*) FROM evaluation_run WHERE project_id = #{projectId,jdbcType=BINARY}
             AND created_at >= #{from}) AS evaluation_run_count,
          (SELECT COALESCE(SUM(cost_amount), 0) FROM usage_ledger
             WHERE project_id = #{projectId,jdbcType=BINARY} AND occurred_at >= #{from}) AS cost_amount,
          (SELECT COALESCE(SUM(input_tokens + output_tokens + embedding_tokens), 0)
             FROM usage_ledger WHERE project_id = #{projectId,jdbcType=BINARY}
             AND occurred_at >= #{from}) AS token_count
        """)
    OverviewRow overview(@Param("projectId") UUID projectId, @Param("from") Instant from);

    /**
     * Audit Event 数据库行。
     *
     * @param id              主键
     * @param sourceEventId   来源事件
     * @param sourcePlane     来源平面
     * @param organizationId  组织
     * @param projectId       项目
     * @param principalType   主体类型
     * @param principalRef    主体引用
     * @param scopeType       Scope 类型
     * @param scopeRef        Scope 引用
     * @param action          动作
     * @param result          结果
     * @param resourceType    资源类型
     * @param resourceRef     资源引用
     * @param diffSummaryJson 差异 JSON
     * @param policyVersion   策略版本
     * @param roleVersion     角色版本
     * @param traceId         追踪标识
     * @param requestId       请求标识
     * @param occurredAt      发生时间
     * @param ingestedAt      接收时间
     * @author refinex
     */
    record AuditRow(
        UUID id, String sourceEventId, String sourcePlane, UUID organizationId, UUID projectId,
        String principalType, String principalRef, String scopeType, String scopeRef,
        String action, String result, String resourceType, String resourceRef,
        String diffSummaryJson, String policyVersion, Long roleVersion, String traceId,
        String requestId, Instant occurredAt, Instant ingestedAt) {
    }

    /**
     * 价格表数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param priceKey 价格表键
     * @param name 显示名称
     * @param status 生命周期状态
     * @param version 乐观锁版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @author refinex
     */
    record PriceTableRow(UUID id, UUID organizationId, UUID projectId, String priceKey,
                         String name, String status, long version, Instant createdAt,
                         Instant updatedAt) {
    }

    /**
     * 不可变价格版本数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param priceTableId 价格表标识
     * @param versionNumber 版本序号
     * @param currency 计价币种
     * @param effectiveFrom 起效时间
     * @param entriesJson 价格项数据
     * @param contentHash 内容摘要
     * @param createdAt 创建时间
     * @author refinex
     */
    record PriceVersionRow(UUID id, UUID organizationId, UUID projectId, UUID priceTableId,
                           long versionNumber, String currency, Instant effectiveFrom,
                           String entriesJson, byte[] contentHash, Instant createdAt) {
    }

    /**
     * 治理用量明细数据库行。
     *
     * @param id 数据库主键
     * @param sourcePlane 来源平面
     * @param sourceRecordId 来源记录标识
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param agentId 可选代理标识
     * @param revisionId 可选修订标识
     * @param deploymentId 可选部署标识
     * @param sessionId 可选会话标识
     * @param turnId 可选轮次标识
     * @param runId 可选运行标识
     * @param usageType 计量类型
     * @param provider 供应方标识
     * @param model 可选模型名称
     * @param tool 可选工具名称
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     * @param cachedTokens 缓存令牌数
     * @param embeddingTokens 嵌入令牌数
     * @param toolCalls 工具调用次数
     * @param sandboxDurationMs 沙箱时长毫秒数
     * @param estimated 是否为估算值
     * @param priceTableVersionId 可选价格版本
     * @param currency 可选计价币种
     * @param costAmount 成本金额
     * @param occurredAt 发生时间
     * @param ingestedAt 接收时间
     * @author refinex
     */
    record UsageRow(UUID id, String sourcePlane, String sourceRecordId, UUID organizationId,
                    UUID projectId, UUID agentId, UUID revisionId, UUID deploymentId,
                    UUID sessionId, UUID turnId, UUID runId, String usageType, String provider,
                    String model, String tool, long inputTokens, long outputTokens,
                    long cachedTokens, long embeddingTokens, long toolCalls,
                    long sandboxDurationMs, boolean estimated, UUID priceTableVersionId,
                    String currency, BigDecimal costAmount, Instant occurredAt,
                    Instant ingestedAt) {
    }

    /**
     * 用量聚合增量数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param periodStart 窗口开始时间
     * @param periodEnd 窗口结束时间
     * @param dimensionRef 项目维度引用
     * @param provider 供应方标识
     * @param modelKey 模型维度键
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     * @param cachedTokens 缓存令牌数
     * @param embeddingTokens 嵌入令牌数
     * @param toolCalls 工具调用次数
     * @param sandboxDurationMs 沙箱时长毫秒数
     * @param estimatedRecords 估算记录数量
     * @param costAmount 成本金额
     * @param currency 计价币种
     * @param priceTableVersionId 可选价格版本
     * @param updatedAt 更新时间
     * @author refinex
     */
    record UsageAggregateDelta(UUID id, UUID organizationId, UUID projectId,
                               Instant periodStart, Instant periodEnd, String dimensionRef,
                               String provider, String modelKey, long inputTokens,
                               long outputTokens, long cachedTokens, long embeddingTokens,
                               long toolCalls, long sandboxDurationMs, long estimatedRecords,
                               BigDecimal costAmount, String currency, UUID priceTableVersionId,
                               Instant updatedAt) {
    }

    /**
     * 用量聚合查询数据库行。
     *
     * @param periodStart 窗口开始时间
     * @param periodEnd 窗口结束时间
     * @param dimensionType 聚合维度类型
     * @param dimensionRef 聚合维度引用
     * @param provider 供应方标识
     * @param modelKey 模型维度键
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     * @param cachedTokens 缓存令牌数
     * @param embeddingTokens 嵌入令牌数
     * @param toolCalls 工具调用次数
     * @param sandboxDurationMs 沙箱时长毫秒数
     * @param estimatedRecords 估算记录数量
     * @param sourceRecords 来源明细数量
     * @param costAmount 成本金额
     * @param currency 计价币种
     * @author refinex
     */
    record UsageAggregateRow(Instant periodStart, Instant periodEnd, String dimensionType,
                             String dimensionRef, String provider, String modelKey,
                             long inputTokens, long outputTokens, long cachedTokens,
                             long embeddingTokens, long toolCalls, long sandboxDurationMs,
                             long estimatedRecords, long sourceRecords, BigDecimal costAmount,
                             String currency) {
    }

    /**
     * 配额策略数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param scopeType 作用域类型
     * @param scopeRef 作用域引用
     * @param metric 配额指标
     * @param enforcement 软硬执行方式
     * @param limitValue 配额上限
     * @param windowSeconds 可选窗口秒数
     * @param budgetAction 预算超限动作
     * @param effectiveFrom 起效时间
     * @param effectiveUntil 可选失效时间
     * @param status 生命周期状态
     * @param version 乐观锁版本
     * @param createdAt 创建时间
     * @author refinex
     */
    record QuotaPolicyRow(UUID id, UUID organizationId, UUID projectId, String scopeType,
                          String scopeRef, String metric, String enforcement,
                          BigDecimal limitValue, Long windowSeconds, String budgetAction,
                          Instant effectiveFrom, Instant effectiveUntil, String status,
                          long version, Instant createdAt) {
    }

    /**
     * 配额预留查询数据库行。
     *
     * @param id 数据库主键
     * @param policyId 配额策略标识
     * @param amount 预留数量
     * @param status 预留状态
     * @param expiresAt 到期时间
     * @param version 乐观锁版本
     * @author refinex
     */
    record ReservationRow(UUID id, UUID policyId, BigDecimal amount, String status,
                          Instant expiresAt, long version) {
    }

    /**
     * 配额预留插入数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param policyId 配额策略标识
     * @param idempotencyKey 幂等键
     * @param subjectRef 工作引用
     * @param amount 预留数量
     * @param expiresAt 到期时间
     * @param createdAt 创建时间
     * @author refinex
     */
    record ReservationInsert(UUID id, UUID organizationId, UUID projectId, UUID policyId,
                             String idempotencyKey, String subjectRef, BigDecimal amount,
                             Instant expiresAt, Instant createdAt) {
    }

    /**
     * 评估数据集数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param datasetKey 数据集键
     * @param name 显示名称
     * @param description 可选用途说明
     * @param status 生命周期状态
     * @param version 乐观锁版本
     * @param createdAt 创建时间
     * @author refinex
     */
    record DatasetRow(UUID id, UUID organizationId, UUID projectId, String datasetKey,
                      String name, String description, String status, long version,
                      Instant createdAt) {
    }

    /**
     * 不可变数据集版本数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param datasetId 数据集标识
     * @param versionNumber 版本序号
     * @param schemaJson 用例结构数据
     * @param contentHash 内容摘要
     * @param createdAt 创建时间
     * @author refinex
     */
    record DatasetVersionRow(UUID id, UUID organizationId, UUID projectId, UUID datasetId,
                             long versionNumber, String schemaJson, byte[] contentHash,
                             Instant createdAt) {
    }

    /**
     * 评估测试用例数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param datasetVersionId 数据集版本标识
     * @param caseKey 用例键
     * @param inputObjectUri 输入对象引用地址
     * @param inputContentHash 输入内容摘要
     * @param expectedJson 期望结果数据
     * @param expectedContentHash 期望内容摘要
     * @param weight 评分权重
     * @param createdAt 创建时间
     * @author refinex
     */
    record TestCaseRow(UUID id, UUID organizationId, UUID projectId, UUID datasetVersionId,
                       String caseKey, String inputObjectUri, byte[] inputContentHash,
                       String expectedJson, byte[] expectedContentHash, BigDecimal weight,
                       Instant createdAt) {
    }

    /**
     * 评估器数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param evaluatorKey 评估器键
     * @param name 显示名称
     * @param status 生命周期状态
     * @param version 乐观锁版本
     * @param createdAt 创建时间
     * @author refinex
     */
    record EvaluatorRow(UUID id, UUID organizationId, UUID projectId, String evaluatorKey,
                        String name, String status, long version, Instant createdAt) {
    }

    /**
     * 不可变评估器版本数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param evaluatorId 评估器标识
     * @param versionNumber 版本序号
     * @param evaluatorType 评估器类型
     * @param configJson 安全配置数据
     * @param contentHash 内容摘要
     * @param createdAt 创建时间
     * @author refinex
     */
    record EvaluatorVersionRow(UUID id, UUID organizationId, UUID projectId, UUID evaluatorId,
                               long versionNumber, String evaluatorType, String configJson,
                               byte[] contentHash, Instant createdAt) {
    }

    /**
     * 评估运行数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param candidateRevisionId 候选修订标识
     * @param candidateSnapshotId 候选快照标识
     * @param datasetVersionId 数据集版本标识
     * @param evaluatorVersionId 评估器版本标识
     * @param provider 执行供应方
     * @param model 可选模型名称
     * @param threshold 通过阈值
     * @param baselineRunId 可选基准运行标识
     * @param status 运行状态
     * @param totalScore 可选总分
     * @param regressionDelta 可选回归差值
     * @param createdAt 创建时间
     * @param completedAt 可选完成时间
     * @author refinex
     */
    record EvaluationRunRow(UUID id, UUID organizationId, UUID projectId,
                            UUID candidateRevisionId, UUID candidateSnapshotId,
                            UUID datasetVersionId, UUID evaluatorVersionId, String provider,
                            String model, BigDecimal threshold, UUID baselineRunId,
                            String status, BigDecimal totalScore, BigDecimal regressionDelta,
                            Instant createdAt, Instant completedAt) {
    }

    /**
     * 评估分数数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param evaluationRunId 评估运行标识
     * @param testCaseId 测试用例标识
     * @param metricKey 评分指标键
     * @param score 分数
     * @param passed 是否通过
     * @param detailsJson 安全评分详情
     * @param createdAt 创建时间
     * @author refinex
     */
    record EvaluationScoreRow(UUID id, UUID organizationId, UUID projectId,
                              UUID evaluationRunId, UUID testCaseId, String metricKey,
                              BigDecimal score, boolean passed, String detailsJson,
                              Instant createdAt) {
    }

    /**
     * 发布门禁数据库行。
     *
     * @param id 数据库主键
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param agentId 代理标识
     * @param environmentId 可选环境标识
     * @param datasetVersionId 数据集版本标识
     * @param evaluatorVersionId 评估器版本标识
     * @param threshold 通过阈值
     * @param enforcement 软硬执行方式
     * @param status 门禁状态
     * @param version 乐观锁版本
     * @author refinex
     */
    record ReleaseGateRow(UUID id, UUID organizationId, UUID projectId, UUID agentId,
                          UUID environmentId, UUID datasetVersionId, UUID evaluatorVersionId,
                          BigDecimal threshold, String enforcement, String status, long version) {
    }

    /**
     * 治理概览数据库行。
     *
     * @param auditCount 审计事件数量
     * @param activeQuotaCount 活动配额数量
     * @param evaluationRunCount 评估运行数量
     * @param costAmount 成本金额
     * @param tokenCount 令牌总数
     * @author refinex
     */
    record OverviewRow(long auditCount, long activeQuotaCount, long evaluationRunCount,
                       BigDecimal costAmount, long tokenCount) {
    }
}
