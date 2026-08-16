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

import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.snapshot.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 Snapshot v1 字段语义生成稳定 Canonical JSON，并计算排除顶层 contentHash 的 SHA-256。
 *
 * @author refinex
 */
public final class CanonicalSnapshotSerializer {

    /**
     * 应用统一 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper 应用统一 JSON 映射器
     */
    public CanonicalSnapshotSerializer(JsonMapper jsonMapper) {
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param provisional 已通过 Kernel 不变量校验的临时 Snapshot；其 contentHash 会被替换
     * @return 带真实 Hash 的 Snapshot 与规范 JSON
     */
    public SerializedSnapshot serialize(AgentRevisionSnapshot provisional) {
        Map<String, Object> withoutHash = fields(provisional, false);
        Checksum hash = Checksum.sha256(write(withoutHash));
        AgentRevisionSnapshot sealed = copyWithHash(provisional, hash);
        return new SerializedSnapshot(sealed, write(fields(sealed, true)));
    }

    /**
     * @param snapshot    Snapshot
     * @param includeHash 是否包含顶层内容 Hash
     * @return 按契约声明顺序组织的语言中立字段
     */
    private Map<String, Object> fields(AgentRevisionSnapshot snapshot, boolean includeHash) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", snapshot.schemaVersion().value());
        root.put("organizationId", snapshot.organizationId().asString());
        root.put("projectId", snapshot.projectId().asString());
        root.put("snapshotId", snapshot.snapshotId().asString());
        root.put("agentId", snapshot.agentId().asString());
        root.put("revisionId", snapshot.revisionId().asString());
        root.put("revisionNumber", snapshot.revisionNumber());
        root.put("createdAt", snapshot.createdAt().toString());
        if (includeHash) {
            root.put("contentHash", snapshot.contentHash().toString());
        }
        root.put("runtimeProvider", snapshot.runtimeProvider().value());
        root.put("agent", Map.of(
            "name", snapshot.agent().name(),
            "entrypoint", snapshot.agent().entrypoint().name().toLowerCase(java.util.Locale.ROOT),
            "requiredCapabilities", snapshot.agent().requiredCapabilities()));
        root.put("model", model(snapshot.model()));
        root.put("prompts", snapshot.prompts().stream().map(this::prompt).toList());
        root.put("mcpServers", snapshot.mcpServers().stream().map(this::mcp).toList());
        root.put("skills", snapshot.skills().stream().map(this::skill).toList());
        root.put("knowledge", snapshot.knowledge().stream().map(this::knowledge).toList());
        root.put("memory", Map.of(
            "profileVersionId", snapshot.memory().profileVersionId().asString()));
        root.put("workspace", Map.of(
            "profileVersionId", snapshot.workspace().profileVersionId().asString()));
        root.put("sandbox", Map.of(
            "profileVersionId", snapshot.sandbox().profileVersionId().asString()));
        root.put("permissions", permission(snapshot.permissions()));
        root.put("limits", Map.of(
            "turnTimeoutSeconds", snapshot.limits().turnTimeout().toSeconds(),
            "maxToolCalls", snapshot.limits().maxToolCalls(),
            "maxSubAgents", snapshot.limits().maxSubAgents()));
        return root;
    }

