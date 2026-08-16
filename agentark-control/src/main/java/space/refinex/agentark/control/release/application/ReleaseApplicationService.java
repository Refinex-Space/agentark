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
import space.refinex.agentark.control.catalog.domain.CatalogAssetStatus;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Environment;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.ReleaseModels.*;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.SecretRef;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 实现 Agent/Draft 读写、Revision 查询以及 Environment Deployment 指针生命周期。
 *
 * @author refinex
 */
public class ReleaseApplicationService {

    /**
     * Release 持久化端口。
     */
    private final ReleaseRepository repository;

    /**
     * Catalog 稳定 Agent 身份端口。
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
     * Secret 引用检查端口。
     */
    private final SecretRepository secretRepository;

    /**
     * 审计发布器。
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
     * 创建 Release 应用服务。
     *
     * @param repository           Release 端口
     * @param catalogRepository    Catalog 端口
     * @param tenantRepository     租户目录
     * @param authorizationService 授权服务
     * @param secretRepository     Secret 端口
     * @param auditPublisher       审计发布器
     * @param clock                UTC 时钟
     * @param jsonMapper           JSON 映射器
     */
    public ReleaseApplicationService(
        ReleaseRepository repository,
        CatalogRepository catalogRepository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        SecretRepository secretRepository,
        IamAuditPublisher auditPublisher,
        Clock clock,
        JsonMapper jsonMapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository must not be null");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository must not be null");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 在同一事务创建 Agent 稳定身份和首个 Draft。
     *
     * @param principal   已认证主体
     * @param projectId   项目标识
     * @param key         稳定 Key
     * @param name        展示名称
     * @param description 用途说明
     * @param spec        首个 Draft 规范
     * @return 新 Agent
     */
    @Transactional
    public Agent createAgent(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String name,
        String description,
        AgentDraftSpec spec) {
        Project project = authorize(principal, projectId, PermissionRegistry.AGENT_MANAGE);
        Instant now = Instant.now(clock);
        CatalogAsset stored = new CatalogAsset(
            AgentId.generate(), CatalogAssetKind.AGENT, project.organizationId(), projectId,
            key, name, description, "{}", CatalogAssetStatus.ACTIVE, 0, now, now);
        catalogRepository.insertAsset(stored, actor(principal));
        repository.insertDraft(new AgentDraft(
            (AgentId) stored.id(), project.organizationId(), projectId, spec, 0, now), actor(principal));
        audit("agent.create", principal, project, stored.id().asString(), now);
        return agent(stored);
    }

    /**
     * 读取同项目内的 Agent 稳定身份。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param agentId   Agent 标识
     * @return Agent 稳定身份
     */
    @Transactional(readOnly = true)
    public Agent getAgent(
        AgentArkPrincipal principal, ProjectId projectId, AgentId agentId) {
        authorize(principal, projectId, PermissionRegistry.AGENT_READ);
        return agent(requiredAgent(projectId, agentId));
    }

    /**
     * 读取 Agent 当前唯一 Draft。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param agentId   Agent 标识
     * @return 当前 Draft
     */
    @Transactional(readOnly = true)
    public AgentDraft getDraft(
        AgentArkPrincipal principal, ProjectId projectId, AgentId agentId) {
        authorize(principal, projectId, PermissionRegistry.AGENT_READ);
        requiredAgent(projectId, agentId);
        return repository.findDraft(projectId, agentId)
            .orElseThrow(() -> new IamNotFoundException("agent draft is not visible"));
    }

    /**
     * 使用乐观锁更新 Agent Draft。
     *
     * @param principal       主体
     * @param projectId       项目
     * @param agentId         Agent
     * @param spec            新规范
     * @param expectedVersion 预期版本
     * @return 更新后的 Draft
     */
    @Transactional
    public AgentDraft updateDraft(
        AgentArkPrincipal principal,
        ProjectId projectId,
        AgentId agentId,
        AgentDraftSpec spec,
        long expectedVersion) {
        Project project = authorize(principal, projectId, PermissionRegistry.AGENT_MANAGE);
        requiredAgent(projectId, agentId);
        Instant now = Instant.now(clock);
        if (repository.updateDraft(
            projectId, agentId, spec, expectedVersion, now, actor(principal)) != 1) {
            throw new IamConflictException("draft version precondition failed");
        }
        audit("agent.draft.update", principal, project, agentId.asString(), now);
        return repository.findDraft(projectId, agentId)
            .orElseThrow(() -> new IllegalStateException("updated draft is missing"));
    }

