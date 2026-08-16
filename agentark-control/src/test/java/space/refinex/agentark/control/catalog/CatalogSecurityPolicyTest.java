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

package space.refinex.agentark.control.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.catalog.application.CatalogPayloadValidator;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 验证资产载荷拒绝明文凭据和 MCP 私网 Endpoint。
 *
 * @author refinex
 */
class CatalogSecurityPolicyTest {

    /** 资产载荷校验器。 */
    private final CatalogPayloadValidator validator =
        new CatalogPayloadValidator(JsonMapper.builder().build());

    /** 创建 Catalog 安全策略测试实例。 */
    CatalogSecurityPolicyTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 验证疑似明文 Token 字段不能进入 Model Profile。 */
    @Test
    void rejectsPlaintextCredentialFields() {
        assertThatThrownBy(() -> validator.validateVersion(
            CatalogAssetKind.MODEL_PROVIDER,
            Map.of(
                "modelName", "example",
                "capabilities", List.of("TOOL"),
                "parameters", Map.of(),
                "token", "forbidden")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("plaintext secret");
    }

    /** 验证 MCP Remote Transport 拒绝私网和云元数据地址。 */
    @Test
    void rejectsPrivateMcpEndpoints() {
        assertThatThrownBy(() -> validator.validateVersion(
            CatalogAssetKind.MCP_SERVER,
            Map.of(
                "transport", "STREAMABLE_HTTP",
                "endpointUri", "https://127.0.0.1/mcp",
                "transportConfig", Map.of(),
                "ssrfPolicy", Map.of(
                    "denyPrivateNetworks", true,
                    "denyCloudMetadata", true,
                    "resolveAndPinDns", true),
                "tools", List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SSRF");
    }
}

