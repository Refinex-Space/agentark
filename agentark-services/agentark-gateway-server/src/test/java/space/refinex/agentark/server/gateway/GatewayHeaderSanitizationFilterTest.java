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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.web.AgentArkReactiveWebAutoConfiguration;
import space.refinex.agentark.foundation.web.RequestContext;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 Gateway 删除伪造身份 Header，同时保留凭据、租户意图和 SSE 重放标识。
 *
 * @author refinex
 */
class GatewayHeaderSanitizationFilterTest {

    /**
     * 验证不可信身份字段被移除，Foundation 请求标识覆盖客户端值。
     */
    @Test
    void removesDerivedIdentityHeadersAndPreservesSignedInputs() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/runtime/runs/run-1/events:stream")
                .header("Authorization", "Bearer signed-token")
                .header("Last-Event-ID", "event-7")
                .header("X-AgentArk-Organization-Id", "organization-intent")
                .header("X-AgentArk-Principal", "forged-principal")
                .header("X-AgentArk-Service-Id", "forged-service")
                .header("X-Forwarded-Client-Cert", "forged-certificate")
                .header("X-Request-Id", "client-request")
                .build());
        exchange.getAttributes().put(
            AgentArkReactiveWebAutoConfiguration.REQUEST_CONTEXT_ATTRIBUTE,
            new RequestContext(
                "trusted-request",
                "0123456789abcdef0123456789abcdef",
                Optional.empty()));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return Mono.empty();
        };

        new GatewayHeaderSanitizationFilter().filter(exchange, chain).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer signed-token");
        assertThat(headers.getFirst("Last-Event-ID")).isEqualTo("event-7");
        assertThat(headers.getFirst("X-AgentArk-Organization-Id"))
            .isEqualTo("organization-intent");
        assertThat(headers.getFirst("X-Request-Id")).isEqualTo("trusted-request");
        assertThat(headers.getFirst("X-AgentArk-Principal")).isNull();
        assertThat(headers.getFirst("X-AgentArk-Service-Id")).isNull();
        assertThat(headers.getFirst("X-Forwarded-Client-Cert")).isNull();
    }
}
