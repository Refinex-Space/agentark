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

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * 为内置 Identity 或外部 OIDC BFF 统一提供 Redis Indexed WebSession 和安全 Cookie。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${agentark.gateway.identity.enabled:false}' == 'true' || '${agentark.gateway.bff.enabled:false}' == 'true'")
@EnableRedisIndexedWebSession(
    maxInactiveIntervalInSeconds = 1800,
    redisNamespace = "agentark:gateway:sessions")
public class GatewaySessionConfiguration {

    /**
     * 创建无状态配置实例。
     */
    public GatewaySessionConfiguration() {
    }

    /**
     * 创建 HttpOnly、SameSite=Lax 且生产可强制 Secure 的 Session Cookie。
     */
    @Bean
    public WebSessionIdResolver gatewayWebSessionIdResolver(
        GatewayIdentityProperties identity, GatewayBffProperties bff) {
        boolean local = identity.isEnabled();
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName(local ? identity.getSessionCookieName() : bff.getSessionCookieName());
        resolver.addCookieInitializer(builder -> builder
            .httpOnly(true)
            .secure(local ? identity.isSessionCookieSecure() : bff.isSessionCookieSecure())
            .sameSite("Lax")
            .path("/"));
        return resolver;
    }
}
