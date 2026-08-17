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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 证明 Sandbox 安全合同强制非 Root、只读根文件系统、默认断网和有界资源。
 *
 * @author refinex
 */
class SandboxSecurityPolicyTest {

    /**
     * 证明完整的最小权限 Sandbox 配置可以进入 Runtime。
     */
    @Test
    void shouldAcceptRestrictedSandbox() {
        assertThatCode(() -> new SandboxBinding("version-1", secureConfiguration()))
            .doesNotThrowAnyException();
    }

    /**
     * 证明 Root、可写根文件系统、开放网络、Docker Socket 或可变镜像任一出现即拒绝。
     */
    @Test
    void shouldRejectPrivilegeAndEgressDowngrades() {
        assertRejected("runAsNonRoot", false);
        assertRejected("readOnlyRootFilesystem", false);
        assertRejected("networkDefaultDeny", false);
        assertRejected("mountDockerSocket", true);
        assertRejected("privileged", true);
        assertRejected("imageDigest", "registry.example.com/agentark/sandbox:latest");
    }

    /**
     * 证明 CPU、内存、PID、磁盘、时间和输出上限不能缺失或超出平台界限。
     */
    @Test
    void shouldRejectUnboundedResources() {
        Map<String, Object> configuration = secureConfiguration();
        Map<String, Object> resources = new HashMap<>(
            cast(configuration.get("resources")));
        resources.put("timeoutSeconds", 0);
        configuration.put("resources", resources);

        assertThatThrownBy(() -> new SandboxBinding("version-1", configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeoutSeconds");
    }

    /**
     * @param key 被篡改字段
     * @param value 不安全值
     */
    private void assertRejected(String key, Object value) {
        Map<String, Object> configuration = secureConfiguration();
        configuration.put(key, value);
        assertThatThrownBy(() -> new SandboxBinding("version-1", configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * @return 满足生产最小权限边界的 Sandbox 配置
     */
    private Map<String, Object> secureConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("securityVersion", 1);
        configuration.put("trustZone", "UNTRUSTED");
        configuration.put("runtime", "KUBERNETES");
        configuration.put("imageDigest", "registry.example.com/agentark/sandbox@sha256:"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        configuration.put("runAsNonRoot", true);
        configuration.put("readOnlyRootFilesystem", true);
        configuration.put("allowPrivilegeEscalation", false);
        configuration.put("privileged", false);
        configuration.put("capabilitiesDrop", List.of("ALL"));
        configuration.put("seccompProfile", "RuntimeDefault");
        configuration.put("networkDefaultDeny", true);
        configuration.put("mountDockerSocket", false);
        configuration.put("workspaceIsolation", "SESSION");
        configuration.put("resources", Map.of(
            "cpuMillis", 1000,
            "memoryBytes", 536870912,
            "pids", 128,
            "diskBytes", 1073741824,
            "timeoutSeconds", 300,
            "outputBytes", 1048576));
        configuration.put("imageVerification", Map.of(
            "signatureRequired", true,
            "provenanceRequired", true,
            "scanStatus", "PASSED"));
        configuration.put("artifactInspection", Map.of(
            "secretScanRequired", true,
            "piiAction", "QUARANTINE",
            "maxArtifactBytes", 1048576,
            "allowedMediaTypes", List.of("text/plain", "application/json")));
        configuration.put("cleanupTtlSeconds", 60);
        return configuration;
    }

    /**
     * @param value 已知为字符串键对象的测试值
     * @return 可复制的对象视图
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
