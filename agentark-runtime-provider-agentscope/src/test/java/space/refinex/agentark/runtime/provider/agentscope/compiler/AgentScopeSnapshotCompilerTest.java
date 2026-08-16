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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;
import space.refinex.agentark.runtime.provider.agentscope.ProviderTestFixtures;
import space.refinex.agentark.runtime.provider.agentscope.RuntimeProviderDescriptor;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

/**
 * 验证 Golden Snapshot 编译、Envelope/Hash/能力门禁和 Single Flight 缓存。
 *
 * @author refinex
 */
class AgentScopeSnapshotCompilerTest {

    /** Jackson 2 映射器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 无敏感编译计划缓存。 */
    private final SnapshotCompilationCache cache = new SnapshotCompilationCache();

    /** 待测试 Snapshot Compiler。 */
    private final AgentScopeSnapshotCompiler compiler = new AgentScopeSnapshotCompiler(
        RuntimeProviderDescriptor.current(), objectMapper, cache);

    /** 验证 Golden Snapshot 可完全脱离 Control Catalog 编译为无敏感计划。 */
    @Test
    void compilesGoldenSnapshotWithoutCatalogLookup() {
        AgentScopeCompilationPlan plan = compiler.compile(
            ProviderTestFixtures.snapshot(objectMapper));

        assertThat(plan.agentName()).isEqualTo("review-agent");
        assertThat(plan.organizationId()).isEqualTo(ProviderTestFixtures.ORGANIZATION_ID);
        assertThat(plan.projectId()).isEqualTo(ProviderTestFixtures.PROJECT_ID);
        assertThat(plan.agentId().asString())
            .isEqualTo("0198a4b0-0004-7004-8004-000000000004");
        assertThat(plan.model().provider()).isEqualTo("fake");
        assertThat(plan.memory().configuration()).containsEntry("strategy", "session");
        assertThat(plan.workspace().configuration()).containsEntry("mode", "isolated");
        assertThat(plan.sandbox().configuration()).containsEntry("network", "deny");
        assertThat(plan.permission().defaultDecision()).isEqualTo("DENY");
        assertThat(plan.permission().rules()).isEmpty();
        assertThat(plan.toString()).contains("secret://project/model-test");
        assertThat(plan.toString()).doesNotContain("actual-secret-value");
    }

    /** 验证不兼容 Provider 和 Schema 在缓存查询前失败。 */
    @Test
    void rejectsProviderAndSchemaMismatch() {
        SnapshotDescriptor valid = ProviderTestFixtures.snapshot(objectMapper);
        SnapshotDescriptor providerMismatch = new SnapshotDescriptor(
            valid.revisionId(), valid.snapshotId(), valid.contentHash(), 1,
            "other-provider", valid.canonicalJson());
        SnapshotDescriptor schemaMismatch = new SnapshotDescriptor(
            valid.revisionId(), valid.snapshotId(), valid.contentHash(), 2,
            valid.runtimeProvider(), valid.canonicalJson());

        assertCode(providerMismatch, ProviderErrorCode.SNAPSHOT_PROVIDER_MISMATCH);
        assertCode(schemaMismatch, ProviderErrorCode.SNAPSHOT_SCHEMA_UNSUPPORTED);
    }

    /** 验证 Envelope 伪造 Hash 不能复用缓存计划。 */
    @Test
    void rejectsTamperedSnapshotHash() {
        SnapshotDescriptor valid = ProviderTestFixtures.snapshot(objectMapper);
        SnapshotDescriptor tampered = new SnapshotDescriptor(
            valid.revisionId(), valid.snapshotId(), Checksum.sha256("tampered"),
            valid.schemaVersion(), valid.runtimeProvider(), valid.canonicalJson());

        assertCode(tampered, ProviderErrorCode.SNAPSHOT_INVALID);
    }

