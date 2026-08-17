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

package space.refinex.agentark.runtime.provider.agentscope.compiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import space.refinex.agentark.foundation.observability.SpanConvention;
import space.refinex.agentark.runtime.provider.agentscope.RuntimeProviderDescriptor;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;
import space.refinex.agentark.runtime.provider.agentscope.knowledge.KnowledgeBinding;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpBinding;
import space.refinex.agentark.runtime.provider.agentscope.memory.MemoryBinding;
import space.refinex.agentark.runtime.provider.agentscope.model.ModelBinding;
import space.refinex.agentark.runtime.provider.agentscope.permission.PermissionBinding;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptBinding;
import space.refinex.agentark.runtime.provider.agentscope.sandbox.SandboxBinding;
import space.refinex.agentark.runtime.provider.agentscope.skill.SkillBinding;
import space.refinex.agentark.runtime.provider.agentscope.workspace.WorkspaceBinding;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/**
 * 仅使用 Runtime Internal Contract 返回的 Canonical Snapshot 创建无 Secret 编译计划。
 *
 * @author refinex
 */
public final class AgentScopeSnapshotCompiler {

    /**
     * 保持 Canonical JSON 字段顺序的解析类型。
     */
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_TYPE =
        new TypeReference<>() {
        };

    /**
     * Provider 版本与能力声明。
     */
    private final RuntimeProviderDescriptor providerDescriptor;

    /**
     * 用于重算 Canonical Hash 和解析字段的 Jackson 2 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 仅缓存无敏感编译计划的 Single Flight 缓存。
     */
    private final SnapshotCompilationCache cache;

    /** Snapshot 编译 Telemetry。 */
    private final AgentArkTelemetry telemetry;

    /**
     * @param providerDescriptor Provider 能力声明
     * @param objectMapper       Jackson 2 映射器
     * @param cache              无敏感编译计划缓存
     */
    public AgentScopeSnapshotCompiler(
        RuntimeProviderDescriptor providerDescriptor,
        ObjectMapper objectMapper,
        SnapshotCompilationCache cache) {
        this(providerDescriptor, objectMapper, cache, AgentArkTelemetry.noop());
    }

