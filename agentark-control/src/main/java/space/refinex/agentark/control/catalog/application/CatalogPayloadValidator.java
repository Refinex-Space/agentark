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

package space.refinex.agentark.control.catalog.application;

import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SecretRef;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.*;

/**
 * 对语言中立资产载荷执行分类约束、SecretRef、ObjectRef 与 MCP SSRF 信息模型校验。
 *
 * @author refinex
 */
public final class CatalogPayloadValidator {

    /** 被视为疑似明文凭据且禁止进入资产 JSON 的字段名。 */
    private static final Set<String> FORBIDDEN_SECRET_FIELDS = Set.of(
        "apikey", "api_key", "token", "password", "secretvalue", "secret_value", "credentials");

    /** 统一 JSON 映射器。 */
    private final JsonMapper jsonMapper;

    /**
     * @param jsonMapper 应用统一 JSON 映射器
     */
    public CatalogPayloadValidator(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param kind 资产分类
     * @param payload 调用方语言中立载荷
     * @return 完成校验且键顺序稳定的载荷
     */
    public CatalogValidatedPayload validateVersion(
        CatalogAssetKind kind, Map<String, Object> payload) {
        Map<String, Object> checked = requireMap(payload, "payload");
        rejectSecretValues(checked);
        List<Map<String, Object>> tools = switch (kind) {
            case PROMPT -> validatePrompt(checked);
            case MODEL_PROVIDER -> validateModelProfile(checked);
            case MCP_SERVER -> validateMcpServer(checked);
            case SKILL -> validateSkill(checked);
            case MEMORY_PROFILE, WORKSPACE_PROFILE, SANDBOX_PROFILE ->
                validateProfile(checked);
            case PERMISSION_POLICY -> validatePermissionPolicy(checked);
            case AGENT -> throw new IllegalArgumentException("agent does not support versions");
        };
        return new CatalogValidatedPayload(writeCanonical(checked), tools);
    }

    /**
     * @param kind 资产分类
     * @param metadata 分类专属元数据
     * @return 规范 JSON
     */
    public String validateMetadata(CatalogAssetKind kind, Map<String, Object> metadata) {
        Map<String, Object> checked = requireMap(metadata, "metadata");
        rejectSecretValues(checked);
        if (kind == CatalogAssetKind.MODEL_PROVIDER) {
            requiredEnum(checked, "providerType", Set.of(
                "OPENAI_COMPATIBLE", "ANTHROPIC", "GEMINI", "OLLAMA", "CUSTOM"));
            requireMap(checked.get("descriptor"), "descriptor");
        } else if (!checked.isEmpty()) {
            throw new IllegalArgumentException("metadata is only supported for model-provider");
        }
        return writeCanonical(checked);
    }

    /**
     * 提取所有以 {@code SecretRef} 结尾的引用字段，供应用服务执行同项目存在性检查。
     *
     * @param payload 已通过分类校验的版本载荷
     * @return 去重后的 SecretRef 集合
     */
    public Set<SecretRef> secretRefs(Map<String, Object> payload) {
        Set<SecretRef> refs = new LinkedHashSet<>();
        collectSecretRefs(payload, refs);
        return Set.copyOf(refs);
    }

    /**
     * @param payload Prompt 载荷
     * @return 空 Tool 列表
     */
    private List<Map<String, Object>> validatePrompt(Map<String, Object> payload) {
        requiredString(payload, "template", 1_000_000);
        requireMap(payload.get("variableSchema"), "variableSchema");
        requiredString(payload, "purpose", 255);
        return List.of();
    }

    /**
     * @param payload Model Profile 载荷
     * @return 空 Tool 列表
     */
    private List<Map<String, Object>> validateModelProfile(Map<String, Object> payload) {
        requiredString(payload, "modelName", 255);
        List<?> capabilities = requiredList(payload, "capabilities");
        Set<String> allowed = Set.of("TOOL", "VISION", "STRUCTURED_OUTPUT", "STREAMING");
        if (capabilities.isEmpty() || capabilities.stream()
            .anyMatch(value -> !(value instanceof String text) || !allowed.contains(text))) {
            throw new IllegalArgumentException("capabilities contains unsupported values");
        }
        requireMap(payload.get("parameters"), "parameters");
        optionalSecretRef(payload, "credentialSecretRef");
        return List.of();
    }

    /**
     * @param payload MCP Server Version 载荷
     * @return 已校验 Tool Descriptor 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validateMcpServer(Map<String, Object> payload) {
        String transport = requiredEnum(
            payload, "transport", Set.of("STREAMABLE_HTTP", "SSE", "STDIO"));
        if ("STDIO".equals(transport)) {
            requiredString(payload, "commandName", 255);
            if (payload.get("endpointUri") != null) {
                throw new IllegalArgumentException("stdio transport must not contain endpointUri");
            }
        } else {
            validateRemoteEndpoint(requiredString(payload, "endpointUri", 2048));
            if (payload.get("commandName") != null) {
                throw new IllegalArgumentException("remote transport must not contain commandName");
            }
        }
        requireMap(payload.get("transportConfig"), "transportConfig");
        optionalSecretRef(payload, "tlsSecretRef");
        optionalSecretRef(payload, "authSecretRef");
        Map<String, Object> policy = requireMap(payload.get("ssrfPolicy"), "ssrfPolicy");
        requireTrue(policy, "denyPrivateNetworks");
        requireTrue(policy, "denyCloudMetadata");
        requireTrue(policy, "resolveAndPinDns");

        List<?> values = requiredList(payload, "tools");
        List<Map<String, Object>> tools = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object value : values) {
            Map<String, Object> tool = requireMap(value, "tool");
            String name = requiredString(tool, "name", 255);
            if (!names.add(name)) {
                throw new IllegalArgumentException("tool names must be unique in a server version");
            }
            requireMap(tool.get("argumentSchema"), "argumentSchema");
            requiredEnum(tool, "accessMode", Set.of("READ", "WRITE", "READ_WRITE"));
            requiredEnum(tool, "riskLevel", Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL"));
            requiredEnum(tool, "idempotency", Set.of("IDEMPOTENT", "NON_IDEMPOTENT", "UNKNOWN"));
            requireMap(tool.get("permissionMetadata"), "permissionMetadata");
            tools.add((Map<String, Object>) canonicalValue(tool));
        }
        return List.copyOf(tools);
    }

    /**
     * @param payload Skill Version 载荷
     * @return 空 Tool 列表
     */
    private List<Map<String, Object>> validateSkill(Map<String, Object> payload) {
        Map<String, Object> artifact = requireMap(payload.get("artifact"), "artifact");
        ObjectRef.of(
            requiredString(artifact, "uri", 2048),
            new space.refinex.agentark.kernel.ref.Checksum(
                requiredString(artifact, "checksum", 71)),
            requiredLong(artifact, "size", 0),
            requiredString(artifact, "mediaType", 255));
        validateSourceUri(requiredString(payload, "sourceUri", 2048));
        requiredString(payload, "license", 255);
        Object signature = payload.get("signature");
        if (signature != null) {
            requireMap(signature, "signature");
        }
        requireMap(payload.get("compatibility"), "compatibility");
        return List.of();
    }

    /**
     * @param payload Profile 载荷
     * @return 空 Tool 列表
     */
    private List<Map<String, Object>> validateProfile(Map<String, Object> payload) {
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("profile payload must not be empty");
        }
        return List.of();
    }