    /** 验证不同 MCP Server 不能暴露同名 Tool。 */
    @Test
    void rejectsMcpToolNameConflict() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper, root ->
            root.put("mcpServers", List.of(
                mcp("0198a4b0-0020-7020-8020-000000000020", "https://one.example.com"),
                mcp("0198a4b0-0021-7021-8021-000000000021", "https://two.example.com"))));

        assertCode(snapshot, ProviderErrorCode.MCP_CONFIGURATION_INVALID);
    }

    /** 验证 Provider 能力不足时按稳定错误码拒绝编译。 */
    @Test
    @SuppressWarnings("unchecked")
    void rejectsUnsupportedModelRuntimeCapability() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper, root -> {
            Map<String, Object> agent = new java.util.LinkedHashMap<>(
                (Map<String, Object>) root.get("agent"));
            agent.put("requiredCapabilities", List.of("streaming", "audio"));
            root.put("agent", agent);
        });

        assertCode(snapshot, ProviderErrorCode.CAPABILITY_UNSUPPORTED);
    }

    /** 验证 Fake MCP 配置无需网络即可完整映射 Transport、Tool 白名单和 Secret 策略。 */
    @Test
    void mapsFakeMcpBindingWithoutNetworkAccess() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper, root ->
            root.put("mcpServers", List.of(Map.of(
                "mcpServerVersionId", "0198a4b0-0020-7020-8020-000000000020",
                "transport", "streamable-http",
                "endpoint", "https://fake-mcp.example.com",
                "credential", Map.of(
                    "secretRef", "secret://project/fake-mcp",
                    "resolutionPolicy", "PINNED_VERSION"),
                "allowedTools", List.of("repository.read")))));

        var binding = compiler.compile(snapshot).mcpServers().getFirst();

        assertThat(binding.endpoint().toString()).isEqualTo("https://fake-mcp.example.com");
        assertThat(binding.allowedTools()).containsExactly("repository.read");
        assertThat(binding.credential().orElseThrow().resolutionPolicy())
            .isEqualTo("PINNED_VERSION");
    }

    /** 验证底层 URI 构造错误不会以无分类异常泄漏到 Runtime。 */
    @Test
    void wrapsMalformedBindingAsStructuredCompilationError() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper, root ->
            root.put("mcpServers", List.of(Map.of(
                "mcpServerVersionId", "0198a4b0-0020-7020-8020-000000000020",
                "transport", "streamable-http",
                "endpoint", "not a URI with spaces",
                "allowedTools", List.of("repository.read")))));

        assertCode(snapshot, ProviderErrorCode.SNAPSHOT_INVALID);
    }

    /** 验证开放 Profile 配置中的常见凭据字段不能夹带明文进入编译计划。 */
    @Test
    @SuppressWarnings("unchecked")
    void rejectsPlaintextCredentialFieldsInProfileConfiguration() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper, root -> {
            Map<String, Object> workspace = new java.util.LinkedHashMap<>(
                (Map<String, Object>) root.get("workspace"));
            workspace.put("configuration", Map.of("api_key", "plaintext"));
            root.put("workspace", workspace);
        });

        assertCode(snapshot, ProviderErrorCode.SNAPSHOT_INVALID);
    }

    /** 验证并发编译只产生一个可重用计划实例。 */
    @Test
    void singleFlightsConcurrentCompilation() {
        SnapshotDescriptor snapshot = ProviderTestFixtures.snapshot(objectMapper);
        List<CompletableFuture<AgentScopeCompilationPlan>> futures = IntStream.range(0, 16)
            .mapToObj(ignored -> CompletableFuture.supplyAsync(() -> compiler.compile(snapshot)))
            .toList();
        List<AgentScopeCompilationPlan> plans = futures.stream()
            .map(CompletableFuture::join).toList();

        assertThat(plans).allMatch(plan -> plan == plans.getFirst());
        assertThat(cache.size()).isEqualTo(1);
    }

    /** 验证缓存达到上限后只淘汰已完成计划且所有内容均可重新编译。 */
    @Test
    void boundsRebuildableCache() {
        SnapshotCompilationCache bounded = new SnapshotCompilationCache(1);
        AgentScopeSnapshotCompiler boundedCompiler = new AgentScopeSnapshotCompiler(
            RuntimeProviderDescriptor.current(), objectMapper, bounded);
        SnapshotDescriptor first = ProviderTestFixtures.snapshot(objectMapper);
        SnapshotDescriptor second = ProviderTestFixtures.snapshot(objectMapper, root -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> agent = new java.util.LinkedHashMap<>(
                (Map<String, Object>) root.get("agent"));
            agent.put("name", "second-agent");
            root.put("agent", agent);
        });

        boundedCompiler.compile(first);
        boundedCompiler.compile(second);

        assertThat(bounded.size()).isEqualTo(1);
        assertThat(boundedCompiler.compile(first).agentName()).isEqualTo("review-agent");
    }

    /**
     * 创建一个显式白名单的 MCP 绑定 JSON。
     *
     * @param versionId MCP 版本标识
     * @param endpoint  HTTPS Endpoint
     * @return MCP JSON 对象
     */
    private Map<String, Object> mcp(String versionId, String endpoint) {
        return Map.of(
            "mcpServerVersionId", versionId,
            "transport", "streamable-http",
            "endpoint", endpoint,
            "allowedTools", List.of("repository.read"));
    }

    /**
     * 断言编译失败对应稳定错误码。
     *
     * @param snapshot Snapshot Descriptor
     * @param code     预期错误码
     */
    private void assertCode(SnapshotDescriptor snapshot, ProviderErrorCode code) {
        assertThatThrownBy(() -> compiler.compile(snapshot))
            .isInstanceOfSatisfying(AgentScopeProviderException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
