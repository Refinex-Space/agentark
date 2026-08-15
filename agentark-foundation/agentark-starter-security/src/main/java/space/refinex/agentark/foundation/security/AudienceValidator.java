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

package space.refinex.agentark.foundation.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

/**
 * 要求 JWT 至少包含一个服务端配置的 Audience，防止跨服务重放 Token。
 *
 * @author refinex
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * 允许的 Audience 白名单。
     */
    private final Set<String> audiences;

    /**
     * 创建 Audience 校验器。
     *
     * @param audiences 非空 Audience 白名单
     * @throws IllegalArgumentException 当白名单为空或包含空值时抛出
     */
    public AudienceValidator(Set<String> audiences) {
        this.audiences =
            Set.copyOf(java.util.Objects.requireNonNull(audiences, "audiences must not be null"));
        if (this.audiences.isEmpty() || this.audiences.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("audiences must contain non-blank values");
        }
    }

    /**
     * 校验 JWT Audience 与白名单是否相交。
     *
     * @param token 已完成签名解析的 JWT
     * @return 成功或稳定的 invalid_token 错误
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        boolean accepted = token.getAudience().stream().anyMatch(audiences::contains);
        if (accepted) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "JWT audience is not allowed", null));
    }
}
