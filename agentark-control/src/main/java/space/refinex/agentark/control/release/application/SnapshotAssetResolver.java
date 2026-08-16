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

import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.*;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.IamNotFoundException;
import space.refinex.agentark.control.release.application.port.KnowledgeSnapshotLookup;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.*;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SchemaVersion;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.kernel.snapshot.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 在发布边界解析并校验所有可编辑目录引用，生成不再依赖目录的完整 Kernel Snapshot。
 *
 * @author refinex
 */
public final class SnapshotAssetResolver {

    /**
     * 资产目录端口。
     */
    private final CatalogRepository catalogRepository;

    /**
     * SecretRef 可见性端口。
     */
    private final SecretRepository secretRepository;

    /**
     * Knowledge READY Revision 中立查询端口。
     */
    private final KnowledgeSnapshotLookup knowledgeLookup;

    /**
     * JSON 解析器。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param catalogRepository 资产目录端口
     * @param secretRepository  SecretRef 可见性端口
     * @param knowledgeLookup   Knowledge READY 查询端口
     * @param jsonMapper        JSON 映射器
     */
    public SnapshotAssetResolver(
        CatalogRepository catalogRepository,
        SecretRepository secretRepository,
        KnowledgeSnapshotLookup knowledgeLookup,
        JsonMapper jsonMapper) {
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository must not be null");
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository must not be null");
        this.knowledgeLookup = Objects.requireNonNull(knowledgeLookup, "knowledgeLookup must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param agentId        Agent 标识
     * @param agentName      Agent 稳定名称
     * @param draft          Draft 规范
     * @param revisionId     新 Revision 标识
     * @param snapshotId     新 Snapshot 标识
     * @param revisionNumber Revision 序号
     * @param now            发布时间
     * @return 已通过 Kernel 与资产约束校验的临时 Snapshot
     */
    public AgentRevisionSnapshot resolve(
        OrganizationId organizationId,
        ProjectId projectId,
        AgentId agentId,
        String agentName,
        AgentDraftSpec draft,
        RevisionId revisionId,
        SnapshotId snapshotId,
        long revisionNumber,
        Instant now) {
        PermissionSpec permission = permission(projectId, draft.permissionPolicy());
        ModelSpec model = model(projectId, draft.model(), draft.requiredCapabilities());
        List<PromptSpec> prompts = draft.prompts().stream()
            .map(binding -> prompt(projectId, binding)).toList();
        List<McpSpec> mcp = mcp(projectId, draft.mcpServers(), permission);
        List<SkillSpec> skills = draft.skills().stream()
            .map(binding -> skill(projectId, binding)).toList();
        List<KnowledgeSpec> knowledge = draft.knowledge().stream()
            .map(binding -> knowledge(projectId, binding)).toList();
        Map<String, Object> memory = profile(projectId, CatalogAssetKind.MEMORY_PROFILE,
            draft.profiles().memoryId(), draft.profiles().memoryVersionId());
        Map<String, Object> workspace = profile(projectId, CatalogAssetKind.WORKSPACE_PROFILE,
            draft.profiles().workspaceId(), draft.profiles().workspaceVersionId());
        Map<String, Object> sandbox = profile(projectId, CatalogAssetKind.SANDBOX_PROFILE,
            draft.profiles().sandboxId(), draft.profiles().sandboxVersionId());

        return new AgentRevisionSnapshot(
            SchemaVersion.initial(), organizationId, projectId, snapshotId, agentId, revisionId,
            revisionNumber, now, Checksum.sha256("provisional"),
            new RuntimeProviderId(draft.runtimeProvider()),
            new AgentSpec(agentName, AgentEntrypoint.HARNESS, draft.requiredCapabilities()),
            model, prompts, mcp, skills, knowledge,
            new MemorySpec(draft.profiles().memoryVersionId(), memory),
            new WorkspaceSpec(draft.profiles().workspaceVersionId(), workspace),
            new SandboxSpec(draft.profiles().sandboxVersionId(), sandbox), permission,
            new RuntimeLimits(Duration.ofSeconds(draft.limits().turnTimeoutSeconds()),
                draft.limits().maxToolCalls(), draft.limits().maxSubAgents()));
    }

    /**
     * @param projectId 项目标识 @param binding 模型绑定 @param requiredCapabilities 必需能力 @return 模型规范
     */
    private ModelSpec model(
        ProjectId projectId, ModelBinding binding, List<String> requiredCapabilities) {
        CatalogAsset provider = asset(CatalogAssetKind.MODEL_PROVIDER, projectId, binding.providerId());
        CatalogVersion profile = version(
            CatalogAssetKind.MODEL_PROVIDER, projectId, binding.providerId(), binding.profileId());
        Map<String, Object> metadata = json(provider.metadataJson());
        Map<String, Object> payload = json(profile.payloadJson());
        Set<String> capabilities = strings(payload.get("capabilities"), "model capabilities");
        Map<String, String> requiredModel = Map.of(
            "tool-calling", "TOOL", "vision", "VISION",
            "structured-output", "STRUCTURED_OUTPUT", "streaming", "STREAMING");
        for (String requirement : requiredCapabilities) {
            String modelCapability = requiredModel.get(requirement);
            if (modelCapability != null && !capabilities.contains(modelCapability)) {
                throw new IamConflictException("model profile does not satisfy required capability");
            }
        }
        SecretRef credential = SecretRef.parse(string(payload, "credentialSecretRef"));
        requireSecret(projectId, credential);
        Map<String, Object> parameters = map(payload.get("parameters"), "model parameters");
        String providerName = string(metadata, "providerType")
            .toLowerCase(Locale.ROOT).replace('_', '-');
        return new ModelSpec(
            providerName, string(payload, "modelName"),
            new ModelParameters(decimal(parameters, "temperature"), integer(parameters, "maxTokens")),
            new CredentialSpec(credential, SecretResolutionPolicy.LATEST_ENABLED));
    }

    /**
     * @param projectId 项目标识 @param binding Prompt 绑定 @return Prompt 规范
     */
    private PromptSpec prompt(ProjectId projectId, PromptBinding binding) {
        CatalogVersion version = version(
            CatalogAssetKind.PROMPT, projectId, binding.promptId(), binding.versionId());
        String content = string(json(version.payloadJson()), "template");
        return new PromptSpec(binding.role(), binding.versionId(), Checksum.sha256(content), content);
    }

    /**
     * @param projectId 项目标识 @param bindings MCP 绑定 @param permission 权限规范 @return MCP 规范
     */
    private List<McpSpec> mcp(
        ProjectId projectId, List<McpBinding> bindings, PermissionSpec permission) {
        Set<String> globallyBoundTools = new HashSet<>();
        List<McpSpec> result = new ArrayList<>();
        for (McpBinding binding : bindings) {
            CatalogVersion version = version(
                CatalogAssetKind.MCP_SERVER, projectId, binding.serverId(), binding.versionId());
            Map<String, Object> payload = json(version.payloadJson());
            Map<String, McpToolDescriptorSnapshot> descriptors = new HashMap<>();
            for (McpToolDescriptorSnapshot descriptor
                : catalogRepository.listToolDescriptors(projectId, binding.versionId())) {
                descriptors.put(descriptor.toolName(), descriptor);
            }
            for (String tool : binding.allowedTools()) {
                McpToolDescriptorSnapshot descriptor = descriptors.get(tool);
                if (descriptor == null) {
                    throw new IamConflictException("MCP allowed tool is not present in descriptor snapshot");
                }
                if (!globallyBoundTools.add(tool)) {
                    throw new IamConflictException("MCP tool name conflicts across server bindings");
                }
                if ("CRITICAL".equals(descriptor.riskLevel())) {
                    throw new IamConflictException("critical MCP tool cannot be published in Snapshot v1");
                }
                if ("HIGH".equals(descriptor.riskLevel()) && permission.rules().stream()
                    .noneMatch(rule -> rule.resource().equals("tool:" + tool)
                        && rule.decision() == PermissionDecision.ASK)) {
                    throw new IamConflictException("high-risk MCP tool requires explicit ASK policy");
                }
            }
            if (payload.get("tlsSecretRef") != null) {
                throw new IamConflictException("MCP TLS Secret requires Snapshot schema v2");
            }
            Optional<CredentialSpec> credential = Optional.empty();
            if (payload.get("authSecretRef") != null) {
                SecretRef ref = SecretRef.parse(string(payload, "authSecretRef"));
                requireSecret(projectId, ref);
                credential = Optional.of(new CredentialSpec(
                    ref, SecretResolutionPolicy.LATEST_ENABLED));
            }
            String transportValue = string(payload, "transport");
            McpTransport transport;
            URI endpoint;
            if ("STREAMABLE_HTTP".equals(transportValue)) {
                transport = McpTransport.STREAMABLE_HTTP;
                endpoint = URI.create(string(payload, "endpointUri"));
            } else if ("STDIO".equals(transportValue)) {
                transport = McpTransport.STDIO;
                endpoint = URI.create("stdio://" + string(payload, "commandName"));
            } else {
                throw new IamConflictException("MCP SSE is not supported by Snapshot schema v1");
            }
            result.add(new McpSpec(
                binding.versionId(), transport, endpoint, credential, binding.allowedTools()));
        }
        return List.copyOf(result);
    }

    /**
     * @param projectId 项目标识 @param binding Skill 绑定 @return Skill 规范
     */
    private SkillSpec skill(ProjectId projectId, SkillBinding binding) {
        CatalogVersion version = version(
            CatalogAssetKind.SKILL, projectId, binding.skillId(), binding.versionId());
        Map<String, Object> artifact = map(json(version.payloadJson()).get("artifact"), "artifact");
        return new SkillSpec(binding.versionId(), ObjectRef.of(
            string(artifact, "uri"), new Checksum(string(artifact, "checksum")),
            longValue(artifact, "size"), string(artifact, "mediaType")));
    }

    /**
     * @param projectId 项目标识 @param binding Knowledge 绑定 @return Knowledge 规范
     */
    private KnowledgeSpec knowledge(ProjectId projectId, KnowledgeBinding binding) {
        KnowledgeSnapshotLookup.ResolvedKnowledge resolved = knowledgeLookup.findReady(
                projectId, binding.knowledgeBaseId(), binding.revisionId())
            .orElseThrow(() -> new IamConflictException(
                "knowledge revision is not visible or READY"));
        return new KnowledgeSpec(resolved.revisionId(), new RetrievalSpec(
            resolved.topK(), resolved.scoreThreshold(), resolved.reranker()));
    }

    /**
     * @param projectId 项目标识 @param binding 权限策略绑定 @return 权限规范
     */
    private PermissionSpec permission(ProjectId projectId, PermissionBinding binding) {
        CatalogVersion version = version(
            CatalogAssetKind.PERMISSION_POLICY, projectId, binding.policyId(), binding.versionId());
        Map<String, Object> payload = json(version.payloadJson());
        PermissionDecision defaultDecision = PermissionDecision.valueOf(
            string(payload, "defaultDecision"));
        List<PermissionRuleSpec> rules = new ArrayList<>();
        Object rawRules = payload.get("rules");
        if (!(rawRules instanceof List<?> values)) {
            throw new IamConflictException("permission rules must be a list");
        }
        for (Object value : values) {
            Map<String, Object> rule = map(value, "permission rule");
            rules.add(new PermissionRuleSpec(
                string(rule, "resource"), PermissionDecision.valueOf(string(rule, "decision"))));
        }
        return new PermissionSpec(defaultDecision, rules);
    }

    /**
     * @param projectId 项目标识 @param kind Profile 类型 @param ownerId 稳定身份 @param versionId 版本
     */
    private Map<String, Object> profile(
        ProjectId projectId, CatalogAssetKind kind, StrongId ownerId, StrongId versionId) {
        return json(version(kind, projectId, ownerId, versionId).payloadJson());
    }

    /**
     * @param kind 类型 @param projectId 项目标识 @param id 稳定身份 @return 活动资产
     */
    private CatalogAsset asset(CatalogAssetKind kind, ProjectId projectId, StrongId id) {
        CatalogAsset asset = catalogRepository.findAsset(kind, projectId, id)
            .orElseThrow(() -> new IamNotFoundException("catalog asset is not visible"));
        if (asset.status() != CatalogAssetStatus.ACTIVE) {
            throw new IamConflictException("catalog asset is archived");
        }
        return asset;
    }

    /**
     * @param kind 类型 @param projectId 项目标识 @param ownerId 稳定身份 @param id 版本 @return Published 版本
     */
    private CatalogVersion version(
        CatalogAssetKind kind, ProjectId projectId, StrongId ownerId, StrongId id) {
        asset(kind, projectId, ownerId);
        CatalogVersion version = catalogRepository.findVersion(kind, projectId, ownerId, id)
            .orElseThrow(() -> new IamNotFoundException("catalog version is not visible"));
        if (version.status() != CatalogVersionStatus.PUBLISHED) {
            throw new IamConflictException("catalog version must be PUBLISHED");
        }
        return version;
    }

    /**
     * @param projectId 项目标识 @param ref SecretRef
     */
    private void requireSecret(ProjectId projectId, SecretRef ref) {
        if (!secretRepository.existsReference(projectId, ref)) {
            throw new IamNotFoundException("secret reference is not visible");
        }
    }

    /**
     * @param json JSON 文本 @return 顶层对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String json) {
        try {
            return jsonMapper.readValue(json, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored catalog JSON is invalid", exception);
        }
    }

    /**
     * @param value 候选对象 @param name 字段名 @return Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IamConflictException(name + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    /**
     * @param map 对象 @param key 键 @return 非空字符串
     */
    private String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IamConflictException(key + " must be a non-blank string");
        }
        return text;
    }

    /**
     * @param value 候选列表 @param name 字段名 @return 字符串集合
     */
    private Set<String> strings(Object value, String name) {
        if (!(value instanceof List<?> list)
            || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IamConflictException(name + " must be a string list");
        }
        Set<String> result = new LinkedHashSet<>();
        list.forEach(item -> result.add((String) item));
        return Set.copyOf(result);
    }

    /**
     * @param map 对象 @param key 键 @return Decimal
     */
    private BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IamConflictException(key + " must be a decimal");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new IamConflictException(key + " must be a decimal");
        }
    }

    /**
     * @param map 对象 @param key 键 @return int
     */
    private int integer(Map<String, Object> map, String key) {
        long value = longValue(map, key);
        if (value > Integer.MAX_VALUE) {
            throw new IamConflictException(key + " exceeds integer range");
        }
        return (int) value;
    }

    /**
     * @param map 对象 @param key 键 @return long
     */
    private long longValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            throw new IamConflictException(key + " must be a number");
        }
        return number.longValue();
    }
}
