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

package space.refinex.agentark.knowledge.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import space.refinex.agentark.knowledge.adapter.out.persistence.KnowledgePersistenceRows.IngestionResultRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行 Knowledge 摄取结果和 Control Outbox 的显式 SQL。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KnowledgeIngestionResultMapper {

    /**
     * 插入一个不可变摄取 Attempt 结果。
     *
     * @param row 摄取结果数据库行
     */
    @Insert("""
        INSERT INTO knowledge_ingestion_result
            (id, request_id, organization_id, project_id, knowledge_revision_id,
             scheduler_job_id, attempt_id, idempotency_key, document_count, chunk_count,
             checksum, artifact_refs_json, status, failure_code, completed_at, created_at,
             created_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{requestId,jdbcType=BINARY},
             #{organizationId,jdbcType=BINARY}, #{projectId,jdbcType=BINARY},
             #{knowledgeRevisionId,jdbcType=BINARY}, #{schedulerJobId,jdbcType=BINARY},
             #{attemptId,jdbcType=BINARY}, #{idempotencyKey}, #{documentCount}, #{chunkCount},
             #{checksum,jdbcType=BINARY}, #{artifactRefsJson}, #{status}, #{failureCode},
             #{completedAt}, #{createdAt}, #{createdBy})
        """)
    void insertResult(IngestionResultRow row);

    /**
     * 按项目和幂等键查询结果。
     *
     * @param projectId      项目 UUID
     * @param idempotencyKey 幂等键
     * @return 摄取结果行
     */
    @Select("""
        SELECT id, request_id, organization_id, project_id, knowledge_revision_id,
               scheduler_job_id, attempt_id, idempotency_key, document_count, chunk_count,
               checksum, artifact_refs_json, status, failure_code, completed_at, created_at,
               created_by
        FROM knowledge_ingestion_result
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND idempotency_key = #{idempotencyKey}
        """)
    Optional<IngestionResultRow> findByIdempotencyKey(
        @Param("projectId") UUID projectId,
        @Param("idempotencyKey") String idempotencyKey);

    /**
     * 按 Revision 与 Attempt 查询结果。
     *
     * @param revisionId Knowledge Revision UUID
     * @param attemptId  Attempt UUID
     * @return 摄取结果行
     */
    @Select("""
        SELECT id, request_id, organization_id, project_id, knowledge_revision_id,
               scheduler_job_id, attempt_id, idempotency_key, document_count, chunk_count,
               checksum, artifact_refs_json, status, failure_code, completed_at, created_at,
               created_by
        FROM knowledge_ingestion_result
        WHERE knowledge_revision_id = #{revisionId,jdbcType=BINARY}
          AND attempt_id = #{attemptId,jdbcType=BINARY}
        """)
    Optional<IngestionResultRow> findByAttempt(
        @Param("revisionId") UUID revisionId,
        @Param("attemptId") UUID attemptId);

    /**
     * 写入与 Knowledge Revision 状态转换同事务的 Control Outbox。
     *
     * @param id          Outbox Event UUID
     * @param aggregateId Knowledge Revision UUID 字符串
     * @param eventType   稳定事件类型
     * @param payloadJson 非敏感 JSON
     * @param createdAt   创建时间
     */
    @Insert("""
        INSERT INTO control_outbox
            (id, aggregate_type, aggregate_id, event_type, payload_json, status, attempts,
             available_at, created_at, published_at)
        VALUES
            (#{id,jdbcType=BINARY}, 'knowledge_revision', #{aggregateId}, #{eventType},
             #{payloadJson}, 'PENDING', 0, #{createdAt}, #{createdAt}, NULL)
        """)
    void insertOutbox(
        @Param("id") UUID id,
        @Param("aggregateId") String aggregateId,
        @Param("eventType") String eventType,
        @Param("payloadJson") String payloadJson,
        @Param("createdAt") Instant createdAt);
}
