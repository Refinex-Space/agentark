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

package space.refinex.agentark.control.iam.adapter.in.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;

import java.util.Objects;

/**
 * 将 Foundation 验证后的 JWT Principal 包装为 Spring Security 已认证 Token。
 *
 * @author refinex
 */
public final class IamJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Foundation 严格 Principal 转换器。
     */
    private final AgentArkJwtPrincipalConverter principalConverter;

    /**
     * 创建 JWT 认证转换器。
     *
     * @param principalConverter Foundation Principal 转换器
     */
    public IamJwtAuthenticationConverter(AgentArkJwtPrincipalConverter principalConverter) {
        this.principalConverter = Objects.requireNonNull(principalConverter, "principalConverter must not be null");
    }

    /**
     * 将 JWT 转换为以 AgentArkPrincipal 为 Principal 的认证对象。
     *
     * @param source 已验证 JWT
     * @return 已认证 Token
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        var principal = principalConverter.convert(source);
        var authorities = principal.authorities().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, source, authorities);
    }
}
