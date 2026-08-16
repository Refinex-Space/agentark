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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.PrincipalType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 Gateway 与 Control API Key 内部契约的请求和安全失败语义。
 *
 * @author refinex
 */
class HttpControlApiKeyClientTest {

    /**
     * 验证凭据仅通过 Authorization 发送，且成功响应重新构造强类型租户主体。
     */
    @Test
    void sendsCredentialAndMapsVerifiedPrincipal() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("""
                    {
                      "issuer": "agentark-iam",
                      "subject": "service-account-1",
                      "principalType": "API_KEY",
                      "authorities": ["agent:read"],
                      "organizationId": "01900000-0000-7000-8000-000000000001",
                      "projectId": "01900000-0000-7000-8000-000000000002"
                    }
                    """)
                .build());
        };
        HttpControlApiKeyClient client = client(exchange);

        var principal = client.verifyRemotely("ak_live_test-credential").block();

        assertThat(captured.get().url().getPath())
            .isEqualTo("/internal/v1/auth/api-keys:verify");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
            .isEqualTo("ApiKey ak_live_test-credential");
        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().type()).isEqualTo(PrincipalType.API_KEY);
        assertThat(principal.orElseThrow().tenantSelection()).isPresent();
        assertThat(principal.orElseThrow().authorities()).containsExactly("agent:read");
    }

    /**
     * 验证 Control 的未认证响应只转为空结果，不把响应体或凭据包装为伪主体。
     */
    @Test
    void mapsUnauthorizedResponseToEmptyResult() {
        HttpControlApiKeyClient client = client(request -> Mono.just(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body("{\"title\":\"unauthorized\"}")
                .build()));

        assertThat(client.verifyRemotely("invalid-credential").block()).isEmpty();
    }

    /**
     * 创建使用测试交换函数的 Control 客户端。
     *
     * @param exchangeFunction 可检查或合成 HTTP 交换的函数
     * @return Control API Key 客户端
     */
    private HttpControlApiKeyClient client(ExchangeFunction exchangeFunction) {
        return new HttpControlApiKeyClient(
            WebClient.builder()
                .baseUrl("http://control.example.test")
                .exchangeFunction(exchangeFunction)
                .build());
    }
}
