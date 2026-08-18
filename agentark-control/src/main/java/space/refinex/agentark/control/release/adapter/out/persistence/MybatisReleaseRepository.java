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

import space.refinex.agentark.control.release.adapter.out.persistence.ReleasePersistenceRows.*;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.*;
import space.refinex.agentark.control.release.domain.ReleaseModels.*;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

/**
 * 使用显式 Scope MyBatis Mapper 实现 Release Repository，并维护 Draft 组件查询投影。
 *
 * @author refinex
 */
public final class MybatisReleaseRepository implements ReleaseRepository {

    /**
     * Release 持久化映射器。
     */
    private final ReleaseMapper mapper;

    /**
     * 应用统一 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 MyBatis Release Repository。
     *
     * @param mapper     Release Mapper
     * @param jsonMapper JSON 映射器
     */
    public MybatisReleaseRepository(ReleaseMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param draft 首次 Draft @param actor 操作主体
     */
    @Override
    public void insertDraft(AgentDraft draft, String actor) {
        mapper.insertDraft(new DraftRow(
            draft.agentId().value(), draft.organizationId().value(), draft.projectId().value(),
            write(draft.spec()), draft.version(), draft.updatedAt(), draft.updatedAt()), actor);
        insertComponents(draft, draft.updatedAt());
    }

    /**
     * @param projectId 项目 @param agentId Agent @return Draft
     */
    @Override
    public Optional<AgentDraft> findDraft(ProjectId projectId, AgentId agentId) {
        return mapper.findDraft(projectId.value(), agentId.value()).map(this::draft);
    }

    /**
     * 使用乐观锁更新 Draft，并在成功后同事务重建组件查询投影。
     *
     * @param projectId       项目标识
     * @param agentId         Agent 标识
     * @param spec            新 Draft 规范
     * @param expectedVersion 预期乐观锁版本
     * @param now             更新时间
     * @param actor           操作主体
     * @return 实际更新行数
     */
    @Override
    public int updateDraft(ProjectId projectId, AgentId agentId, AgentDraftSpec spec, long expectedVersion, Instant now, String actor) {
        int updated = mapper.updateDraft(projectId.value(), agentId.value(), write(spec), expectedVersion, now, actor);
        if (updated == 1) {
            AgentDraft draft = mapper.findDraft(projectId.value(), agentId.value()).map(this::draft).orElseThrow();
            mapper.deleteComponents(projectId.value(), agentId.value());
            insertComponents(draft, now);
        }
        return updated;
    }

    /**
     * @param projectId 项目 @param agentId Agent @return 锁定 Draft
     */
    @Override
    public Optional<AgentDraft> lockDraft(ProjectId projectId, AgentId agentId) {
        return mapper.lockDraft(projectId.value(), agentId.value()).map(this::draft);
    }

    /**
     * @param projectId 项目 @param agentId Agent @return 下一 Revision 序号
     */
    @Override
    public long nextRevisionNumber(ProjectId projectId, AgentId agentId) {
        return mapper.nextRevisionNumber(projectId.value(), agentId.value());
    }

    /**
     * @param report 校验报告 @param projectId 项目 @param actor 操作主体
     */
    @Override
    public void insertValidationReport(ValidationReport report, ProjectId projectId, String actor) {
        mapper.insertValidationReport(
            report.id().value(), projectId.value(), report.agentId().value(),
            report.draftVersion(), report.valid() ? "VALID" : "INVALID",
            write(report.findings()), report.createdAt(), actor);
    }

    /**
     * @param projectId 项目 @param agentId Agent @param key 幂等键 @return 操作
     */
    @Override
    public Optional<PublishOperation> findPublishOperation(ProjectId projectId, AgentId agentId, String key) {
        return mapper.findPublishOperation(projectId.value(), agentId.value(), key)
            .map(this::operation);
    }

    /**
     * @param snapshot Snapshot @param operation 操作 @param report 报告 @param outbox 事件 @param actor 主体
     */
    @Override
    public void insertPublished(StoredSnapshot snapshot, PublishOperation operation, ValidationReport report, OutboxEvent outbox, String actor) {
        SnapshotRow row = snapshotRow(snapshot);
        mapper.insertRevision(row, actor);
        mapper.insertSnapshot(row, actor);
        mapper.insertPublishOperation(new OperationRow(
                operation.id().value(), operation.projectId().value(), operation.agentId().value(),
                operation.idempotencyKey(), operation.draftVersion(), operation.status().name(),
                operation.revisionId().map(RevisionId::value).orElse(null), operation.createdAt()),
            snapshot.revision().organizationId().value(), actor);
        insertValidationReport(report, operation.projectId(), actor);
        insertOutbox(outbox);
    }

    /**
     * @param projectId 项目 @param revisionId Revision @return Snapshot
     */
    @Override
    public Optional<StoredSnapshot> findSnapshot(ProjectId projectId, RevisionId revisionId) {
        return mapper.findSnapshot(projectId.value(), revisionId.value()).map(this::snapshot);
    }

    /**
     * @param revisionId Revision 标识 @return 内部运行时使用的 Snapshot
     */
    @Override
    public Optional<StoredSnapshot> findSnapshotInternal(RevisionId revisionId) {
        return mapper.findSnapshotInternal(revisionId.value()).map(this::snapshot);
    }

    /**
     * @param projectId 项目 @param agentId Agent @return Revision 列表
     */
    @Override
    public List<AgentRevision> listRevisions(ProjectId projectId, AgentId agentId) {
        return mapper.listRevisions(projectId.value(), agentId.value()).stream()
            .map(this::revision).toList();
    }

    /**
     * 按 UUIDv7 顺序读取 Environment 内 Deployment。
     *
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param afterId       排除的最后 Deployment
     * @param limit         读取上限
     * @return Deployment 列表
     */
    @Override
    public List<Deployment> listDeployments(ProjectId projectId, EnvironmentId environmentId, DeploymentId afterId, int limit) {
        return mapper.listDeployments(projectId.value(), environmentId.value(), afterId.value(), limit)
            .stream().map(this::deployment).toList();
    }

    /**
     * @param deployment Deployment @param history 历史 @param outbox 事件 @param actor 主体
     */
    @Override
    public void insertDeployment(Deployment deployment, DeploymentRevision history, OutboxEvent outbox, String actor) {
        mapper.insertDeployment(deploymentRow(deployment), actor);
        insertHistory(deployment, history, actor);
        insertOutbox(outbox);
    }

    /**
     * @param projectId 项目 @param environmentId 环境 @param id Deployment @return Deployment
     */
    @Override
    public Optional<Deployment> findDeployment(ProjectId projectId, EnvironmentId environmentId, DeploymentId id) {
        return mapper.findDeployment(projectId.value(), environmentId.value(), id.value())
            .map(this::deployment);
    }

    /**
     * @param id Deployment 标识 @return 内部运行时使用的 Deployment
     */
    @Override
    public Optional<Deployment> findDeploymentInternal(DeploymentId id) {
        return mapper.findDeploymentInternal(id.value()).map(this::deployment);
    }

    /**
     * 使用乐观锁更新 Deployment，并在成功后追加历史与 Outbox。
     *
     * @param deployment      新 Deployment 状态
     * @param expectedVersion 预期乐观锁版本
     * @param history         只追加历史事件
     * @param outbox          同事务 Outbox 事件
     * @param actor           操作主体
     * @return 实际更新行数
     */
    @Override
    public int updateDeployment(Deployment deployment, long expectedVersion, DeploymentRevision history, OutboxEvent outbox, String actor) {
        int updated = mapper.updateDeployment(deploymentRow(deployment), expectedVersion, actor);
        if (updated == 1) {
            insertHistory(deployment, history, actor);
            insertOutbox(outbox);
        }
        return updated;
    }

    /**
     * @param draft Draft @param now 投影时刻
     */
    private void insertComponents(AgentDraft draft, Instant now) {
        AgentDraftSpec spec = draft.spec();
        insertComponent(draft, "MODEL", 0, spec.model().providerId().value(), spec.model().profileId().value(), Map.of(), now);
        for (int index = 0; index < spec.prompts().size(); index++) {
            PromptBinding value = spec.prompts().get(index);
            insertComponent(draft, "PROMPT", index, value.promptId().value(), value.versionId().value(), Map.of("role", value.role().name()), now);
        }
        for (int index = 0; index < spec.mcpServers().size(); index++) {
            McpBinding value = spec.mcpServers().get(index);
            insertComponent(draft, "MCP", index, value.serverId().value(), value.versionId().value(), Map.of("allowedTools", value.allowedTools()), now);
        }
        for (int index = 0; index < spec.skills().size(); index++) {
            SkillBinding value = spec.skills().get(index);
            insertComponent(draft, "SKILL", index, value.skillId().value(), value.versionId().value(), Map.of(), now);
        }
        for (int index = 0; index < spec.knowledge().size(); index++) {
            KnowledgeBinding value = spec.knowledge().get(index);
            insertComponent(draft, "KNOWLEDGE", index, value.knowledgeBaseId().value(), value.revisionId().value(), Map.of(), now);
        }
        ProfileBindings profiles = spec.profiles();
        insertComponent(draft, "MEMORY", 0, profiles.memoryId().value(), profiles.memoryVersionId().value(), Map.of(), now);
        insertComponent(draft, "WORKSPACE", 0, profiles.workspaceId().value(), profiles.workspaceVersionId().value(), Map.of(), now);
        insertComponent(draft, "SANDBOX", 0, profiles.sandboxId().value(), profiles.sandboxVersionId().value(), Map.of(), now);
        insertComponent(draft, "PERMISSION", 0, spec.permissionPolicy().policyId().value(), spec.permissionPolicy().versionId().value(), Map.of(), now);
    }

    /**
     * @param draft Draft @param type 类型 @param order 顺序 @param ownerId Owner @param versionId Version @param binding 绑定 @param now 时刻
     */
    private void insertComponent(AgentDraft draft, String type, int order, UUID ownerId, UUID versionId, Map<String, Object> binding, Instant now) {
        mapper.insertComponent(new ComponentRow(draft.agentId().value(), draft.organizationId().value(), draft.projectId().value(), type, order, ownerId, versionId, write(binding), now));
    }

    /**
     * @param deployment Deployment @param history 历史 @param actor 主体
     */
    private void insertHistory(Deployment deployment, DeploymentRevision history, String actor) {
        mapper.insertDeploymentRevision(
            history.id().value(), deployment.organizationId().value(), deployment.projectId().value(),
            deployment.id().value(), history.action().name(),
            history.fromRevisionId().map(RevisionId::value).orElse(null),
            history.toRevisionId().value(), history.createdAt(), actor);
    }

    /**
     * @param event Outbox 事件
     */
    private void insertOutbox(OutboxEvent event) {
        mapper.insertOutbox(event.id().value(), event.aggregateType(), event.aggregateId(), event.eventType(), event.payloadJson(), event.createdAt());
    }

    /**
     * @param row Draft 行 @return Draft
     */
    private AgentDraft draft(DraftRow row) {
        return new AgentDraft(
            new AgentId(row.agentId()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), read(row.specJson(), AgentDraftSpec.class),
            row.version(), row.updatedAt());
    }

    /**
     * @param row 操作行 @return 发布操作
     */
    private PublishOperation operation(OperationRow row) {
        return new PublishOperation(new JobId(row.id()), new ProjectId(row.projectId()), new AgentId(row.agentId()),
            row.idempotencyKey(), row.draftVersion(), PublishStatus.valueOf(row.status()),
            Optional.ofNullable(row.revisionId()).map(RevisionId::new), row.createdAt());
    }

    /**
     * @param stored Snapshot @return 数据库行
     */
    private SnapshotRow snapshotRow(StoredSnapshot stored) {
        AgentRevision revision = stored.revision();
        return new SnapshotRow(
            revision.id().value(), revision.organizationId().value(), revision.projectId().value(),
            revision.agentId().value(), revision.snapshotId().value(), revision.revisionNumber(),
            revision.schemaVersion(), revision.runtimeProvider(),
            HexFormat.of().parseHex(revision.contentHash().hex()),
            write(revision.requiredCapabilities()), revision.createdAt(), stored.canonicalJson());
    }

    /**
     * @param row Snapshot 行 @return Stored Snapshot
     */
    private StoredSnapshot snapshot(SnapshotRow row) {
        return new StoredSnapshot(revision(row), row.snapshotJson());
    }

    /**
     * @param row Snapshot 行 @return Revision
     */
    private AgentRevision revision(SnapshotRow row) {
        return new AgentRevision(
            new RevisionId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new AgentId(row.agentId()),
            new SnapshotId(row.snapshotId()), row.revisionNumber(), row.schemaVersion(),
            row.runtimeProvider(),
            new Checksum("sha256:" + HexFormat.of().formatHex(row.contentHash())),
            readStringList(row.requiredCapabilitiesJson()), row.createdAt());
    }

    /**
     * @param deployment Deployment @return 数据库行
     */
    private DeploymentRow deploymentRow(Deployment deployment) {
        return new DeploymentRow(
            deployment.id().value(), deployment.organizationId().value(),
            deployment.projectId().value(), deployment.environmentId().value(),
            deployment.agentId().value(), deployment.desiredRevisionId().value(),
            deployment.status().name(), deployment.trafficPolicy().type().name(),
            deployment.trafficPolicy().canaryPercent(), deployment.version(),
            deployment.createdAt(), deployment.updatedAt());
    }

    /**
     * @param row 数据库行 @return Deployment
     */
    private Deployment deployment(DeploymentRow row) {
        return new Deployment(
            new DeploymentId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new EnvironmentId(row.environmentId()),
            new AgentId(row.agentId()), new RevisionId(row.desiredRevisionId()),
            DeploymentStatus.valueOf(row.desiredStatus()),
            new TrafficPolicy(TrafficPolicyType.valueOf(row.trafficPolicyType()), row.canaryPercent()),
            row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * @param value 值 @return JSON
     */
    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("release JSON serialization failed", exception);
        }
    }

    /**
     * @param value JSON @param type 类型 @param <T> 目标类型 @return 对象
     */
    private <T> T read(String value, Class<T> type) {
        try {
            return jsonMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored release JSON is invalid", exception);
        }
    }

    /**
     * @param value JSON @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private List<String> readStringList(String value) {
        return List.copyOf(read(value, List.class));
    }
}
