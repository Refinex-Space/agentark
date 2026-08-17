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

package space.refinex.agentark.runtime.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeController;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeProblemDetailAdvice;
import space.refinex.agentark.runtime.domain.RuntimeModels.DeploymentDescriptor;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 WebFlux 参数绑定验证 Runtime Public API 接单、查询与租户隐藏语义。
 *
 * @author refinex
 */
class RuntimeApiE2ETest {

    /**
     * 创建 Runtime API 端到端测试实例。
     */
    RuntimeApiE2ETest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 证明 Session 返回 201、Turn 事务后返回 202，且 Event Envelope 可按 Run 查询。
     */
    @Test
    void acceptsSessionAndTurnThenExposesPersistentRunEvent() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        RuntimeController controller = controller(fixture);
        WebTestClient client = client(controller, principal(
            fixture.organizationId, fixture.projectId,
            Set.of("runtime:execute", "runtime:read")));

        JsonNode session = client.post().uri("/api/v1/runtime/sessions")
            .header("Idempotency-Key", "api-session")
            .bodyValue(Map.of(
                "organizationId", fixture.organizationId.asString(),
                "projectId", fixture.projectId.asString(),
                "deploymentId", fixture.deploymentId.asString(),
                "participantMetadata", Map.of("actor", "api-test"),
                "channelMetadata", Map.of("channel", "test")))
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().valueMatches("Location", "/api/v1/runtime/sessions/.+")
            .expectBody(JsonNode.class)
            .returnResult().getResponseBody();
        assertThat(session).isNotNull();
        String sessionId = session.get("sessionId").stringValue();

        JsonNode turn = client.post().uri(
                "/api/v1/runtime/sessions/{sessionId}/turns", sessionId)
            .header("Idempotency-Key", "api-turn")
            .bodyValue(Map.of(
                "organizationId", fixture.organizationId.asString(),
                "projectId", fixture.projectId.asString(),
                "input", Map.of("text", "hello"),
                "priority", 10))
            .exchange()
            .expectStatus().isAccepted()
            .expectHeader().valueMatches("Location", "/api/v1/runtime/runs/.+")
            .expectBody(JsonNode.class)
            .returnResult().getResponseBody();
        assertThat(turn).isNotNull();
        String runId = turn.get("runId").stringValue();

        client.get().uri("/api/v1/runtime/runs/{runId}", runId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.runId").isEqualTo(runId)
            .jsonPath("$.status").isEqualTo("CREATED");
        client.get().uri("/api/v1/runtime/runs/{runId}/events?after=0&limit=10", runId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].eventType").isEqualTo("run.accepted")
            .jsonPath("$[0].sessionSequence").isEqualTo(1)
            .jsonPath("$[0].sequence").isEqualTo(1)
            .jsonPath("$[0].payload").exists()
            .jsonPath("$[0].payloadRef").doesNotExist();
    }

    /**
     * 证明不同 Project 的已认证主体不能枚举其他租户的 Session。
     */
    @Test
    void hidesCrossTenantSessionAsNotFound() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        RuntimeController controller = controller(fixture);
        var owner = client(controller, principal(
            fixture.organizationId, fixture.projectId, Set.of("runtime:execute")));
        JsonNode session = owner.post().uri("/api/v1/runtime/sessions")
            .header("Idempotency-Key", "hidden-session")
            .bodyValue(Map.of(
                "organizationId", fixture.organizationId.asString(),
                "projectId", fixture.projectId.asString(),
                "deploymentId", fixture.deploymentId.asString()))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(JsonNode.class)
            .returnResult().getResponseBody();
        assertThat(session).isNotNull();

        client(controller, principal(
            fixture.organizationId, ProjectId.generate(), Set.of("runtime:read")))
            .get().uri(
                "/api/v1/runtime/sessions/{sessionId}",
                session.get("sessionId").stringValue())
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType("application/problem+json")
            .expectBody()
            .jsonPath("$.code").isEqualTo("ARK-RUNTIME-NOT_FOUND-00001");
    }

    /**
     * 使用中立内存事实源装配 Runtime Controller，不绕过应用服务和授权层。
     *
     * @param fixture Phase 13 测试夹具
     * @return Runtime Controller
     */
    private RuntimeController controller(RuntimePhase13TestSupport fixture) {
        RuntimeProviderMetadata provider = new RuntimeProviderMetadata(
            "agentscope-java-2", "compiler-v1", Set.of(1), Set.of("streaming"));
        RuntimeProviderCatalog providerCatalog = () -> provider;
        RuntimeAdmissionService admission = new RuntimeAdmissionService(
            ignored -> new DeploymentDescriptor(
                fixture.deploymentId, fixture.organizationId, fixture.projectId,
                fixture.revisionId, true, provider.providerId(), 1, Set.of()),
            ignored -> fixture.snapshot, providerCatalog, fixture.application);
        RuntimeQueryService query = new RuntimeQueryService(
            fixture.store, fixture.store, fixture.store, fixture.store);
        RuntimeControlService control = new RuntimeControlService(
            fixture.coordinator, fixture.engine);
        return new RuntimeController(
            admission, query, control, fixture.coordinator,
            new RuntimeAuthorizationService(),
            new RuntimeEventStreamService(fixture.store, fixture.notifier),
            JsonMapper.builder().build(), providerCatalog, Optional.empty());
    }

    /**
     * 创建带指定租户选择和权限的测试主体。
     *
     * @param organizationId 租户组织
     * @param projectId      租户项目
     * @param authorities    Runtime 权限集合
     * @return 已认证主体
     */
    private AgentArkPrincipal principal(
        OrganizationId organizationId, ProjectId projectId, Set<String> authorities) {
        return new AgentArkPrincipal(
            "https://issuer.agentark.test", "runtime-api-test", PrincipalType.USER,
            authorities,
            Optional.of(new TenantSelection(
                organizationId, Optional.of(projectId), Optional.empty())),
            Optional.empty());
    }

    /**
     * 创建把测试 Authentication 注入 WebFlux Exchange 的 HTTP 客户端。
     *
     * @param controller Runtime Controller
     * @param principal  测试主体
     * @return WebTestClient
     */
    private WebTestClient client(RuntimeController controller, AgentArkPrincipal principal) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            principal, null, "runtime-test");
        return WebTestClient.bindToController(controller)
            .controllerAdvice(new RuntimeProblemDetailAdvice())
            .webFilter((exchange, chain) -> chain.filter(
                exchange.mutate().principal(Mono.just(authentication)).build()))
            .build();
    }
}
