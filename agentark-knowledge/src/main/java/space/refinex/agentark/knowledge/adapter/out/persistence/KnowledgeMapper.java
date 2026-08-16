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
import org.apache.ibatis.annotations.*;
import space.refinex.agentark.knowledge.adapter.out.persistence.KnowledgePersistenceRows.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行显式 Project Scope 的 Knowledge SQL，并忽略全局租户插件的重复条件注入。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KnowledgeMapper {

    /**
     * @param row Knowledge Base 数据库行
     */
    @Insert("""
        INSERT INTO knowledge_base
            (id, organization_id, project_id, knowledge_key, name, description, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeKey}, #{name}, #{description}, #{status},
             #{version}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy})
        """)
    void insertKnowledgeBase(BaseRow row);

    /**
     * @param projectId 项目 UUID
     * @param id        Knowledge Base UUID
     * @return 同项目数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_key, name, description, status, version,
               created_at, created_by, updated_at, updated_by
        FROM knowledge_base
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<BaseRow> findKnowledgeBase(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * @param projectId 项目 UUID
     * @param afterId   上一页最后一个 UUID
     * @param limit     最大结果数
     * @return 按 UUIDv7 排序的数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_key, name, description, status, version,
               created_at, created_by, updated_at, updated_by
        FROM knowledge_base
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND (#{afterId,jdbcType=BINARY} IS NULL OR id > #{afterId,jdbcType=BINARY})
        ORDER BY id
        LIMIT #{limit}
        """)
    List<BaseRow> listKnowledgeBases(
        @Param("projectId") UUID projectId,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * @param row 数据源数据库行
     */
    @Insert("""
        INSERT INTO data_source
            (id, organization_id, project_id, knowledge_base_id, source_type, name,
             descriptor_json, created_at, created_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeBaseId,jdbcType=BINARY}, #{sourceType},
             #{name}, #{descriptorJson}, #{createdAt}, #{createdBy})
        """)
    void insertDataSource(DataSourceRow row);

    /**
     * @param projectId 项目 UUID
     * @param id        数据源 UUID
     * @return 同项目数据源行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, source_type, name,
               descriptor_json, created_at, created_by
        FROM data_source
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<DataSourceRow> findDataSource(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * @param projectId       项目 UUID
     * @param knowledgeBaseId Knowledge Base UUID
     * @param afterId         上一页最后一个 UUID
     * @param limit           最大结果数
     * @return 数据源行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, source_type, name,
               descriptor_json, created_at, created_by
        FROM data_source
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND knowledge_base_id = #{knowledgeBaseId,jdbcType=BINARY}
          AND (#{afterId,jdbcType=BINARY} IS NULL OR id > #{afterId,jdbcType=BINARY})
        ORDER BY id
        LIMIT #{limit}
        """)
    List<DataSourceRow> listDataSources(
        @Param("projectId") UUID projectId,
        @Param("knowledgeBaseId") UUID knowledgeBaseId,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * @param row 文档数据库行
     */
    @Insert("""
        INSERT INTO document
            (id, organization_id, project_id, knowledge_base_id, data_source_id, title,
             metadata_json, status, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeBaseId,jdbcType=BINARY},
             #{dataSourceId,jdbcType=BINARY}, #{title}, #{metadataJson}, #{status}, #{version},
             #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy})
        """)
    void insertDocument(DocumentRow row);

    /**
     * @param row 文档 ACL 数据库行
     */
    @Insert("""
        INSERT INTO document_acl
            (document_id, organization_id, project_id, subject_type, subject_id, access_level,
             created_at, created_by)
        VALUES
            (#{documentId,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{subjectType}, #{subjectId,jdbcType=BINARY},
             #{accessLevel}, #{createdAt}, #{createdBy})
        """)
    void insertDocumentAcl(AclRow row);

    /**
     * @param row 文档修订数据库行
     */
    @Insert("""
        INSERT INTO document_revision
            (id, organization_id, project_id, knowledge_base_id, document_id, revision_number,
             original_file_name, object_uri, content_hash, content_size, content_type, created_at,
             created_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeBaseId,jdbcType=BINARY},
             #{documentId,jdbcType=BINARY}, #{revisionNumber}, #{originalFileName}, #{objectUri},
             #{contentHash,jdbcType=BINARY}, #{contentSize}, #{contentType}, #{createdAt},
             #{createdBy})
        """)
    void insertDocumentRevision(DocumentRevisionRow row);

    /**
     * @param projectId 项目 UUID
     * @param id        文档 UUID
     * @return 同项目文档行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, data_source_id, title,
               metadata_json, status, version, created_at, created_by, updated_at, updated_by
        FROM document
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<DocumentRow> findDocument(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * @param projectId       项目 UUID
     * @param knowledgeBaseId Knowledge Base UUID
     * @param afterId         上一页最后一个 UUID
     * @param limit           最大结果数
     * @return 文档数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, data_source_id, title,
               metadata_json, status, version, created_at, created_by, updated_at, updated_by
        FROM document
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND knowledge_base_id = #{knowledgeBaseId,jdbcType=BINARY}
          AND (#{afterId,jdbcType=BINARY} IS NULL OR id > #{afterId,jdbcType=BINARY})
        ORDER BY id
        LIMIT #{limit}
        """)
    List<DocumentRow> listDocuments(
        @Param("projectId") UUID projectId,
        @Param("knowledgeBaseId") UUID knowledgeBaseId,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * @param projectId  项目 UUID
     * @param documentId 文档 UUID
     * @return 文档 ACL 行
     */
    @Select("""
        SELECT document_id, organization_id, project_id, subject_type, subject_id, access_level,
               created_at, created_by
        FROM document_acl
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND document_id = #{documentId,jdbcType=BINARY}
        ORDER BY subject_type, subject_id
        """)
    List<AclRow> listDocumentAcl(
        @Param("projectId") UUID projectId, @Param("documentId") UUID documentId);

    /**
     * @param projectId 项目 UUID
     * @param id        文档修订 UUID
     * @return 同项目文档修订行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, document_id, revision_number,
               original_file_name, object_uri, content_hash, content_size, content_type,
               created_at, created_by
        FROM document_revision
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<DocumentRevisionRow> findDocumentRevision(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * 锁定项目行，使同项目同 Key 的 Profile 版本号分配串行化。
     *
     * @param projectId 项目 UUID
     * @return 锁定的项目 UUID
     */
    @Select("SELECT id FROM project WHERE id = #{projectId,jdbcType=BINARY} FOR UPDATE")
    Optional<UUID> lockProject(@Param("projectId") UUID projectId);

    /**
     * @param table 受控 Profile 表
     * @param row   Profile 数据库行
     */
    @InsertProvider(type = KnowledgeSqlProvider.class, method = "insertProfile")
    void insertProfile(@Param("table") ProfileTable table, @Param("row") ProfileRow row);

    /**
     * @param table     受控 Profile 表
     * @param projectId 项目 UUID
     * @param id        Profile UUID
     * @return 同项目 Profile 行
     */
    @SelectProvider(type = KnowledgeSqlProvider.class, method = "findProfile")
    Optional<ProfileRow> findProfile(
        @Param("table") ProfileTable table,
        @Param("projectId") UUID projectId,
        @Param("id") UUID id);

    /**
     * @param table      受控 Profile 表
     * @param projectId  项目 UUID
     * @param profileKey 稳定 Key
     * @return 下一个版本号
     */
    @SelectProvider(type = KnowledgeSqlProvider.class, method = "nextProfileVersion")
    long nextProfileVersion(
        @Param("table") ProfileTable table,
        @Param("projectId") UUID projectId,
        @Param("profileKey") String profileKey);

    /**
     * 锁定 Knowledge Base 行，使 Revision 版本号分配串行化。
     *
     * @param projectId       项目 UUID
     * @param knowledgeBaseId Knowledge Base UUID
     * @return 锁定的 Knowledge Base UUID
     */
    @Select("""
        SELECT id FROM knowledge_base
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND id = #{knowledgeBaseId,jdbcType=BINARY}
        FOR UPDATE
        """)
    Optional<UUID> lockKnowledgeBase(
        @Param("projectId") UUID projectId,
        @Param("knowledgeBaseId") UUID knowledgeBaseId);

    /**
     * @param projectId       项目 UUID
     * @param knowledgeBaseId Knowledge Base UUID
     * @return 下一个 Revision 版本号
     */
    @Select("""
        SELECT COALESCE(MAX(revision_number), 0) + 1
        FROM knowledge_revision
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND knowledge_base_id = #{knowledgeBaseId,jdbcType=BINARY}
        """)
    long nextKnowledgeRevisionNumber(
        @Param("projectId") UUID projectId,
        @Param("knowledgeBaseId") UUID knowledgeBaseId);

    /**
     * @param row Knowledge Revision 数据库行
     */
    @Insert("""
        INSERT INTO knowledge_revision
            (id, organization_id, project_id, knowledge_base_id, revision_number,
             parser_profile_id, chunk_profile_id, embedding_profile_id, retrieval_profile_id,
             content_hash, status, failure_code, version, created_at, created_by, updated_at,
             updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeBaseId,jdbcType=BINARY}, #{revisionNumber},
             #{parserProfileId,jdbcType=BINARY}, #{chunkProfileId,jdbcType=BINARY},
             #{embeddingProfileId,jdbcType=BINARY}, #{retrievalProfileId,jdbcType=BINARY},
             #{contentHash,jdbcType=BINARY}, #{status}, #{failureCode}, #{version}, #{createdAt},
             #{createdBy}, #{updatedAt}, #{updatedBy})
        """)
    void insertKnowledgeRevision(RevisionRow row);

    /**
     * @param revisionId         Knowledge Revision UUID
     * @param organizationId     组织 UUID
     * @param projectId          项目 UUID
     * @param documentRevisionId 文档修订 UUID
     * @param ordinal            文档顺序
     * @param createdAt          创建时间
     * @param createdBy          创建主体
     */
    @Insert("""
        INSERT INTO knowledge_revision_document
            (knowledge_revision_id, organization_id, project_id, document_revision_id,
             ordinal_value, created_at, created_by)
        VALUES
            (#{revisionId,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{documentRevisionId,jdbcType=BINARY}, #{ordinal},
             #{createdAt}, #{createdBy})
        """)
    void insertKnowledgeRevisionDocument(
        @Param("revisionId") UUID revisionId,
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("documentRevisionId") UUID documentRevisionId,
        @Param("ordinal") int ordinal,
        @Param("createdAt") Instant createdAt,
        @Param("createdBy") String createdBy);

    /**
     * @param projectId 项目 UUID
     * @param id        Revision UUID
     * @return 同项目 Revision 行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, revision_number,
               parser_profile_id, chunk_profile_id, embedding_profile_id, retrieval_profile_id,
               content_hash, status, failure_code, version, created_at, created_by, updated_at,
               updated_by
        FROM knowledge_revision
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<RevisionRow> findKnowledgeRevision(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * @param projectId       项目 UUID
     * @param knowledgeBaseId Knowledge Base UUID
     * @param afterId         上一页最后一个 UUID
     * @param limit           最大结果数
     * @return Revision 行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_base_id, revision_number,
               parser_profile_id, chunk_profile_id, embedding_profile_id, retrieval_profile_id,
               content_hash, status, failure_code, version, created_at, created_by, updated_at,
               updated_by
        FROM knowledge_revision
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND knowledge_base_id = #{knowledgeBaseId,jdbcType=BINARY}
          AND (#{afterId,jdbcType=BINARY} IS NULL OR id > #{afterId,jdbcType=BINARY})
        ORDER BY id
        LIMIT #{limit}
        """)
    List<RevisionRow> listKnowledgeRevisions(
        @Param("projectId") UUID projectId,
        @Param("knowledgeBaseId") UUID knowledgeBaseId,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * @param projectId  项目 UUID
     * @param revisionId Revision UUID
     * @return 按顺序排列的文档修订 UUID
     */
    @Select("""
        SELECT document_revision_id
        FROM knowledge_revision_document
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND knowledge_revision_id = #{revisionId,jdbcType=BINARY}
        ORDER BY ordinal_value
        """)
    List<UUID> listKnowledgeRevisionDocuments(
        @Param("projectId") UUID projectId, @Param("revisionId") UUID revisionId);

    /**
     * @param projectId       项目 UUID
     * @param id              Revision UUID
     * @param status          新状态
     * @param failureCode     可选失败代码
     * @param expectedVersion 预期乐观锁版本
     * @param updatedAt       更新时间
     * @param updatedBy       更新主体
     * @return 更新行数
     */
    @Update("""
        UPDATE knowledge_revision
        SET status = #{status}, failure_code = #{failureCode}, version = version + 1,
            updated_at = #{updatedAt}, updated_by = #{updatedBy}
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
          AND version = #{expectedVersion}
        """)
    int updateKnowledgeRevisionState(
        @Param("projectId") UUID projectId,
        @Param("id") UUID id,
        @Param("status") String status,
        @Param("failureCode") String failureCode,
        @Param("expectedVersion") long expectedVersion,
        @Param("updatedAt") Instant updatedAt,
        @Param("updatedBy") String updatedBy);

    /**
     * @param row 摄取请求数据库行
     */
    @Insert("""
        INSERT INTO knowledge_ingestion_request
            (id, organization_id, project_id, knowledge_revision_id, idempotency_key, status,
             requested_at, requested_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{knowledgeRevisionId,jdbcType=BINARY},
             #{idempotencyKey}, #{status}, #{requestedAt}, #{requestedBy})
        """)
    void insertIngestionRequest(IngestionRow row);

    /**
     * @param projectId      项目 UUID
     * @param idempotencyKey 幂等键
     * @return 同项目请求行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_revision_id, idempotency_key, status,
               requested_at, requested_by
        FROM knowledge_ingestion_request
        WHERE project_id = #{projectId,jdbcType=BINARY} AND idempotency_key = #{idempotencyKey}
        """)
    Optional<IngestionRow> findIngestionRequestByKey(
        @Param("projectId") UUID projectId,
        @Param("idempotencyKey") String idempotencyKey);

    /**
     * @param projectId 项目 UUID
     * @param id        请求 UUID
     * @return 同项目请求行
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_revision_id, idempotency_key, status,
               requested_at, requested_by
        FROM knowledge_ingestion_request
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<IngestionRow> findIngestionRequestById(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * 仅供已认证 Internal Service 按全局 UUIDv7 加载计划。
     *
     * @param id 请求 UUID
     * @return 请求行及其可信租户范围
     */
    @Select("""
        SELECT id, organization_id, project_id, knowledge_revision_id, idempotency_key, status,
               requested_at, requested_by
        FROM knowledge_ingestion_request
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<IngestionRow> findIngestionRequestInternal(@Param("id") UUID id);
}
