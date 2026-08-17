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

package space.refinex.agentark.runtime.provider.agentscope.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 校验不可信 Skill 与 Tool 执行所需的 Sandbox 最小安全合同。
 *
 * <p>该合同拒绝可变镜像标签、Root、可写根文件系统、提权、Docker Socket、默认出网和无界资源。
 * 具体 Kubernetes 或远程 Sandbox Adapter 仍必须把这些字段逐项映射为底层强制策略。
 *
 * @author refinex
 */
public final class SandboxSecurityPolicy {

    /**
     * 只允许使用内容寻址的 OCI 镜像摘要。
     */
    private static final Pattern IMAGE_DIGEST = Pattern.compile(
        "[a-zA-Z0-9._/:@-]+@sha256:[a-f0-9]{64}");

    /**
     * 禁止实例化纯校验工具。
     */
    private SandboxSecurityPolicy() {
        // 仅提供静态验证入口。
    }

    /**
     * 校验 Sandbox Snapshot 已冻结全部强制安全字段和有界资源限制。
     *
     * @param configuration Snapshot 中的 Sandbox 配置
     */
    public static void validate(Map<String, Object> configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        requireNumber(configuration, "securityVersion", 1, 1);
        requireText(configuration, "trustZone", "UNTRUSTED");
        requireText(configuration, "runtime", "KUBERNETES");
        String imageDigest = string(configuration, "imageDigest");
        if (!IMAGE_DIGEST.matcher(imageDigest).matches()) {
            throw new IllegalArgumentException("sandbox imageDigest must be an OCI sha256 digest");
        }
        requireBoolean(configuration, "runAsNonRoot", true);
        requireBoolean(configuration, "readOnlyRootFilesystem", true);
        requireBoolean(configuration, "allowPrivilegeEscalation", false);
        requireBoolean(configuration, "privileged", false);
        requireBoolean(configuration, "networkDefaultDeny", true);
        requireBoolean(configuration, "mountDockerSocket", false);
        requireText(configuration, "seccompProfile", "RuntimeDefault");
        requireText(configuration, "workspaceIsolation", "SESSION");
        Object drops = configuration.get("capabilitiesDrop");
        if (!(drops instanceof List<?> list) || list.size() != 1 || !"ALL".equals(list.get(0))) {
            throw new IllegalArgumentException("sandbox capabilitiesDrop must contain only ALL");
        }
        Map<String, Object> resources = object(configuration, "resources");
        requireNumber(resources, "cpuMillis", 1, 8000);
        requireNumber(resources, "memoryBytes", 16L * 1024 * 1024, 16L * 1024 * 1024 * 1024);
        requireNumber(resources, "pids", 1, 1024);
        requireNumber(resources, "diskBytes", 1024, 100L * 1024 * 1024 * 1024);
        requireNumber(resources, "timeoutSeconds", 1, 3600);
        requireNumber(resources, "outputBytes", 1, 64L * 1024 * 1024);
        Map<String, Object> imageVerification = object(configuration, "imageVerification");
        requireBoolean(imageVerification, "signatureRequired", true);
        requireBoolean(imageVerification, "provenanceRequired", true);
        requireText(imageVerification, "scanStatus", "PASSED");
        Map<String, Object> artifactInspection = object(configuration, "artifactInspection");
        requireBoolean(artifactInspection, "secretScanRequired", true);
        requireText(artifactInspection, "piiAction", "QUARANTINE");
        requireNumber(artifactInspection, "maxArtifactBytes", 1,
            ((Number) resources.get("outputBytes")).longValue());
        Object allowedMediaTypes = artifactInspection.get("allowedMediaTypes");
        if (!(allowedMediaTypes instanceof List<?> types) || types.isEmpty()
            || types.stream().anyMatch(type -> !(type instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException(
                "sandbox artifactInspection.allowedMediaTypes must not be empty");
        }
        requireNumber(configuration, "cleanupTtlSeconds", 0, 3600);
    }

    /**
     * @param values 配置对象
     * @param key 字段名
     * @param expected 唯一允许文本
     */
    private static void requireText(Map<String, Object> values, String key, String expected) {
        if (!expected.equals(values.get(key))) {
            throw new IllegalArgumentException("sandbox " + key + " must be " + expected);
        }
    }

    /**
     * @param values 配置对象
     * @param key 字段名
     * @param expected 唯一允许布尔值
     */
    private static void requireBoolean(Map<String, Object> values, String key, boolean expected) {
        if (!(values.get(key) instanceof Boolean value) || value != expected) {
            throw new IllegalArgumentException("sandbox " + key + " must be " + expected);
        }
    }

    /**
     * @param values 配置对象
     * @param key 字段名
     * @param minimum 含边界最小值
     * @param maximum 含边界最大值
     */
    private static void requireNumber(
        Map<String, Object> values, String key, long minimum, long maximum) {
        if (!(values.get(key) instanceof Number number)
            || number.longValue() < minimum || number.longValue() > maximum) {
            throw new IllegalArgumentException(
                "sandbox " + key + " must be between " + minimum + " and " + maximum);
        }
    }

    /**
     * @param values 配置对象
     * @param key 字段名
     * @return 非空文本
     */
    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("sandbox " + key + " must not be blank");
        }
        return text;
    }

    /**
     * @param values 配置对象
     * @param key 字段名
     * @return 具名嵌套对象
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("sandbox " + key + " must be an object");
        }
        return (Map<String, Object>) value;
    }
}
