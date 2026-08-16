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

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.*;

/**
 * 将已验证 JWT 的受信声明转换为协议中立主体，并严格校验租户层级和服务身份。
 *
 * @author refinex
 */
public final class AgentArkJwtPrincipalConverter implements Converter<Jwt, AgentArkPrincipal> {

    /**
     * 声明名称和转换规则配置。
     */
    private final AgentArkSecurityProperties properties;

    /**
     * 创建 JWT 主体转换器。
     *
     * @param properties 声明名称配置
     */
    public AgentArkJwtPrincipalConverter(AgentArkSecurityProperties properties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 将已通过签名、Issuer、时间和 Audience 校验的 JWT 转换为 AgentArk 主体。
     *
     * @param source 已验证 JWT
     * @return 不拥有 IAM 生命周期的 AgentArk Principal
     * @throws IllegalArgumentException 当 Subject、主体类型、租户层级或声明类型不合法时抛出
     */
    @Override
    public AgentArkPrincipal convert(Jwt source) {
        java.util.Objects.requireNonNull(source, "source must not be null");
        PrincipalType type = principalType(source);
        Optional<TenantSelection> tenantSelection = tenantSelection(source);
        Optional<ServiceIdentity> serviceIdentity =
            type == PrincipalType.SERVICE
                ? Optional.of(
                new ServiceIdentity(
                    requiredStringClaim(source, properties.getServiceIdClaim()),
                    Set.copyOf(source.getAudience())))
                : Optional.empty();
        return new AgentArkPrincipal(
            requiredIssuer(source),
            source.getSubject(),
            type,
            authorities(source),
            tenantSelection,
            serviceIdentity);
    }

    /**
     * 读取已验证 JWT 的 Issuer，并拒绝缺失或过长值，避免跨身份源 Subject 碰撞。
     *
     * @param jwt 已验证 JWT
     * @return 规范 Issuer 字符串
     * @throws IllegalArgumentException 当 Issuer 缺失、为空或超过持久化上限时抛出
     */
    private String requiredIssuer(Jwt jwt) {
        if (jwt.getIssuer() == null) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        String issuer = jwt.getIssuer().toString();
        if (issuer.isBlank() || issuer.length() > 255) {
            throw new IllegalArgumentException("JWT issuer must contain 1 to 255 characters");
        }
        return issuer;
    }

    /**
     * 解析主体类型，缺失 Claim 时按交互式用户处理。
     *
     * @param jwt 已验证 JWT
     * @return 主体类型
     */
    private PrincipalType principalType(Jwt jwt) {
        String claim = optionalStringClaim(jwt, properties.getPrincipalTypeClaim()).orElse("USER");
        try {
            return PrincipalType.valueOf(claim.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("JWT principal type is invalid", error);
        }
    }

    /**
     * 解析租户层级；项目或环境 Claim 不允许脱离组织 Claim 单独出现。
     *
     * @param jwt 已验证 JWT
     * @return 可选租户选择
     */
    private Optional<TenantSelection> tenantSelection(Jwt jwt) {
        Optional<String> organization = optionalStringClaim(jwt, properties.getOrganizationClaim());
        Optional<String> project = optionalStringClaim(jwt, properties.getProjectClaim());
        Optional<String> environment = optionalStringClaim(jwt, properties.getEnvironmentClaim());
        if (organization.isEmpty()) {
            if (project.isPresent() || environment.isPresent()) {
                throw new IllegalArgumentException("JWT project or environment requires organization");
            }
            return Optional.empty();
        }
        return Optional.of(
            new TenantSelection(
                OrganizationId.parse(organization.orElseThrow()),
                project.map(ProjectId::parse),
                environment.map(EnvironmentId::parse)));
    }

    /**
     * 解析字符串或字符串集合形式的候选权限 Claim。
     *
     * @param jwt 已验证 JWT
     * @return 去重后的候选权限集合
     */
    private Set<String> authorities(Jwt jwt) {
        Object claim = jwt.getClaim(properties.getAuthoritiesClaim());
        if (claim == null) {
            return Set.of();
        }
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        if (claim instanceof String value) {
            Arrays.stream(value.trim().split("\\s+"))
                .filter(authority -> !authority.isBlank())
                .forEach(authorities::add);
        } else if (claim instanceof Collection<?> values) {
            values.forEach(
                value -> {
                    if (!(value instanceof String authority) || authority.isBlank()) {
                        throw new IllegalArgumentException("JWT authorities must contain strings");
                    }
                    authorities.add(authority);
                });
        } else {
            throw new IllegalArgumentException("JWT authorities claim must be a string or collection");
        }
        return Set.copyOf(authorities);
    }

    /**
     * 读取必需的非空字符串 Claim。
     *
     * @param jwt       已验证 JWT
     * @param claimName Claim 名称
     * @return 非空 Claim 值
     */
    private String requiredStringClaim(Jwt jwt, String claimName) {
        return optionalStringClaim(jwt, claimName)
            .orElseThrow(() -> new IllegalArgumentException("required JWT claim is missing"));
    }

    /**
     * 读取可选的非空字符串 Claim，并拒绝错误类型或空白值。
     *
     * @param jwt       已验证 JWT
     * @param claimName Claim 名称
     * @return 可选 Claim 值
     */
    private Optional<String> optionalStringClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (claim == null) {
            return Optional.empty();
        }
        if (!(claim instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("JWT claim must be a non-blank string");
        }
        return Optional.of(value);
    }
}
