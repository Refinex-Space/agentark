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

package space.refinex.agentark.control.release.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.release.adapter.in.web.ReleaseApiModels.*;
import space.refinex.agentark.control.release.application.AgentPublisher;
import space.refinex.agentark.control.release.application.ReleaseApplicationService;
import space.refinex.agentark.control.release.application.RuntimeInternalContractService;
import space.refinex.agentark.control.release.application.RuntimeInternalContractService.DeploymentDescriptor;
import space.refinex.agentark.control.release.domain.ReleaseModels.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.*;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.*;

/**
 * 暴露 Agent Draft/Publish/Deployment Public API 和受服务身份保护的 Runtime Internal API。
 *
 * @author refinex
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class ReleaseController {

    /**
     * Release 应用服务。
     */
    private final ReleaseApplicationService service;

    /**
     * Agent 发布器。
     */
    private final AgentPublisher publisher;

    /**
     * Runtime Internal Contract 服务。
     */
    private final RuntimeInternalContractService internalService;

    /**
     * Public Snapshot JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Release API 适配器。
     *
     * @param service         Release 应用服务
     * @param publisher       Agent 发布器
     * @param internalService Runtime Internal Contract 服务
     * @param objectMapper    Snapshot JSON 解析器
     */
    public ReleaseController(
        ReleaseApplicationService service,
        AgentPublisher publisher,
        RuntimeInternalContractService internalService,
        ObjectMapper objectMapper) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.internalService = Objects.requireNonNull(internalService, "internalService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 按不透明游标列出项目内 Agent。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7
     * @param cursor         可选不透明游标
     * @param limit          页大小，范围 1 到 100
     * @return Agent 游标页
     */
    @GetMapping("/api/v1/projects/{projectId}/agents")
    public AgentPageResponse listAgents(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<Agent> page = service.listAgents(
            principal(authentication), ProjectId.parse(projectId), cursor, limit);
        return new AgentPageResponse(
            page.items(), page.nextCursor().orElse(null), page.hasMore());
    }

    /**
     * 创建 Agent 稳定身份及其初始 Draft。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param request        Agent 与初始 Draft 请求
     * @return 带资源定位的已创建 Agent
     */
    @PostMapping("/api/v1/projects/{projectId}/agents")
    public ResponseEntity<Agent> createAgent(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateAgentRequest request) {
        Agent created = service.createAgent(
            principal(authentication), ProjectId.parse(projectId), request.key(), request.name(),
            request.description(), request.draft());
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/agents/" + created.id().asString()))
            .body(created);
    }

    /**
     * 读取同项目内可见的 Agent 稳定身份。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @return Agent 稳定身份
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}")
    public Agent getAgent(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId) {
        return service.getAgent(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId));
    }

    /**
     * 读取 Agent 当前唯一可编辑 Draft。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @return 当前 Draft
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}/draft")
    public AgentDraft getDraft(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId) {
        return service.getDraft(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId));
    }

    /**
     * 使用乐观锁替换 Agent Draft。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @param request        新 Draft 与预期版本
     * @return 更新后的 Draft
     */
    @PutMapping("/api/v1/projects/{projectId}/agents/{agentId}/draft")
    public AgentDraft updateDraft(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId,
        @Valid @RequestBody UpdateDraftRequest request) {
        return service.updateDraft(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId),
            request.draft(), request.expectedVersion());
    }

    /**
     * 校验当前 Draft 的全部版本引用和发布约束，但不创建 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @return 持久化校验报告
     */
    @PostMapping("/api/v1/projects/{projectId}/agents/{agentId}/draft/validate")
    public ValidationReport validateDraft(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId) {
        return publisher.validate(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId));
    }

    /**
     * 幂等发布当前 Draft 并返回不可变 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @param request        幂等键与预期 Draft 版本
     * @return 带资源定位的不可变 Revision
     */
    @PostMapping("/api/v1/projects/{projectId}/agents/{agentId}/publish")
    public ResponseEntity<AgentRevision> publish(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId,
        @Valid @RequestBody PublishRequest request) {
        AgentRevision revision = publisher.publish(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId),
            request.idempotencyKey(), request.expectedDraftVersion());
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/agents/" + agentId + "/revisions/"
                + revision.id().asString())).body(revision);
    }

    /**
     * 列出 Agent 的不可变 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @return 按 Revision 序号排序的列表
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}/revisions")
    public List<AgentRevision> revisions(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId) {
        return service.listRevisions(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId));
    }

    /**
     * 读取 Agent 的指定不可变 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param agentId        Agent UUIDv7 字符串
     * @param revisionId     Revision UUIDv7 字符串
     * @return 指定 Revision 元数据
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}/revisions/{revisionId}")
    public AgentRevision revision(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId,
        @PathVariable String revisionId) {
        return service.getRevision(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId),
            RevisionId.parse(revisionId));
    }

    /**
     * 返回已授权 Revision 的不可变 Snapshot，并用内容摘要提供 ETag。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7
     * @param agentId        Agent UUIDv7
     * @param revisionId     Revision UUIDv7
     * @return 不含明文 Secret 的 Snapshot 视图
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}/revisions/{revisionId}/snapshot")
    public ResponseEntity<SnapshotResponse> snapshot(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId,
        @PathVariable String revisionId) {
        StoredSnapshot stored = service.getSnapshot(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId),
            RevisionId.parse(revisionId));
        String etag = "\"" + stored.revision().contentHash().hex() + "\"";
        try {
            return ResponseEntity.ok().eTag(etag).body(new SnapshotResponse(
                revisionId, stored.revision().contentHash().toString(),
                objectMapper.readTree(stored.canonicalJson())));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("stored snapshot JSON is invalid", exception);
        }
    }

    /**
     * 比较两个不可变 Agent Revision 的安全顶层区段。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7
     * @param agentId        Agent UUIDv7
     * @param baseRevisionId 基准 Revision UUIDv7
     * @param targetRevisionId 目标 Revision UUIDv7
     * @return 不含资产正文的 Revision 差异摘要
     */
    @GetMapping("/api/v1/projects/{projectId}/agents/{agentId}/revisions:diff")
    public RevisionComparisonResponse compareRevisions(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String agentId,
        @RequestParam String baseRevisionId,
        @RequestParam String targetRevisionId) {
        RevisionComparison comparison = service.compareRevisions(
            principal(authentication), ProjectId.parse(projectId), AgentId.parse(agentId),
            RevisionId.parse(baseRevisionId), RevisionId.parse(targetRevisionId));
        return new RevisionComparisonResponse(
            comparison.baseRevisionId().asString(), comparison.targetRevisionId().asString(),
            comparison.baseContentHash().toString(), comparison.targetContentHash().toString(),
            comparison.changedSections());
    }

    /**
     * 按 UUIDv7 游标列出 Environment 内 Deployment。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7
     * @param environmentId  所属环境 UUIDv7
     * @param cursor         可选不透明游标
     * @param limit          页大小，范围 1 到 100
     * @return Deployment 游标页
     */
    @GetMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments")
    public DeploymentPageResponse listDeployments(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<Deployment> page = service.listDeployments(
            principal(authentication), ProjectId.parse(projectId),
            EnvironmentId.parse(environmentId), cursor, limit);
        return new DeploymentPageResponse(
            page.items(), page.nextCursor().orElse(null), page.hasMore());
    }

    /**
     * 在 Environment 内创建稳定 Deployment。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param request        Agent、Revision 和流量策略
     * @return 带资源定位的新 Deployment
     */
    @PostMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments")
    public ResponseEntity<Deployment> createDeployment(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @Valid @RequestBody CreateDeploymentRequest request) {
        Deployment deployment = service.createDeployment(
            principal(authentication), ProjectId.parse(projectId),
            EnvironmentId.parse(environmentId), AgentId.parse(request.agentId()),
            RevisionId.parse(request.revisionId()), request.policy());
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/environments/" + environmentId
                + "/deployments/" + deployment.id().asString())).body(deployment);
    }

    /**
     * 读取指定 Environment Deployment。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param deploymentId   Deployment UUIDv7 字符串
     * @return Deployment 当前指针和期望状态
     */
    @GetMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}")
    public Deployment getDeployment(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @PathVariable String deploymentId) {
        return service.getDeployment(
            principal(authentication), ProjectId.parse(projectId),
            EnvironmentId.parse(environmentId), DeploymentId.parse(deploymentId));
    }

    /**
     * 使用乐观锁把 Deployment 全量推进到目标 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param deploymentId   Deployment UUIDv7 字符串
     * @param request        目标 Revision 和预期 Deployment 版本
     * @return Promote 后的 Deployment
     */
    @PostMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/promote")
    public Deployment promote(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @PathVariable String deploymentId,
        @Valid @RequestBody MoveDeploymentRequest request) {
        return service.promote(
            principal(authentication), ProjectId.parse(projectId), EnvironmentId.parse(environmentId),
            DeploymentId.parse(deploymentId), RevisionId.parse(request.revisionId()),
            request.expectedVersion());
    }

    /**
     * 使用乐观锁把 Deployment 指针回退到历史 Revision。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param deploymentId   Deployment UUIDv7 字符串
     * @param request        目标历史 Revision 和预期 Deployment 版本
     * @return Rollback 后的 Deployment
     */
    @PostMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/rollback")
    public Deployment rollback(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @PathVariable String deploymentId,
        @Valid @RequestBody MoveDeploymentRequest request) {
        return service.rollback(
            principal(authentication), ProjectId.parse(projectId), EnvironmentId.parse(environmentId),
            DeploymentId.parse(deploymentId), RevisionId.parse(request.revisionId()),
            request.expectedVersion());
    }

    /**
     * 启用 Deployment 的新 Session 接纳状态。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param deploymentId   Deployment UUIDv7 字符串
     * @param request        预期 Deployment 版本
     * @return Enable 后的 Deployment
     */
    @PostMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/enable")
    public Deployment enable(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @PathVariable String deploymentId,
        @Valid @RequestBody ChangeDeploymentStatusRequest request) {
        return service.enable(
            principal(authentication), ProjectId.parse(projectId), EnvironmentId.parse(environmentId),
            DeploymentId.parse(deploymentId), request.expectedVersion());
    }

    /**
     * 禁用 Deployment 的新 Session 接纳状态。
     *
     * @param authentication Spring Security 已认证上下文
     * @param projectId      所属项目 UUIDv7 字符串
     * @param environmentId  所属环境 UUIDv7 字符串
     * @param deploymentId   Deployment UUIDv7 字符串
     * @param request        预期 Deployment 版本
     * @return Disable 后的 Deployment
     */
    @PostMapping("/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/disable")
    public Deployment disable(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @PathVariable String deploymentId,
        @Valid @RequestBody ChangeDeploymentStatusRequest request) {
        return service.disable(
            principal(authentication), ProjectId.parse(projectId), EnvironmentId.parse(environmentId),
            DeploymentId.parse(deploymentId), request.expectedVersion());
    }

    /**
     * 按 Runtime 能力声明读取不可变 Canonical Snapshot，并支持条件缓存。
     *
     * @param authentication  Spring Security 已认证服务上下文
     * @param revisionId      Revision UUIDv7 字符串
     * @param runtimeProvider Runtime Provider 稳定名称
     * @param schemaVersions  逗号分隔的 Snapshot Schema 版本
     * @param capabilities    逗号分隔的 Runtime 能力名称
     * @param ifNoneMatch     可选的 HTTP ETag 条件
     * @return ETag 命中时 304，否则返回 Canonical Snapshot JSON
     */
    @GetMapping(value = "/internal/v1/agent-revisions/{revisionId}/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> internalSnapshot(
        Authentication authentication,
        @PathVariable String revisionId,
        @RequestHeader("X-AgentArk-Runtime-Provider") String runtimeProvider,
        @RequestHeader("X-AgentArk-Snapshot-Schema-Versions") String schemaVersions,
        @RequestHeader(value = "X-AgentArk-Runtime-Capabilities", defaultValue = "") String capabilities,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        StoredSnapshot snapshot = internalService.snapshot(
            principal(authentication), RevisionId.parse(revisionId), runtimeProvider,
            integers(schemaVersions), strings(capabilities));
        String etag = "\"" + snapshot.revision().contentHash().hex() + "\"";
        if (ifNoneMatch != null && Arrays.stream(ifNoneMatch.split(","))
            .map(String::trim).anyMatch(etag::equals)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).contentType(MediaType.APPLICATION_JSON)
            .body(snapshot.canonicalJson());
    }

    /**
     * 为 Runtime 返回不暴露 Control Entity 的 Deployment 描述。
     *
     * @param authentication Spring Security 已认证服务上下文
     * @param deploymentId   Deployment UUIDv7 字符串
     * @return 语言中立 Deployment Descriptor
     */
    @GetMapping("/internal/v1/deployments/{deploymentId}")
    public DeploymentDescriptor internalDeployment(
        Authentication authentication, @PathVariable String deploymentId) {
        return internalService.deployment(
            principal(authentication), DeploymentId.parse(deploymentId));
    }

    /**
     * 从 Spring Security 上下文提取 AgentArk 主体。
     *
     * @param authentication 安全上下文
     * @return AgentArk 主体
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new IamAccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }

    /**
     * 解析逗号分隔的正整数 Header。
     *
     * @param value 逗号分隔整数
     * @return 去重后的整数集合
     */
    private Set<Integer> integers(String value) {
        try {
            Set<Integer> result = new LinkedHashSet<>();
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    result.add(Integer.parseInt(item.trim()));
                }
            }
            return Set.copyOf(result);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("snapshot schema versions header is invalid", exception);
        }
    }

    /**
     * 解析逗号分隔的稳定名称 Header。
     *
     * @param value 逗号分隔稳定名称
     * @return 去空白且去重的字符串集合
     */
    private Set<String> strings(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return Set.copyOf(result);
    }
}
