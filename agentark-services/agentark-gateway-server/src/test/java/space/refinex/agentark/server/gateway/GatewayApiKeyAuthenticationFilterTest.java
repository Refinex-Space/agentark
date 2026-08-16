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

package space.refinex.agentark.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 Gateway API Key 认证只用于 Control 路由且不把凭据放入认证主体。
 *
 * @author refinex
 */
class GatewayApiKeyAuthenticationFilterTest {

    /**
     * 符合 Control 生成格式的测试凭据。
     */
    private static final String CREDENTIAL =
        "ark_abcdefghijkl_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

    /**
     * 验证成功结果建立请求级 SecurityContext，且 Authentication 不保留明文凭据。
     */
    @Test
    void authenticatesControlRequestWithoutRetainingCredential() {
        AgentArkPrincipal principal = principal();
        ControlApiKeyVerifier verifier = credential -> Mono.just(Optional.of(principal));
        GatewayApiKeyAuthenticationFilter filter = filter(verifier);
        MockServerWebExchange exchange = exchange("/api/v1/organizations");
        AtomicReference<Authentication> observed = new AtomicReference<>();

        filter.filter(exchange, current -> ReactiveSecurityContextHolder.getContext()
            .doOnNext(context -> observed.set(context.getAuthentication()))
            .then()).block();

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getPrincipal()).isEqualTo(principal);
        assertThat(observed.get().getCredentials()).isNull();
        assertThat(observed.get().toString()).doesNotContain(CREDENTIAL);
    }

    /**
     * 验证 API Key 不能绕过 Runtime 独立 Bearer JWT 边界。
     */
    @Test
    void rejectsApiKeyForRuntimeRoute() {
        AtomicBoolean verified = new AtomicBoolean();
        ControlApiKeyVerifier verifier = credential -> {
            verified.set(true);
            return Mono.just(Optional.of(principal()));
        };
        GatewayApiKeyAuthenticationFilter filter = filter(verifier);
        MockServerWebExchange exchange = exchange("/api/v1/runtime/sessions");

        filter.filter(exchange, current -> Mono.empty()).block();

        assertThat(verified).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * 创建 API Key 过滤器。
     *
     * @param verifier 测试验证端口
     * @return 过滤器
     */
    private GatewayApiKeyAuthenticationFilter filter(ControlApiKeyVerifier verifier) {
        return new GatewayApiKeyAuthenticationFilter(
            verifier,
            new GatewaySecurityProblemWriter(JsonMapper.builder().build()));
    }

    /**
     * 创建携带测试 API Key 的请求交换。
     *
     * @param path 请求路径
     * @return Mock 交换
     */
    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
            MockServerHttpRequest.get(path)
                .header("Authorization", "ApiKey " + CREDENTIAL)
                .build());
    }

    /**
     * 创建项目级 API Key 主体。
     *
     * @return API Key 主体
     */
    private AgentArkPrincipal principal() {
        TenantSelection selection = new TenantSelection(
            OrganizationId.generate(),
            Optional.of(ProjectId.generate()),
            Optional.empty());
        return new AgentArkPrincipal(
            "agentark-iam",
            "service-account-1",
            PrincipalType.API_KEY,
            Set.of("agent:read"),
            Optional.of(selection),
            Optional.empty());
    }
}
