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

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;

/**
 * 将已验证 JWT 转换为不保留原始 Token 的 Gateway 请求级认证主体。
 *
 * @author refinex
 */
public final class GatewayJwtAuthenticationConverter
    implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    /**
     * Foundation JWT Principal 转换器。
     */
    private final AgentArkJwtPrincipalConverter principalConverter;

    /**
     * 创建响应式 JWT 认证转换器。
     *
     * @param principalConverter Foundation Principal 转换器
     */
    public GatewayJwtAuthenticationConverter(
        AgentArkJwtPrincipalConverter principalConverter) {
        this.principalConverter = java.util.Objects.requireNonNull(
            principalConverter, "principalConverter must not be null");
    }

    /**
     * 转换已完成签名、Issuer、Audience、时间和算法校验的 JWT。
     *
     * @param source 已验证 JWT
     * @return 已认证主体信号
     */
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt source) {
        var principal = principalConverter.convert(source);
        var authorities = principal.authorities().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        AbstractAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        return Mono.just(authentication);
    }
}
