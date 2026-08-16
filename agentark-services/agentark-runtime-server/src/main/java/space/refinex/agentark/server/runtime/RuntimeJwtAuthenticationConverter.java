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

package space.refinex.agentark.server.runtime;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;

import java.util.Objects;

/**
 * 将已验证 JWT 转换为以 AgentArkPrincipal 为 Principal 的响应式认证 Token。
 *
 * @author refinex
 */
public final class RuntimeJwtAuthenticationConverter
    implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    /**
     * Foundation 严格 Principal 转换器。
     */
    private final AgentArkJwtPrincipalConverter principalConverter;

    /**
     * 创建 Runtime JWT 认证转换器。
     *
     * @param principalConverter Foundation Principal 转换器
     */
    public RuntimeJwtAuthenticationConverter(
        AgentArkJwtPrincipalConverter principalConverter) {
        this.principalConverter = Objects.requireNonNull(
            principalConverter, "principalConverter must not be null");
    }

    /**
     * 将 JWT 包装为已认证且带 Runtime 权限集合的 Token。
     *
     * @param source 已验证 JWT
     * @return 响应式认证 Token
     */
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt source) {
        var principal = principalConverter.convert(source);
        var authorities = principal.authorities().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        return Mono.just(UsernamePasswordAuthenticationToken.authenticated(
            principal, source, authorities));
    }
}
