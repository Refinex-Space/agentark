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

package space.refinex.agentark.runtime.provider.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 锁定 Phase 12 已经验证的 AgentScope 2.0.2 二进制 API，使依赖升级显式失败。
 *
 * @author refinex
 */
class AgentScopeCompatibilityTest {

    /** 验证 Harness Event 流、定向取消和 StateStore 已发布 API 边界。 */
    @Test
    void locksVerifiedAgentScopeBinaryApi() throws Exception {
        assertThat(HarnessAgent.class.getMethod(
            "streamEvents", List.class, RuntimeContext.class)).isNotNull();
        assertThat(HarnessAgent.class.getMethod("getDelegate")).isNotNull();
        assertThat(io.agentscope.core.ReActAgent.class.getMethod(
            "interrupt", RuntimeContext.class)).isNotNull();
        assertThat(Arrays.stream(AgentStateStore.class.getMethods())
            .map(java.lang.reflect.Method::getName))
            .doesNotContain("getVersioned", "saveIfVersion", "supportsVersioning");
    }

    /** 验证可发布 Descriptor 资源与 Java Descriptor 维持一致。 */
    @Test
    void keepsDescriptorResourceAligned() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
            "/META-INF/agentark/runtime-provider.json")) {
            assertThat(input).isNotNull();
            Map<String, Object> resource = new ObjectMapper().readValue(
                input, new TypeReference<Map<String, Object>>() { });
            RuntimeProviderDescriptor descriptor = RuntimeProviderDescriptor.current();

            assertThat(resource.get("runtimeProvider")).isEqualTo(descriptor.providerId());
            assertThat(resource.get("providerVersion")).isEqualTo(descriptor.providerVersion());
            assertThat(resource.get("compilerVersion")).isEqualTo(descriptor.compilerVersion());
            List<String> capabilities = ((List<?>) resource.get("capabilities")).stream()
                .map(String::valueOf)
                .toList();
            assertThat(capabilities)
                .containsExactlyInAnyOrderElementsOf(descriptor.capabilities());
        }
    }

    /** 验证 Control 发布校验可读取稳定的 Provider、Schema 与能力字段。 */
    @Test
    void exposesDescriptorAsInternalContractFields() {
        Map<String, String> fields = RuntimeProviderDescriptor.current().asContractFields();

        assertThat(fields)
            .containsEntry("runtimeProvider", RuntimeProviderDescriptor.PROVIDER_ID)
            .containsEntry("providerVersion", RuntimeProviderDescriptor.PROVIDER_VERSION)
            .containsEntry("compilerVersion", RuntimeProviderDescriptor.COMPILER_VERSION)
            .containsEntry("schemaVersions", "1");
        assertThat(fields.get("capabilities"))
            .contains("streaming", "workspace", "sandbox", "state", "permission");
    }
}
