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

package space.refinex.agentark.control.release.application;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.CanonicalSnapshotSerializer.SerializedSnapshot;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.snapshot.AgentRevisionSnapshot;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 以本地事务把已授权 Draft 发布为不可变 Revision、完整 Snapshot、操作记录和 Outbox。
 *
 * @author refinex
 */
public class AgentPublisher {

    /**
     * 允许进入发布差异摘要的非秘密 Snapshot 顶层区段。
     */
    private static final List<String> DIFF_SECTIONS = List.of(
        "runtimeProvider", "agent", "model", "prompts", "mcpServers", "skills",
        "knowledge", "memory", "workspace", "sandbox", "permissions", "limits");

    /**
     * Release 持久化端口。
     */
    private final ReleaseRepository repository;

    /**
     * 资产解析器。
     */
    private final SnapshotAssetResolver assetResolver;

    /**
     * Canonical Snapshot 序列化器。
     */
    private final CanonicalSnapshotSerializer serializer;

    /**
     * AI 资产目录端口。
     */
    private final CatalogRepository catalogRepository;

    /**
     * 租户目录端口。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * IAM 授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 事务提交后审计发布器。
     */
    private final IamAuditPublisher auditPublisher;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param repository           Release 持久化端口
     * @param assetResolver        资产解析器
     * @param serializer           Canonical Snapshot 序列化器
     * @param catalogRepository    Catalog 端口
     * @param tenantRepository     租户目录
     * @param authorizationService IAM 授权服务
     * @param auditPublisher       审计发布器
     * @param clock                UTC 时钟
     * @param jsonMapper           JSON 映射器
     */
    public AgentPublisher(
        ReleaseRepository repository,
        SnapshotAssetResolver assetResolver,
        CanonicalSnapshotSerializer serializer,
        CatalogRepository catalogRepository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        IamAuditPublisher auditPublisher,
        Clock clock,
        JsonMapper jsonMapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository must not be null");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 校验当前 Draft 并持久化不含资产内容的报告，不创建 Revision。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param agentId   Agent 标识
     * @return 校验报告
     */
    @Transactional
    public ValidationReport validate(
        AgentArkPrincipal principal, ProjectId projectId, AgentId agentId) {
        Project project = authorize(principal, projectId, PermissionRegistry.AGENT_READ);
        AgentDraft draft = repository.findDraft(projectId, agentId)
            .orElseThrow(() -> new IamNotFoundException("agent draft is not visible"));
        Instant now = Instant.now(clock);
        ValidationReport report;
        try {
            resolve(project, agentId, draft, RevisionId.generate(), SnapshotId.generate(), 1, now);
            report = new ValidationReport(
                EventId.generate(), agentId, draft.version(), true, List.of(), now);
        } catch (IllegalArgumentException | IamNotFoundException | IamConflictException exception) {
            report = new ValidationReport(
                EventId.generate(), agentId, draft.version(), false,
                List.of(new ValidationFinding(
                    "draft", "draft.unpublishable", ValidationSeverity.ERROR,
                    safeMessage(exception))), now);
        }
        repository.insertValidationReport(report, projectId, actor(principal));
        return report;
    }

    /**
     * 发布同一幂等键时返回首次成功 Revision；不同 Draft Version 复用相同键会被拒绝。
     *
     * @param principal            已认证主体
     * @param projectId            项目标识
     * @param agentId              Agent 标识
     * @param idempotencyKey       调用方稳定幂等键
     * @param expectedDraftVersion 调用方读取的 Draft 版本
     * @return 发布成功的不可变 Revision
     */
    @Transactional
    public AgentRevision publish(
        AgentArkPrincipal principal,
        ProjectId projectId,
        AgentId agentId,
        String idempotencyKey,
        long expectedDraftVersion) {
        Project project = authorize(principal, projectId, PermissionRegistry.AGENT_PUBLISH);
        AgentDraft draft = repository.lockDraft(projectId, agentId)
            .orElseThrow(() -> new IamNotFoundException("agent draft is not visible"));
        Optional<PublishOperation> existing = repository.findPublishOperation(
            projectId, agentId, idempotencyKey);
        if (existing.isPresent()) {
            PublishOperation operation = existing.orElseThrow();
            if (operation.draftVersion() != expectedDraftVersion) {
                throw new IamConflictException("idempotency key is bound to another draft version");
            }
            return repository.findSnapshot(projectId, operation.revisionId().orElseThrow())
                .orElseThrow(() -> new IllegalStateException("published snapshot is missing"))
                .revision();
        }
        if (draft.version() != expectedDraftVersion) {
            throw new IamConflictException("draft version precondition failed");
        }

        long revisionNumber = repository.nextRevisionNumber(projectId, agentId);
        RevisionId revisionId = RevisionId.generate();
        SnapshotId snapshotId = SnapshotId.generate();
        Instant now = Instant.now(clock);
        AgentRevisionSnapshot provisional = resolve(
            project, agentId, draft, revisionId, snapshotId, revisionNumber, now);
        SerializedSnapshot serialized = serializer.serialize(provisional);
        AgentRevision revision = new AgentRevision(
            revisionId, project.organizationId(), projectId, agentId, snapshotId,
            revisionNumber, 1, draft.spec().runtimeProvider(),
            serialized.snapshot().contentHash(), draft.spec().requiredCapabilities(), now);
        StoredSnapshot stored = new StoredSnapshot(revision, serialized.canonicalJson());
        RevisionDiffSummary diffSummary = diffSummary(projectId, agentId, stored);
        PublishOperation operation = new PublishOperation(
            JobId.generate(), projectId, agentId, idempotencyKey, draft.version(),
            PublishStatus.SUCCEEDED, Optional.of(revisionId), now);
        ValidationReport report = new ValidationReport(
            EventId.generate(), agentId, draft.version(), true, List.of(), now);
        OutboxEvent outbox = new OutboxEvent(
            EventId.generate(), "agent_revision", revisionId.asString(),
            "agent.revision.published",
            outboxPayload(revision, draft.version(), diffSummary), now);
        repository.insertPublished(stored, operation, report, outbox, actor(principal));
        auditPublisher.afterCommit(new IamAuditRecord(
            "agent.publish", actor(principal), "agent_revision", revisionId.asString(),
            Optional.of(project.organizationId()), Optional.of(projectId), "SUCCEEDED", now));
        return revision;
    }

    /**
     * @param project 项目 @param agentId Agent 标识 @param draft Draft @param revisionId Revision @param snapshotId Snapshot @param number 序号 @param now 时刻 @return Snapshot
     */
    private AgentRevisionSnapshot resolve(
        Project project,
        AgentId agentId,
        AgentDraft draft,
        RevisionId revisionId,
        SnapshotId snapshotId,
        long number,
        Instant now) {
        CatalogAsset agent = catalogRepository.findAsset(
                CatalogAssetKind.AGENT, project.id(), agentId)
            .orElseThrow(() -> new IamNotFoundException("agent is not visible"));
        return assetResolver.resolve(
            project.organizationId(), project.id(), agentId, agent.key(), draft.spec(),
            revisionId, snapshotId, number, now);
    }

    /**
     * @param principal 主体 @param projectId 项目标识 @param permission 权限 @return 已授权项目
     */
    private Project authorize(
        AgentArkPrincipal principal, ProjectId projectId, String permission) {
        Project project = tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project is not visible"));
        authorizationService.requirePermission(
            principal, project.organizationId(), Optional.of(projectId), Optional.empty(), permission);
        return project;
    }

    /**
     * @param principal 已认证主体 @return 非秘密稳定审计引用
     */
    private String actor(AgentArkPrincipal principal) {
        return principal.issuer() + ":" + principal.subject();
    }

    /**
     * @param exception 校验异常 @return 不暴露内容的短错误说明
     */
    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Draft 校验失败" : message;
    }

