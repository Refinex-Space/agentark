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

package space.refinex.agentark.control.release.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import space.refinex.agentark.control.release.adapter.out.persistence.ReleasePersistenceRows.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行显式 Project/Environment Scope 的 Release SQL，不依赖隐式 Tenant 条件补全。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ReleaseMapper {

    /**
     * @param row Draft 行 @param actor 操作主体
     */
    @Insert("""
        INSERT INTO agent_draft
            (agent_id, organization_id, project_id, spec_json, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.agentId,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.specJson}, #{row.version},
             #{row.createdAt}, #{actor}, #{row.updatedAt}, #{actor})
        """)
    void insertDraft(@Param("row") DraftRow row, @Param("actor") String actor);

    /**
     * @param row 组件行
     */
    @Insert("""
        INSERT INTO agent_draft_component
            (agent_id, organization_id, project_id, component_type, component_order,
             owner_id, version_id, binding_json, created_at)
        VALUES
            (#{agentId,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{componentType}, #{componentOrder},
             #{ownerId,jdbcType=BINARY}, #{versionId,jdbcType=BINARY}, #{bindingJson}, #{createdAt})
        """)
    void insertComponent(ComponentRow row);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID
     */
    @Delete("DELETE FROM agent_draft_component WHERE project_id = #{projectId,jdbcType=BINARY} AND agent_id = #{agentId,jdbcType=BINARY}")
    void deleteComponents(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID @return Draft
     */
    @Select("""
        SELECT agent_id, organization_id, project_id, spec_json, version, created_at, updated_at
        FROM agent_draft
        WHERE project_id = #{projectId,jdbcType=BINARY} AND agent_id = #{agentId,jdbcType=BINARY}
        """)
    Optional<DraftRow> findDraft(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID @return 锁定 Draft
     */
    @Select("""
        SELECT agent_id, organization_id, project_id, spec_json, version, created_at, updated_at
        FROM agent_draft
        WHERE project_id = #{projectId,jdbcType=BINARY} AND agent_id = #{agentId,jdbcType=BINARY}
        FOR UPDATE
        """)
    Optional<DraftRow> lockDraft(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId);

    /**
     * 使用乐观锁更新 Draft 权威 JSON。
     *
     * @param projectId       项目 UUID
     * @param agentId         Agent UUID
     * @param specJson        新 Draft JSON
     * @param expectedVersion 预期乐观锁版本
     * @param now             更新时间
     * @param actor           操作主体
     * @return 实际更新行数
     */
    @Update("""
        UPDATE agent_draft
        SET spec_json = #{specJson}, version = version + 1,
            updated_at = #{now}, updated_by = #{actor}
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND agent_id = #{agentId,jdbcType=BINARY}
          AND version = #{expectedVersion}
        """)
    int updateDraft(
        @Param("projectId") UUID projectId,
        @Param("agentId") UUID agentId,
        @Param("specJson") String specJson,
        @Param("expectedVersion") long expectedVersion,
        @Param("now") Instant now,
        @Param("actor") String actor);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID @return 下一 Revision 序号
     */
    @Select("""
        SELECT COALESCE(MAX(revision_number), 0) + 1
        FROM agent_revision
        WHERE project_id = #{projectId,jdbcType=BINARY} AND agent_id = #{agentId,jdbcType=BINARY}
        """)
    long nextRevisionNumber(@Param("projectId") UUID projectId, @Param("agentId") UUID agentId);

    /**
     * @param id 报告 UUID @param projectId 项目 UUID @param agentId Agent UUID @param draftVersion Draft 版本 @param status 状态 @param findingsJson 问题 JSON @param now 时刻 @param actor 主体
     */
    @Insert("""
        INSERT INTO validation_report
            (id, organization_id, project_id, agent_id, draft_version, status,
             findings_json, created_at, created_by)
        SELECT #{id,jdbcType=BINARY}, organization_id, project_id, id, #{draftVersion},
               #{status}, #{findingsJson}, #{now}, #{actor}
        FROM agent
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{agentId,jdbcType=BINARY}
        """)
    void insertValidationReport(
        @Param("id") UUID id,
        @Param("projectId") UUID projectId,
        @Param("agentId") UUID agentId,
        @Param("draftVersion") long draftVersion,
        @Param("status") String status,
        @Param("findingsJson") String findingsJson,
        @Param("now") Instant now,
        @Param("actor") String actor);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID @param key 幂等键 @return 操作
     */
    @Select("""
        SELECT id, project_id, agent_id, idempotency_key, draft_version, status,
               revision_id, created_at
        FROM publish_operation
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND agent_id = #{agentId,jdbcType=BINARY}
          AND idempotency_key = #{key}
        """)
    Optional<OperationRow> findPublishOperation(
        @Param("projectId") UUID projectId,
        @Param("agentId") UUID agentId,
        @Param("key") String key);

    /**
     * @param row Snapshot/Revision 行 @param actor 主体
     */
    @Insert("""
        INSERT INTO agent_revision
            (id, organization_id, project_id, agent_id, snapshot_id, revision_number,
             schema_version, runtime_provider, content_hash, required_capabilities_json,
             status, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.agentId,jdbcType=BINARY},
             #{row.snapshotId,jdbcType=BINARY}, #{row.revisionNumber}, #{row.schemaVersion},
             #{row.runtimeProvider}, #{row.contentHash,jdbcType=BINARY},
             #{row.requiredCapabilitiesJson}, 'PUBLISHED', #{row.createdAt}, #{actor})
        """)
    void insertRevision(@Param("row") SnapshotRow row, @Param("actor") String actor);

    /**
     * @param row Snapshot 行 @param actor 主体
     */
    @Insert("""
        INSERT INTO agent_revision_snapshot
            (id, organization_id, project_id, revision_id, schema_version,
             runtime_provider, content_hash, snapshot_json, created_at, created_by)
        VALUES
            (#{row.snapshotId,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.id,jdbcType=BINARY},
             #{row.schemaVersion}, #{row.runtimeProvider}, #{row.contentHash,jdbcType=BINARY},
             #{row.snapshotJson}, #{row.createdAt}, #{actor})
        """)
    void insertSnapshot(@Param("row") SnapshotRow row, @Param("actor") String actor);

    /**
     * @param row 操作行 @param organizationId 组织 UUID @param actor 主体
     */
    @Insert("""
        INSERT INTO publish_operation
            (id, organization_id, project_id, agent_id, idempotency_key, draft_version,
             status, revision_id, created_at, created_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.agentId,jdbcType=BINARY},
             #{row.idempotencyKey}, #{row.draftVersion}, #{row.status},
             #{row.revisionId,jdbcType=BINARY}, #{row.createdAt}, #{actor})
        """)
    void insertPublishOperation(
        @Param("row") OperationRow row,
        @Param("organizationId") UUID organizationId,
        @Param("actor") String actor);

    /**
     * @param id 事件 UUID @param aggregateType 聚合类型 @param aggregateId 聚合标识 @param eventType 事件类型 @param payloadJson 载荷 @param now 时刻
     */
    @Insert("""
        INSERT INTO control_outbox
            (id, aggregate_type, aggregate_id, event_type, payload_json, status,
             attempts, available_at, created_at, published_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{aggregateType}, #{aggregateId}, #{eventType},
             #{payloadJson}, 'PENDING', 0, #{now}, #{now}, NULL)
        """)
    void insertOutbox(
        @Param("id") UUID id,
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") String aggregateId,
        @Param("eventType") String eventType,
        @Param("payloadJson") String payloadJson,
        @Param("now") Instant now);

    /**
     * @param projectId 项目 UUID @param revisionId Revision UUID @return Snapshot
     */
    @Select("""
        SELECT r.id, r.organization_id, r.project_id, r.agent_id, r.snapshot_id,
               r.revision_number, r.schema_version, r.runtime_provider, r.content_hash,
               r.required_capabilities_json, r.created_at, s.snapshot_json
        FROM agent_revision r
        JOIN agent_revision_snapshot s ON s.revision_id = r.id
        WHERE r.project_id = #{projectId,jdbcType=BINARY} AND r.id = #{revisionId,jdbcType=BINARY}
        """)
    Optional<SnapshotRow> findSnapshot(
        @Param("projectId") UUID projectId, @Param("revisionId") UUID revisionId);

    /**
     * @param revisionId Revision UUID @return 内部运行时使用的 Snapshot
     */
    @Select("""
        SELECT r.id, r.organization_id, r.project_id, r.agent_id, r.snapshot_id,
               r.revision_number, r.schema_version, r.runtime_provider, r.content_hash,
               r.required_capabilities_json, r.created_at, s.snapshot_json
        FROM agent_revision r
        JOIN agent_revision_snapshot s ON s.revision_id = r.id
        WHERE r.id = #{revisionId,jdbcType=BINARY}
        """)
    Optional<SnapshotRow> findSnapshotInternal(@Param("revisionId") UUID revisionId);

    /**
     * @param projectId 项目 UUID @param agentId Agent UUID @return Revision 列表
     */
    @Select("""
        SELECT r.id, r.organization_id, r.project_id, r.agent_id, r.snapshot_id,
               r.revision_number, r.schema_version, r.runtime_provider, r.content_hash,
               r.required_capabilities_json, r.created_at, s.snapshot_json
        FROM agent_revision r
        JOIN agent_revision_snapshot s ON s.revision_id = r.id
        WHERE r.project_id = #{projectId,jdbcType=BINARY} AND r.agent_id = #{agentId,jdbcType=BINARY}
        ORDER BY r.revision_number, r.id
        """)
    List<SnapshotRow> listRevisions(
        @Param("projectId") UUID projectId, @Param("agentId") UUID agentId);

    /**
     * @param row Deployment 行 @param actor 主体
     */
    @Insert("""
        INSERT INTO deployment
            (id, organization_id, project_id, environment_id, agent_id, desired_revision_id,
             desired_status, traffic_policy_type, canary_percent, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.environmentId,jdbcType=BINARY},
             #{row.agentId,jdbcType=BINARY}, #{row.desiredRevisionId,jdbcType=BINARY},
             #{row.desiredStatus}, #{row.trafficPolicyType}, #{row.canaryPercent}, #{row.version},
             #{row.createdAt}, #{actor}, #{row.updatedAt}, #{actor})
        """)
    void insertDeployment(@Param("row") DeploymentRow row, @Param("actor") String actor);

    /**
     * @param id 历史 UUID @param organizationId 组织 UUID @param projectId 项目 UUID @param deploymentId Deployment UUID @param action 动作 @param fromRevisionId 原 Revision @param toRevisionId 新 Revision @param now 时刻 @param actor 主体
     */
    @Insert("""
        INSERT INTO deployment_revision
            (id, organization_id, project_id, deployment_id, action,
             from_revision_id, to_revision_id, created_at, created_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{deploymentId,jdbcType=BINARY}, #{action},
             #{fromRevisionId,jdbcType=BINARY}, #{toRevisionId,jdbcType=BINARY}, #{now}, #{actor})
        """)
    void insertDeploymentRevision(
        @Param("id") UUID id,
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("deploymentId") UUID deploymentId,
        @Param("action") String action,
        @Param("fromRevisionId") UUID fromRevisionId,
        @Param("toRevisionId") UUID toRevisionId,
        @Param("now") Instant now,
        @Param("actor") String actor);

    /**
     * @param projectId 项目 UUID @param environmentId 环境 UUID @param id Deployment UUID @return Deployment
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_id, agent_id,
               desired_revision_id, desired_status, traffic_policy_type, canary_percent,
               version, created_at, updated_at
        FROM deployment
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND environment_id = #{environmentId,jdbcType=BINARY}
          AND id = #{id,jdbcType=BINARY}
        """)
    Optional<DeploymentRow> findDeployment(
        @Param("projectId") UUID projectId,
        @Param("environmentId") UUID environmentId,
        @Param("id") UUID id);

    /**
     * 按 UUIDv7 顺序列出 Environment Deployment，查询始终带 Project 与 Environment Scope。
     *
     * @param projectId     项目 UUID
     * @param environmentId 环境 UUID
     * @param afterId       排除的最后 UUIDv7
     * @param limit         读取上限
     * @return Deployment 行列表
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_id, agent_id,
               desired_revision_id, desired_status, traffic_policy_type, canary_percent,
               version, created_at, updated_at
        FROM deployment
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND environment_id = #{environmentId,jdbcType=BINARY}
          AND id > #{afterId,jdbcType=BINARY}
        ORDER BY id
        LIMIT #{limit}
        """)
    List<DeploymentRow> listDeployments(
        @Param("projectId") UUID projectId,
        @Param("environmentId") UUID environmentId,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * @param id Deployment UUID @return 内部运行时使用的 Deployment
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_id, agent_id,
               desired_revision_id, desired_status, traffic_policy_type, canary_percent,
               version, created_at, updated_at
        FROM deployment
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<DeploymentRow> findDeploymentInternal(@Param("id") UUID id);

    /**
     * @param row 新 Deployment 行 @param expectedVersion 预期版本 @param actor 主体 @return 更新行数
     */
    @Update("""
        UPDATE deployment
        SET desired_revision_id = #{row.desiredRevisionId,jdbcType=BINARY},
            desired_status = #{row.desiredStatus},
            traffic_policy_type = #{row.trafficPolicyType},
            canary_percent = #{row.canaryPercent},
            version = version + 1,
            updated_at = #{row.updatedAt},
            updated_by = #{actor}
        WHERE project_id = #{row.projectId,jdbcType=BINARY}
          AND environment_id = #{row.environmentId,jdbcType=BINARY}
          AND id = #{row.id,jdbcType=BINARY}
          AND version = #{expectedVersion}
        """)
    int updateDeployment(
        @Param("row") DeploymentRow row,
        @Param("expectedVersion") long expectedVersion,
        @Param("actor") String actor);
}