    /**
     * @param model 模型规范 @return 模型契约字段
     */
    private Map<String, Object> model(ModelSpec model) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", model.provider());
        value.put("modelName", model.modelName());
        value.put("parameters", Map.of(
            "temperature", model.parameters().temperature(),
            "maxTokens", model.parameters().maxTokens()));
        value.put("credential", credential(model.credential()));
        return value;
    }

    /**
     * @param prompt Prompt 规范 @return Prompt 契约字段
     */
    private Map<String, Object> prompt(PromptSpec prompt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", prompt.role().name().toLowerCase(java.util.Locale.ROOT));
        value.put("promptVersionId", prompt.promptVersionId().asString());
        value.put("contentHash", prompt.contentHash().toString());
        value.put("content", prompt.content());
        return value;
    }

    /**
     * @param mcp MCP 规范 @return MCP 契约字段
     */
    private Map<String, Object> mcp(McpSpec mcp) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mcpServerVersionId", mcp.mcpServerVersionId().asString());
        value.put("transport", switch (mcp.transport()) {
            case STREAMABLE_HTTP -> "streamable-http";
            case STDIO -> "stdio";
        });
        value.put("endpoint", mcp.endpoint().toString());
        mcp.credential().ifPresent(credential -> value.put("credential", credential(credential)));
        value.put("allowedTools", mcp.allowedTools());
        return value;
    }

    /**
     * @param skill Skill 规范 @return Skill 契约字段
     */
    private Map<String, Object> skill(SkillSpec skill) {
        return Map.of(
            "skillVersionId", skill.skillVersionId().asString(),
            "artifact", Map.of(
                "uri", skill.artifact().uri().toString(),
                "checksum", skill.artifact().checksum().toString(),
                "size", skill.artifact().size(),
                "mediaType", skill.artifact().mediaType()));
    }

    /**
     * @param knowledge Knowledge 规范 @return Knowledge 契约字段
     */
    private Map<String, Object> knowledge(KnowledgeSpec knowledge) {
        return Map.of(
            "knowledgeRevisionId", knowledge.knowledgeRevisionId().asString(),
            "retrievalProfile", Map.of(
                "topK", knowledge.retrievalProfile().topK(),
                "scoreThreshold", knowledge.retrievalProfile().scoreThreshold(),
                "reranker", knowledge.retrievalProfile().reranker()));
    }

    /**
     * @param permission 权限规范 @return 权限契约字段
     */
    private Map<String, Object> permission(PermissionSpec permission) {
        List<Map<String, Object>> rules = permission.rules().stream()
            .map(rule -> Map.<String, Object>of(
                "resource", rule.resource(), "decision", rule.decision().name()))
            .toList();
        return Map.of("defaultDecision", permission.defaultDecision().name(), "rules", rules);
    }

    /**
     * @param credential 凭据规范 @return 只含 SecretRef 的契约字段
     */
    private Map<String, Object> credential(CredentialSpec credential) {
        return Map.of(
            "secretRef", credential.secretRef().toString(),
            "resolutionPolicy", credential.resolutionPolicy().name());
    }

    /**
     * @param snapshot 临时 Snapshot @param hash 真实内容 Hash @return 封印后的 Snapshot
     */
    private AgentRevisionSnapshot copyWithHash(AgentRevisionSnapshot snapshot, Checksum hash) {
        return new AgentRevisionSnapshot(
            snapshot.schemaVersion(), snapshot.organizationId(), snapshot.projectId(),
            snapshot.snapshotId(), snapshot.agentId(), snapshot.revisionId(),
            snapshot.revisionNumber(), snapshot.createdAt(), hash, snapshot.runtimeProvider(),
            snapshot.agent(), snapshot.model(), snapshot.prompts(), snapshot.mcpServers(),
            snapshot.skills(), snapshot.knowledge(), snapshot.memory(), snapshot.workspace(),
            snapshot.sandbox(), snapshot.permissions(), snapshot.limits());
    }

    /**
     * @param value 规范字段 @return 无多余空白的 UTF-8 等价 JSON
     */
    private String write(Map<String, Object> value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("canonical snapshot serialization failed", exception);
        }
    }

    /**
     * @param snapshot      带真实 Hash 的 Kernel Snapshot
     * @param canonicalJson 完整规范 JSON
     * @author refinex
     */
    public record SerializedSnapshot(
        AgentRevisionSnapshot snapshot, String canonicalJson) {
        /**
         * 校验序列化结果完整。
         */
        public SerializedSnapshot {
            java.util.Objects.requireNonNull(snapshot, "snapshot must not be null");
            if (canonicalJson == null || canonicalJson.isBlank()) {
                throw new IllegalArgumentException("canonicalJson must not be blank");
            }
        }
    }
}
