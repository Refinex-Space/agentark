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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对 Kubernetes Sandbox 基线执行静态安全回归，防止部署配置绕过 Runtime 安全合同。
 *
 * @author refinex
 */
class SandboxDeploymentPolicyTest {

    /**
     * 证明清单包含默认断网、restricted、非 Root、只读根文件系统、资源限制和自动清理。
     *
     * @throws IOException 无法读取仓库安全清单时抛出
     */
    @Test
    void shouldKeepKubernetesSandboxRestricted() throws IOException {
        String manifest = Files.readString(manifest());

        assertThat(manifest)
            .contains("pod-security.kubernetes.io/enforce: restricted")
            .contains("name: default-deny-all")
            .contains("- Egress")
            .contains("automountServiceAccountToken: false")
            .contains("runAsNonRoot: true")
            .contains("readOnlyRootFilesystem: true")
            .contains("allowPrivilegeEscalation: false")
            .contains("privileged: false")
            .contains("- ALL")
            .contains("type: RuntimeDefault")
            .contains("ttlSecondsAfterFinished: 60")
            .contains("limits:")
            .doesNotContain("hostPath:")
            .doesNotContain("docker.sock");
    }

    /**
     * @return 从模块目录解析到仓库级 Sandbox 清单的规范路径
     */
    private Path manifest() {
        return Path.of("..", "deploy", "security", "sandbox-policy.yaml")
            .toAbsolutePath().normalize();
    }
}