    /**
     * 列出 Agent 的不可变 Revision。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param agentId   Agent 标识
     * @return 按 Revision 序号排序的列表
     */
    @Transactional(readOnly = true)
    public List<AgentRevision> listRevisions(
        AgentArkPrincipal principal, ProjectId projectId, AgentId agentId) {
        authorize(principal, projectId, PermissionRegistry.AGENT_READ);
        requiredAgent(projectId, agentId);
        return repository.listRevisions(projectId, agentId);
    }

    /**
     * 读取 Agent 的指定不可变 Revision。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param agentId    Agent 标识
     * @param revisionId Revision 标识
     * @return 不可变 Revision 元数据
     */
    @Transactional(readOnly = true)
    public AgentRevision getRevision(
        AgentArkPrincipal principal,
        ProjectId projectId,
        AgentId agentId,
        RevisionId revisionId) {
        authorize(principal, projectId, PermissionRegistry.AGENT_READ);
        return requiredRevision(projectId, agentId, revisionId).revision();
    }

    /**
     * 创建 Environment 内稳定 Deployment。
     *
     * @param principal     主体
     * @param projectId     项目
     * @param environmentId 环境
     * @param agentId       Agent
     * @param revisionId    初始 Revision
     * @param policy        流量策略
     * @return 新 Deployment
     */
    @Transactional
    public Deployment createDeployment(
        AgentArkPrincipal principal,
        ProjectId projectId,
        EnvironmentId environmentId,
        AgentId agentId,
        RevisionId revisionId,
        TrafficPolicy policy) {
        Project project = authorizeEnvironment(
            principal, projectId, environmentId, PermissionRegistry.DEPLOYMENT_MANAGE);
        requireFull(policy);
        StoredSnapshot snapshot = requiredRevision(projectId, agentId, revisionId);
        verifySecretsForEnvironment(projectId, environmentId, snapshot.canonicalJson());
        Instant now = Instant.now(clock);
        Deployment deployment = new Deployment(
            DeploymentId.generate(), project.organizationId(), projectId, environmentId, agentId,
            revisionId, DeploymentStatus.ENABLED, policy, 0, now, now);
        DeploymentRevision history = new DeploymentRevision(
            EventId.generate(), deployment.id(), DeploymentAction.CREATE,
            Optional.empty(), revisionId, now);
        repository.insertDeployment(
            deployment, history, deploymentEvent(deployment, "deployment.created", now),
            actor(principal));
        audit("deployment.create", principal, project, deployment.id().asString(), now);
        return deployment;
    }

    /**
     * 读取指定 Environment Deployment。
     *
     * @param principal     已认证主体
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param id            Deployment 标识
     * @return Deployment 当前状态
     */
    @Transactional(readOnly = true)
    public Deployment getDeployment(
        AgentArkPrincipal principal,
        ProjectId projectId,
        EnvironmentId environmentId,
        DeploymentId id) {
        authorizeEnvironment(principal, projectId, environmentId, PermissionRegistry.DEPLOYMENT_READ);
        return repository.findDeployment(projectId, environmentId, id)
            .orElseThrow(() -> new IamNotFoundException("deployment is not visible"));
    }

    /**
     * 使用乐观锁全量推进 Deployment Revision 指针。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param environmentId   环境标识
     * @param id              Deployment 标识
     * @param revisionId      目标 Revision 标识
     * @param expectedVersion 预期 Deployment 版本
     * @return Promote 后的 Deployment
     */
    @Transactional
    public Deployment promote(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, RevisionId revisionId, long expectedVersion) {
        return move(principal, projectId, environmentId, id, revisionId,
            expectedVersion, DeploymentAction.PROMOTE);
    }

    /**
     * 使用乐观锁回退 Deployment Revision 指针。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param environmentId   环境标识
     * @param id              Deployment 标识
     * @param revisionId      目标历史 Revision 标识
     * @param expectedVersion 预期 Deployment 版本
     * @return Rollback 后的 Deployment
     */
    @Transactional
    public Deployment rollback(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, RevisionId revisionId, long expectedVersion) {
        return move(principal, projectId, environmentId, id, revisionId,
            expectedVersion, DeploymentAction.ROLLBACK);
    }

    /**
     * 启用 Deployment 的新 Session 接纳状态。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param environmentId   环境标识
     * @param id              Deployment 标识
     * @param expectedVersion 预期 Deployment 版本
     * @return Enable 后的 Deployment
     */
    @Transactional
    public Deployment enable(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, long expectedVersion) {
        return changeStatus(principal, projectId, environmentId, id,
            expectedVersion, DeploymentStatus.ENABLED, DeploymentAction.ENABLE);
    }