    /**
     * 创建带真实 Telemetry 的 Snapshot Compiler。
     *
     * @param providerDescriptor Provider 能力声明
     * @param objectMapper       Jackson 2 映射器
     * @param cache              无敏感编译计划缓存
     * @param telemetry          编译 Telemetry
     */
    public AgentScopeSnapshotCompiler(
        RuntimeProviderDescriptor providerDescriptor,
        ObjectMapper objectMapper,
        SnapshotCompilationCache cache,
        AgentArkTelemetry telemetry) {
        this.providerDescriptor = Objects.requireNonNull(
            providerDescriptor, "providerDescriptor must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /**
     * 验证 Snapshot 契约、Provider、Hash 和能力，生成可重用编译计划。
     *
     * @param snapshot Runtime 已加载的不可变 Snapshot
     * @return 无 Secret 和 Session 状态的编译计划
     */
    public AgentScopeCompilationPlan compile(SnapshotDescriptor snapshot) {
        return telemetry.inSpan(
            SpanConvention.RUNTIME, "agent.compile",
            Map.of("operation", "compile", "runtime.provider", snapshot.runtimeProvider()),
            () -> compileTracked(snapshot));
    }

    /**
     * 在 {@code runtime.agent.compile} Span 内验证并编译 Snapshot。
     *
     * @param snapshot 固定 Snapshot
     * @return 编译计划
     */
    private AgentScopeCompilationPlan compileTracked(SnapshotDescriptor snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        verifyEnvelope(snapshot);
        SnapshotCompilationCache.CacheKey key = new SnapshotCompilationCache.CacheKey(
            providerDescriptor.providerId(), snapshot.schemaVersion(),
            snapshot.contentHash().toString(), providerDescriptor.compilerVersion());
        try {
            return cache.getOrCompile(key, () -> compileUncached(snapshot, key));
        } catch (AgentScopeProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_INVALID,
                "snapshot cannot be compiled into an AgentScope plan", exception);
        }
    }

    /**
     * 在查询缓存前校验不可被 Cache Key 隐藏的 Envelope 字段。
     *
     * @param snapshot Snapshot Envelope
     */
    private void verifyEnvelope(SnapshotDescriptor snapshot) {
        if (!providerDescriptor.providerId().equals(snapshot.runtimeProvider())) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_PROVIDER_MISMATCH,
                "snapshot runtimeProvider is not handled by this provider");
        }
        if (!providerDescriptor.supportedSchemas().contains(snapshot.schemaVersion())) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_SCHEMA_UNSUPPORTED,
                "snapshot schemaVersion is not supported");
        }
    }

    /**
     * 执行一次完整 Snapshot 编译。
     *
     * @param snapshot Snapshot Envelope
     * @param key      缓存键
     * @return 编译计划
     */
    private AgentScopeCompilationPlan compileUncached(
        SnapshotDescriptor snapshot, SnapshotCompilationCache.CacheKey key) {
        LinkedHashMap<String, Object> root = read(snapshot.canonicalJson());
        verifyCanonicalFields(snapshot, root);
        rejectSensitiveValues(root, "snapshot");

        Map<String, Object> agent = object(root, "agent");
        Set<String> requiredCapabilities = Set.copyOf(strings(
            agent, "requiredCapabilities"));
        if (!providerDescriptor.capabilities().containsAll(requiredCapabilities)) {
            Set<String> missing = new HashSet<>(requiredCapabilities);
            missing.removeAll(providerDescriptor.capabilities());
            throw new AgentScopeProviderException(
                ProviderErrorCode.CAPABILITY_UNSUPPORTED,
                "snapshot requires unsupported capabilities: " + missing);
        }

        List<McpBinding> mcpServers = mcpBindings(array(root, "mcpServers"));
        return new AgentScopeCompilationPlan(
            key,
            OrganizationId.parse(text(root, "organizationId")),
            ProjectId.parse(text(root, "projectId")),
            AgentId.parse(text(root, "agentId")),
            text(agent, "name"),
            requiredCapabilities,
            model(object(root, "model")),
            promptBindings(array(root, "prompts")),
            mcpServers,
            skillBindings(array(root, "skills")),
            knowledgeBindings(array(root, "knowledge")),
            memory(object(root, "memory")),
            workspace(object(root, "workspace")),
            sandbox(object(root, "sandbox")),
            permission(object(root, "permissions")),
            Duration.ofSeconds(longValue(object(root, "limits"), "turnTimeoutSeconds")),
            integer(object(root, "limits"), "maxToolCalls"),
            integer(object(root, "limits"), "maxSubAgents"));
    }

    /**
     * 比对 Snapshot Envelope、Canonical JSON 和排除 contentHash 后重算的 Hash。
     *
     * @param snapshot Snapshot Envelope
     * @param root     Canonical JSON 顶层对象
     */
    private void verifyCanonicalFields(
        SnapshotDescriptor snapshot, LinkedHashMap<String, Object> root) {
        if (integer(root, "schemaVersion") != snapshot.schemaVersion()
            || !text(root, "runtimeProvider").equals(snapshot.runtimeProvider())
            || !text(root, "revisionId").equals(snapshot.revisionId().asString())
            || !text(root, "snapshotId").equals(snapshot.snapshotId().asString())
            || !text(root, "contentHash").equals(snapshot.contentHash().toString())) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_INVALID,
                "snapshot envelope does not match canonical JSON");
        }
        LinkedHashMap<String, Object> withoutHash = new LinkedHashMap<>(root);
        withoutHash.remove("contentHash");
        Checksum calculated = Checksum.sha256(write(withoutHash));
        if (!calculated.equals(snapshot.contentHash())) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_HASH_MISMATCH,
                "snapshot canonical content hash does not match");
        }
    }

    /**
     * 解析模型构建输入，仅保留 SecretRef。
     *
     * @param value Model 对象
     * @return Model 绑定
     */
    private ModelBinding model(Map<String, Object> value) {
        Map<String, Object> credential = object(value, "credential");
        return new ModelBinding(
            text(value, "provider"), text(value, "modelName"), object(value, "parameters"),
            SecretRef.parse(text(credential, "secretRef")),
            text(credential, "resolutionPolicy"));
    }

    /**
     * 解析并重新验证 Prompt 内容 Hash。
     *
     * @param values Prompt 数组
     * @return Prompt 绑定列表
     */
    private List<PromptBinding> promptBindings(List<Object> values) {
        List<PromptBinding> result = new ArrayList<>();
        for (Object item : values) {
            Map<String, Object> prompt = castObject(item, "prompt");
            String content = text(prompt, "content");
            String expected = text(prompt, "contentHash");
            if (!Checksum.sha256(content).toString().equals(expected)) {
                throw new AgentScopeProviderException(
                    ProviderErrorCode.SNAPSHOT_HASH_MISMATCH,
                    "prompt content hash does not match");
            }
            result.add(new PromptBinding(
                text(prompt, "role"), text(prompt, "promptVersionId"), expected, content));
        }
        return List.copyOf(result);
    }

    /**
     * 解析 MCP 绑定并检查跨 Server Tool 名冲突。
     *
     * @param values MCP 数组
     * @return MCP 绑定列表
     */
    private List<McpBinding> mcpBindings(List<Object> values) {
        List<McpBinding> result = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();
        for (Object item : values) {
            Map<String, Object> mcp = castObject(item, "MCP server");
            List<String> allowedTools = strings(mcp, "allowedTools");
            for (String tool : allowedTools) {
                if (!toolNames.add(tool)) {
                    throw new AgentScopeProviderException(
                        ProviderErrorCode.MCP_CONFIGURATION_INVALID,
                        "MCP tool name conflicts across server bindings");
                }
            }
            Optional<McpBinding.Credential> credential = Optional.ofNullable(mcp.get("credential"))
                .map(raw -> {
                    Map<String, Object> value = castObject(raw, "MCP credential");
                    return new McpBinding.Credential(
                        SecretRef.parse(text(value, "secretRef")),
                        text(value, "resolutionPolicy"));
                });
            result.add(new McpBinding(
                text(mcp, "mcpServerVersionId"), text(mcp, "transport"),
                URI.create(text(mcp, "endpoint")), credential, allowedTools));
        }
        return List.copyOf(result);
    }

    /**
     * 解析 Skill 制品引用。
     *
     * @param values Skill 数组
     * @return Skill 绑定列表
     */
    private List<SkillBinding> skillBindings(List<Object> values) {
        return values.stream().map(item -> {
            Map<String, Object> skill = castObject(item, "skill");
            Map<String, Object> artifact = object(skill, "artifact");
            return new SkillBinding(text(skill, "skillVersionId"), ObjectRef.of(
                text(artifact, "uri"), new Checksum(text(artifact, "checksum")),
                longValue(artifact, "size"), text(artifact, "mediaType")));
        }).toList();
    }

    /**
     * 解析 Knowledge Revision 与检索参数。
     *
     * @param values Knowledge 数组
     * @return Knowledge 绑定列表
     */
    private List<KnowledgeBinding> knowledgeBindings(List<Object> values) {
        return values.stream().map(item -> {
            Map<String, Object> knowledge = castObject(item, "knowledge");
            return new KnowledgeBinding(
                text(knowledge, "knowledgeRevisionId"),
                object(knowledge, "retrievalProfile"));
        }).toList();
    }

    /**
     * 解析 Memory Profile。
     *
     * @param value Memory 对象
     * @return Memory 绑定
     */
    private MemoryBinding memory(Map<String, Object> value) {
        return new MemoryBinding(text(value, "profileVersionId"), object(value, "configuration"));
    }

    /**
     * 解析 Workspace Profile。
     *
     * @param value Workspace 对象
     * @return Workspace 绑定
     */
    private WorkspaceBinding workspace(Map<String, Object> value) {
        return new WorkspaceBinding(
            text(value, "profileVersionId"), object(value, "configuration"));
    }

    /**
     * 解析 Sandbox Profile。
     *
     * @param value Sandbox 对象
     * @return Sandbox 绑定
     */
    private SandboxBinding sandbox(Map<String, Object> value) {
        return new SandboxBinding(text(value, "profileVersionId"), object(value, "configuration"));
    }

    /**
     * 解析 Permission Policy。
     *
     * @param value Permission 对象
     * @return Permission 绑定
     */
    private PermissionBinding permission(Map<String, Object> value) {
        List<PermissionBinding.Rule> rules = array(value, "rules").stream().map(item -> {
            Map<String, Object> rule = castObject(item, "permission rule");
            return new PermissionBinding.Rule(text(rule, "resource"), text(rule, "decision"));
        }).toList();
        return new PermissionBinding(text(value, "defaultDecision"), rules);
    }

    /**
     * 递归拒绝疑似明文凭据的字段名，但允许契约规定的 secretRef。
     *
     * @param value 待检查 JSON 值
     * @param path  诊断路径
     */
    private void rejectSensitiveValues(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                String name = String.valueOf(key);
                if (isSensitiveField(name)) {
                    throw new AgentScopeProviderException(
                        ProviderErrorCode.SNAPSHOT_INVALID,
                        "snapshot contains a forbidden sensitive field at " + path + "." + name);
                }
                rejectSensitiveValues(nested, path + "." + name);
            });
        } else if (value instanceof List<?> list) {
            list.forEach(item -> rejectSensitiveValues(item, path + "[]"));
        }
    }

    /**
     * 识别 Snapshot 中不应携带明文的常见凭据字段，同时允许标准 {@code secretRef}。
     *
     * @param name JSON 字段名
     * @return 疑似明文凭据字段时为 true
     */
    private boolean isSensitiveField(String name) {
        String compact = name.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        if ("secretref".equals(compact)) {
            return false;
        }
        return compact.contains("apikey")
            || compact.contains("secretvalue")
            || compact.contains("credentialvalue")
            || compact.endsWith("accesstoken")
            || compact.endsWith("refreshtoken")
            || compact.endsWith("authtoken")
            || compact.endsWith("bearertoken")
            || compact.endsWith("clientsecret")
            || compact.endsWith("privatekey")
            || "password".equals(compact)
            || "token".equals(compact)
            || "authorization".equals(compact)
            || "cookie".equals(compact);
    }

    /**
     * 解析 Canonical JSON 顶层对象。
     *
     * @param json Canonical JSON
     * @return 保留字段顺序的对象
     */
    private LinkedHashMap<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, OBJECT_TYPE);
        } catch (JsonProcessingException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_INVALID, "snapshot JSON cannot be parsed", exception);
        }
    }

    /**
     * 将保留顺序的对象重新写为紧凑 JSON。
     *
     * @param value 待序列化对象
     * @return 紧凑 JSON
     */
    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_INVALID,
                "snapshot JSON cannot be canonicalized", exception);
        }
    }

    /**
     * 获取对象字段。
     *
     * @param value 父对象
     * @param key   字段名
     * @return 子对象
     */
    private Map<String, Object> object(Map<String, Object> value, String key) {
        return castObject(value.get(key), key);
    }

    /**
     * 将 JSON 值校验为字符串键对象。
     *
     * @param value JSON 值
     * @param name  字段名
     * @return 类型化对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castObject(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)
            || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw invalid(name + " must be an object");
        }
        return (Map<String, Object>) map;
    }

    /**
     * 获取数组字段。
     *
     * @param value 父对象
     * @param key   字段名
     * @return JSON 数组
     */
    @SuppressWarnings("unchecked")
    private List<Object> array(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof List<?>)) {
            throw invalid(key + " must be an array");
        }
        return (List<Object>) raw;
    }

    /**
     * 获取非空文本字段。
     *
     * @param value 父对象
     * @param key   字段名
     * @return 文本值
     */
    private String text(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw invalid(key + " must be a non-blank string");
        }
        return text;
    }

    /**
     * 获取字符串数组字段。
     *
     * @param value 父对象
     * @param key   字段名
     * @return 字符串列表
     */
    private List<String> strings(Map<String, Object> value, String key) {
        List<Object> raw = array(value, key);
        if (raw.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            throw invalid(key + " must be a string array");
        }
        return raw.stream().map(String.class::cast).toList();
    }

    /**
     * 获取 long 数值字段。
     *
     * @param value 父对象
     * @param key   字段名
     * @return long 值
     */
    private long longValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number)) {
            throw invalid(key + " must be a number");
        }
        return number.longValue();
    }

    /**
     * 获取 int 数值字段并检查溢出。
     *
     * @param value 父对象
     * @param key   字段名
     * @return int 值
     */
    private int integer(Map<String, Object> value, String key) {
        long number = longValue(value, key);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(key + " exceeds integer range");
        }
        return (int) number;
    }

    /**
     * 创建统一 Snapshot 结构错误。
     *
     * @param message 诊断摘要
     * @return Provider 异常
     */
    private AgentScopeProviderException invalid(String message) {
        return new AgentScopeProviderException(ProviderErrorCode.SNAPSHOT_INVALID, message);
    }
}