    /**
     * 对比最近一次发布和当前 Snapshot，仅返回发生变化的非秘密顶层区段。
     *
     * @param projectId 项目标识
     * @param agentId   Agent 标识
     * @param current   当前待写入 Snapshot
     * @return 不含资产正文的差异摘要
     */
    private RevisionDiffSummary diffSummary(
        ProjectId projectId, AgentId agentId, StoredSnapshot current) {
        Optional<StoredSnapshot> previous = repository.listRevisions(projectId, agentId).stream()
            .max(java.util.Comparator.comparingLong(AgentRevision::revisionNumber))
            .flatMap(revision -> repository.findSnapshot(projectId, revision.id()));
        if (previous.isEmpty()) {
            return new RevisionDiffSummary(Optional.empty(), DIFF_SECTIONS);
        }
        Map<String, Object> previousFields = snapshotFields(previous.orElseThrow().canonicalJson());
        Map<String, Object> currentFields = snapshotFields(current.canonicalJson());
        List<String> changed = DIFF_SECTIONS.stream()
            .filter(section -> !Objects.equals(previousFields.get(section), currentFields.get(section)))
            .toList();
        return new RevisionDiffSummary(
            Optional.of(previous.orElseThrow().revision().id()), changed);
    }

    /**
     * 读取持久化前已由契约校验的 Canonical Snapshot 顶层字段。
     *
     * @param canonicalJson Canonical Snapshot JSON
     * @return 保持字段值结构的映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotFields(String canonicalJson) {
        try {
            return new LinkedHashMap<>(jsonMapper.readValue(canonicalJson, Map.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("canonical snapshot diff serialization failed", exception);
        }
    }

    /**
     * 创建不含资产正文和秘密值的发布 Outbox 载荷。
     *
     * @param revision     Revision
     * @param draftVersion Draft 版本
     * @param diffSummary  非秘密差异摘要
     * @return Outbox JSON
     */
    private String outboxPayload(
        AgentRevision revision, long draftVersion, RevisionDiffSummary diffSummary) {
        try {
            return jsonMapper.writeValueAsString(Map.of(
                "agentId", revision.agentId().asString(),
                "revisionId", revision.id().asString(),
                "snapshotId", revision.snapshotId().asString(),
                "draftVersion", draftVersion,
                "contentHash", revision.contentHash().toString(),
                "diffSummary", Map.of(
                    "previousRevisionId", diffSummary.previousRevisionId()
                        .map(RevisionId::asString).orElse(""),
                    "changedSections", diffSummary.changedSections())));
        } catch (JacksonException exception) {
            throw new IllegalStateException("release outbox serialization failed", exception);
        }
    }
}