    /**
     * 禁用 Deployment 的新 Session 接纳状态。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param environmentId   环境标识
     * @param id              Deployment 标识
     * @param expectedVersion 预期 Deployment 版本
     * @return Disable 后的 Deployment
     */
    @Transactional
    public Deployment disable(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, long expectedVersion) {
        return changeStatus(principal, projectId, environmentId, id,
            expectedVersion, DeploymentStatus.DISABLED, DeploymentAction.DISABLE);
    }

    /**
     * @param principal 主体 @param projectId 项目 @param environmentId 环境 @param id Deployment @param revisionId Revision @param expectedVersion 版本 @param action 动作 @return 新状态
     */
    private Deployment move(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, RevisionId revisionId, long expectedVersion, DeploymentAction action) {
        Project project = authorizeEnvironment(
            principal, projectId, environmentId, PermissionRegistry.DEPLOYMENT_MANAGE);
        Deployment current = requiredDeployment(projectId, environmentId, id);
        StoredSnapshot snapshot = requiredRevision(projectId, current.agentId(), revisionId);
        verifySecretsForEnvironment(projectId, environmentId, snapshot.canonicalJson());
        Instant now = Instant.now(clock);
        Deployment changed = new Deployment(
            current.id(), current.organizationId(), current.projectId(), current.environmentId(),
            current.agentId(), revisionId, current.status(), current.trafficPolicy(),
            current.version() + 1, current.createdAt(), now);
        update(current, changed, expectedVersion, action, principal, now);
        audit("deployment." + action.name().toLowerCase(Locale.ROOT), principal,
            project, id.asString(), now);
        return changed;
    }

    /**
     * @param principal 主体 @param projectId 项目 @param environmentId 环境 @param id Deployment @param expectedVersion 版本 @param status 状态 @param action 动作 @return 新状态
     */
    private Deployment changeStatus(
        AgentArkPrincipal principal, ProjectId projectId, EnvironmentId environmentId,
        DeploymentId id, long expectedVersion, DeploymentStatus status,
        DeploymentAction action) {
        Project project = authorizeEnvironment(
            principal, projectId, environmentId, PermissionRegistry.DEPLOYMENT_MANAGE);
        Deployment current = requiredDeployment(projectId, environmentId, id);
        Instant now = Instant.now(clock);
        Deployment changed = new Deployment(
            current.id(), current.organizationId(), current.projectId(), current.environmentId(),
            current.agentId(), current.desiredRevisionId(), status, current.trafficPolicy(),
            current.version() + 1, current.createdAt(), now);
        update(current, changed, expectedVersion, action, principal, now);
        audit("deployment." + action.name().toLowerCase(Locale.ROOT), principal,
            project, id.asString(), now);
        return changed;
    }

    /**
     * @param current 旧部署 @param changed 新部署 @param expectedVersion 预期版本 @param action 动作 @param principal 主体 @param now 时刻
     */
    private void update(
        Deployment current, Deployment changed, long expectedVersion,
        DeploymentAction action, AgentArkPrincipal principal, Instant now) {
        DeploymentRevision history = new DeploymentRevision(
            EventId.generate(), current.id(), action, Optional.of(current.desiredRevisionId()),
            changed.desiredRevisionId(), now);
        if (repository.updateDeployment(
            changed, expectedVersion, history,
            deploymentEvent(changed, "deployment." + action.name().toLowerCase(Locale.ROOT), now),
            actor(principal)) != 1) {
            throw new IamConflictException("deployment version precondition failed");
        }
    }

    /**
     * @param projectId 项目 @param agentId Agent @param revisionId Revision @return Snapshot
     */
    private StoredSnapshot requiredRevision(
        ProjectId projectId, AgentId agentId, RevisionId revisionId) {
        StoredSnapshot snapshot = repository.findSnapshot(projectId, revisionId)
            .orElseThrow(() -> new IamNotFoundException("agent revision is not visible"));
        if (!snapshot.revision().agentId().equals(agentId)) {
            throw new IamNotFoundException("agent revision is not visible");
        }
        return snapshot;
    }

    /**
     * @param projectId 项目 @param environmentId 环境 @param id Deployment @return 部署
     */
    private Deployment requiredDeployment(
        ProjectId projectId, EnvironmentId environmentId, DeploymentId id) {
        return repository.findDeployment(projectId, environmentId, id)
            .orElseThrow(() -> new IamNotFoundException("deployment is not visible"));
    }

    /**
     * @param projectId 项目 @param agentId Agent @return 活动 Agent
     */
    private CatalogAsset requiredAgent(ProjectId projectId, AgentId agentId) {
        CatalogAsset asset = catalogRepository.findAsset(CatalogAssetKind.AGENT, projectId, agentId)
            .orElseThrow(() -> new IamNotFoundException("agent is not visible"));
        if (asset.status() != CatalogAssetStatus.ACTIVE) {
            throw new IamNotFoundException("agent is not visible");
        }
        return asset;
    }