    /**
     * @param payload Permission Policy 载荷
     * @return 空 Tool 列表
     */
    private List<Map<String, Object>> validatePermissionPolicy(Map<String, Object> payload) {
        requiredEnum(payload, "defaultDecision", Set.of("ALLOW", "DENY"));
        requiredList(payload, "rules");
        requiredList(payload, "scopes");
        requireMap(payload.get("approvalPolicy"), "approvalPolicy");
        return List.of();
    }

    /**
     * @param uriText 远程 Endpoint URI
     */
    private void validateRemoteEndpoint(String uriText) {
        URI uri = URI.create(uriText);
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null || uri.getRawFragment() != null
            || host.equalsIgnoreCase("localhost") || host.equals("169.254.169.254")
            || isPrivateIpv4(host)) {
            throw new IllegalArgumentException("remote MCP endpoint violates SSRF baseline");
        }
    }

    /**
     * @param host Host 候选值
     * @return 是否为明确的私网 IPv4 字面量
     */
    private boolean isPrivateIpv4(String host) {
        return host.matches("10\\..*")
            || host.matches("127\\..*")
            || host.matches("192\\.168\\..*")
            || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    /**
     * @param uriText 来源 URI
     */
    private void validateSourceUri(String uriText) {
        URI uri = URI.create(uriText);
        if (uri.getScheme() == null || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("sourceUri must not contain credentials or query");
        }
    }

    /**
     * @param payload 载荷
     * @param field SecretRef 字段
     */
    private void optionalSecretRef(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value != null) {
            SecretRef.parse(requiredString(payload, field, 512));
        }
    }

    /**
     * @param value 当前结构
     */
    private void rejectSecretValues(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_SECRET_FIELDS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("plaintext secret field is forbidden");
                }
                rejectSecretValues(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            list.forEach(this::rejectSecretValues);
        }
    }

    /**
     * @param value 当前 JSON 结构
     * @param refs 收集目标
     */
    private void collectSecretRefs(Object value, Set<SecretRef> refs) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.endsWith("SecretRef") && entry.getValue() instanceof String text) {
                    refs.add(SecretRef.parse(text));
                }
                collectSecretRefs(entry.getValue(), refs);
            }
        } else if (value instanceof List<?> list) {
            list.forEach(child -> collectSecretRefs(child, refs));
        }
    }

    /**
     * @param map 对象
     * @param field 字段
     * @param allowed 合法值
     * @return 合法枚举字符串
     */
    private String requiredEnum(
        Map<String, Object> map, String field, Set<String> allowed) {
        String value = requiredString(map, field, 64);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " contains unsupported value");
        }
        return value;
    }

    /**
     * @param map 对象
     * @param field 字段
     * @param maxLength 最大字符数
     * @return 非空字符串
     */
    private String requiredString(Map<String, Object> map, String field, int maxLength) {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException(field + " has invalid length");
        }
        return text;
    }

    /**
     * @param map 对象
     * @param field 字段
     * @param minimum 最小值
     * @return 合法长整数
     */
    private long requiredLong(Map<String, Object> map, String field, long minimum) {
        Object value = map.get(field);
        if (!(value instanceof Number number) || number.longValue() < minimum) {
            throw new IllegalArgumentException(field + " has invalid numeric value");
        }
        return number.longValue();
    }

    /**
     * @param map 对象
     * @param field 字段
     * @return 非空列表
     */
    private List<?> requiredList(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return list;
    }

    /**
     * @param map 对象
     * @param field 必须为 true 的字段
     */
    private void requireTrue(Map<String, Object> map, String field) {
        if (!Boolean.TRUE.equals(map.get(field))) {
            throw new IllegalArgumentException(field + " must be true");
        }
    }

    /**
     * @param value 对象候选
     * @param name 错误字段名
     * @return 字符串 Key 的对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String name) {
        if (!(value instanceof Map<?, ?> raw)
            || raw.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new IllegalArgumentException(name + " must be an object with string keys");
        }
        return (Map<String, Object>) raw;
    }

    /**
     * @param value 待规范化值
     * @return Map Key 已排序且集合已防御性复制的值
     */
    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put(String.valueOf(key), canonicalValue(child)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }

    /**
     * @param value 待序列化值
     * @return 稳定键顺序 JSON
     */
    private String writeCanonical(Object value) {
        try {
            return jsonMapper.writeValueAsString(canonicalValue(value));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("payload cannot be serialized as JSON", exception);
        }
    }
}
