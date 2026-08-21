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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 CSRF 只约束浏览器 Session 副作用请求，不破坏 Bearer/API Key 客户端。
 *
 * @author refinex
 */
class GatewayBffCsrfMatcherTest {

    /** BFF 会话 Cookie 名称。 */
    private static final String SESSION_COOKIE = "AGENTARK_SESSION";

    /** 待测 CSRF 匹配器。 */
    private final GatewayBffCsrfMatcher matcher = new GatewayBffCsrfMatcher(SESSION_COOKIE);

    /**
     * 验证带 Session Cookie 的 POST 必须提交 CSRF。
     */
    @Test
    void matchesUnsafeSessionRequest() {
        var request = MockServerHttpRequest.post("/api/v1/projects")
            .cookie(new HttpCookie(SESSION_COOKIE, "session-id"))
            .build();

        assertThat(matcher.matches(MockServerWebExchange.from(request)).block().isMatch()).isTrue();
    }

    /**
     * 验证无 Session Cookie 的 Bearer POST 不进入浏览器 CSRF 匹配。
     */
    @Test
    void ignoresStatelessApiRequest() {
        var request = MockServerHttpRequest.post("/api/v1/projects")
            .header("Authorization", "Bearer test-token")
            .build();

        assertThat(matcher.matches(MockServerWebExchange.from(request)).block().isMatch()).isFalse();
    }

    /**
     * 验证 Session GET 不要求 CSRF。
     */
    @Test
    void ignoresSafeSessionRequest() {
        var request = MockServerHttpRequest.get("/api/v1/auth/session")
            .cookie(new HttpCookie(SESSION_COOKIE, "session-id"))
            .build();

        assertThat(matcher.matches(MockServerWebExchange.from(request)).block().isMatch()).isFalse();
    }
}