    /**
     * @param principal 主体 @param projectId 项目 @param permission 权限 @return 项目
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
     * @param principal 主体 @param projectId 项目 @param environmentId 环境 @param permission 权限 @return 项目
     */
    private Project authorizeEnvironment(
        AgentArkPrincipal principal, ProjectId projectId,
        EnvironmentId environmentId, String permission) {
        Project project = tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project is not visible"));
        Environment environment = tenantRepository.findEnvironment(environmentId)
            .filter(value -> value.projectId().equals(projectId)
                && value.organizationId().equals(project.organizationId()))
            .orElseThrow(() -> new IamNotFoundException("environment is not visible"));
        authorizationService.requirePermission(
            principal, project.organizationId(), Optional.of(projectId),
            Optional.of(environment.id()), permission);
        return project;
    }

    /**
     * @param policy 流量策略
     */
    private void requireFull(TrafficPolicy policy) {
        if (policy.type() != TrafficPolicyType.FULL) {
            throw new IamConflictException("CANARY is modeled but not executable in Phase 10");
        }
    }

    /**
     * @param projectId 项目 @param environmentId 目标环境 @param snapshotJson Snapshot JSON
     */
    private void verifySecretsForEnvironment(
        ProjectId projectId, EnvironmentId environmentId, String snapshotJson) {
        for (SecretRef ref : secretRefs(snapshotJson)) {
            if ("environment".equals(ref.value().getAuthority())) {
                String[] segments = ref.value().getPath().substring(1).split("/");
                if (segments.length != 2 || !EnvironmentId.parse(segments[0]).equals(environmentId)) {
                    throw new IamConflictException("Snapshot SecretRef targets another environment");
                }
            }
            if (!secretRepository.existsReference(projectId, ref)) {
                throw new IamConflictException("Snapshot SecretRef is not resolvable for deployment");
            }
        }
    }

    /**
     * @param snapshotJson Snapshot JSON @return 去重 SecretRef
     */
    @SuppressWarnings("unchecked")
    private Set<SecretRef> secretRefs(String snapshotJson) {
        try {
            Map<String, Object> root = jsonMapper.readValue(snapshotJson, Map.class);
            Set<SecretRef> result = new LinkedHashSet<>();
            collectSecretRefs(root, result);
            return Set.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored snapshot JSON is invalid", exception);
        }
    }

    /**
     * @param value 当前 JSON 值 @param result 收集结果
     */
    private void collectSecretRefs(Object value, Set<SecretRef> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                if ("secretRef".equals(String.valueOf(key)) && nested instanceof String text) {
                    result.add(SecretRef.parse(text));
                } else {
                    collectSecretRefs(nested, result);
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(item -> collectSecretRefs(item, result));
        }
    }

    /**
     * @param stored 目录中的 Agent @return 发布领域的 Agent
     */
    private Agent agent(CatalogAsset stored) {
        return new Agent(
            (AgentId) stored.id(), stored.organizationId(), stored.projectId(), stored.key(),
            stored.name(), stored.description(), stored.version(), stored.createdAt());
    }

    /**
     * @param deployment Deployment @param eventType 事件类型 @param now 时刻 @return Outbox
     */
    private OutboxEvent deploymentEvent(
        Deployment deployment, String eventType, Instant now) {
        try {
            return new OutboxEvent(
                EventId.generate(), "deployment", deployment.id().asString(), eventType,
                jsonMapper.writeValueAsString(Map.of(
                    "deploymentId", deployment.id().asString(),
                    "environmentId", deployment.environmentId().asString(),
                    "agentId", deployment.agentId().asString(),
                    "desiredRevisionId", deployment.desiredRevisionId().asString(),
                    "desiredStatus", deployment.status().name(),
                    "version", deployment.version())), now);
        } catch (JacksonException exception) {
            throw new IllegalStateException("deployment outbox serialization failed", exception);
        }
    }

    /**
     * @param action 审计动作 @param principal 主体 @param project 项目 @param id 资源 @param now 时刻
     */
    private void audit(
        String action, AgentArkPrincipal principal, Project project, String id, Instant now) {
        auditPublisher.afterCommit(new IamAuditRecord(
            action, actor(principal), action.startsWith("agent") ? "agent" : "deployment", id,
            Optional.of(project.organizationId()), Optional.of(project.id()), "SUCCEEDED", now));
    }

    /**
     * @param principal 主体 @return 审计引用
     */
    private String actor(AgentArkPrincipal principal) {
        return principal.issuer() + ":" + principal.subject();
    }
}
