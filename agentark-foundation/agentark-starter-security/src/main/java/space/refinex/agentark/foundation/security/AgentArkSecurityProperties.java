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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 定义 Resource Server 的 OIDC/JWK、Audience 与声明名称，不包含任何 Secret。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.security")
public class AgentArkSecurityProperties {

    /**
     * 是否启用 JWT Decoder 和方法安全基线；默认关闭以避免意外开放错误配置。
     */
    private boolean enabled;

    /**
     * OIDC Issuer URI，用于校验 {@code iss} 并支持发现 JWK。
     */
    private URI issuerUri;

    /**
     * 显式 JWK Set URI，优先于 OIDC Discovery。
     */
    private URI jwkSetUri;

    /**
     * JWT 必须至少匹配一个值的 Audience 白名单。
     */
    private Set<String> audiences = new LinkedHashSet<>();

    /**
     * JWT 允许的非对称 JWS 算法白名单；默认只接受 RS256。
     */
    private Set<String> allowedJwsAlgorithms = new LinkedHashSet<>(Set.of("RS256"));

    /**
     * JWT 内组织标识的 Claim 名称。
     */
    private String organizationClaim = "org_id";

    /**
     * JWT 内项目标识的 Claim 名称。
     */
    private String projectClaim = "project_id";

    /**
     * JWT 内环境标识的 Claim 名称。
     */
    private String environmentClaim = "environment_id";

    /**
     * JWT 内主体类型的 Claim 名称；缺失时按交互式用户处理。
     */
    private String principalTypeClaim = "principal_type";

    /**
     * 服务 JWT 内稳定服务标识的 Claim 名称。
     */
    private String serviceIdClaim = "service_id";

    /**
     * JWT 内候选权限集合的 Claim 名称。
     */
    private String authoritiesClaim = "scope";

    /**
     * 返回安全自动配置是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置安全自动配置启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 OIDC Issuer URI。
     *
     * @return 可为空的 Issuer URI
     */
    public URI getIssuerUri() {
        return issuerUri;
    }

    /**
     * 设置 OIDC Issuer URI。
     *
     * @param issuerUri HTTPS Issuer URI
     */
    public void setIssuerUri(URI issuerUri) {
        this.issuerUri = requireHttps(issuerUri, "issuerUri");
    }

    /**
     * 返回显式 JWK Set URI。
     *
     * @return 可为空的 JWK Set URI
     */
    public URI getJwkSetUri() {
        return jwkSetUri;
    }

    /**
     * 设置显式 JWK Set URI。
     *
     * @param jwkSetUri HTTPS JWK Set URI
     */
    public void setJwkSetUri(URI jwkSetUri) {
        this.jwkSetUri = requireHttps(jwkSetUri, "jwkSetUri");
    }

    /**
     * 返回 Audience 白名单的防御性副本。
     *
     * @return Audience 集合
     */
    public Set<String> getAudiences() {
        return Set.copyOf(audiences);
    }

    /**
     * 设置 JWT Audience 白名单。
     *
     * @param audiences 非空 Audience 集合
     */
    public void setAudiences(Set<String> audiences) {
        this.audiences =
            new LinkedHashSet<>(
                java.util.Objects.requireNonNull(audiences, "audiences must not be null"));
    }

    /**
     * 返回允许的 JWS 算法白名单。
     *
     * @return 非空非对称算法集合
     */
    public Set<String> getAllowedJwsAlgorithms() {
        return Set.copyOf(allowedJwsAlgorithms);
    }

    /**
     * 设置允许的 JWS 算法白名单；拒绝对称算法和未注册算法。
     *
     * @param allowedJwsAlgorithms 非空算法集合
     */
    public void setAllowedJwsAlgorithms(Set<String> allowedJwsAlgorithms) {
        var checked = new LinkedHashSet<>(java.util.Objects.requireNonNull(
            allowedJwsAlgorithms, "allowedJwsAlgorithms must not be null"));
        if (checked.isEmpty()
            || checked.stream().anyMatch(value -> !value.matches("(RS|PS|ES)(256|384|512)"))) {
            throw new IllegalArgumentException(
                "allowedJwsAlgorithms must contain supported asymmetric algorithms");
        }
        this.allowedJwsAlgorithms = checked;
    }

    /**
     * 返回组织 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getOrganizationClaim() {
        return organizationClaim;
    }

    /**
     * 设置组织 Claim 名称。
     *
     * @param organizationClaim 非空 Claim 名称
     */
    public void setOrganizationClaim(String organizationClaim) {
        this.organizationClaim = requireClaim(organizationClaim);
    }

    /**
     * 返回项目 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getProjectClaim() {
        return projectClaim;
    }

    /**
     * 设置项目 Claim 名称。
     *
     * @param projectClaim 非空 Claim 名称
     */
    public void setProjectClaim(String projectClaim) {
        this.projectClaim = requireClaim(projectClaim);
    }

    /**
     * 返回环境 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getEnvironmentClaim() {
        return environmentClaim;
    }

    /**
     * 设置环境 Claim 名称。
     *
     * @param environmentClaim 非空 Claim 名称
     */
    public void setEnvironmentClaim(String environmentClaim) {
        this.environmentClaim = requireClaim(environmentClaim);
    }

    /**
     * 返回主体类型 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getPrincipalTypeClaim() {
        return principalTypeClaim;
    }

    /**
     * 设置主体类型 Claim 名称。
     *
     * @param principalTypeClaim 非空 Claim 名称
     */
    public void setPrincipalTypeClaim(String principalTypeClaim) {
        this.principalTypeClaim = requireClaim(principalTypeClaim);
    }

    /**
     * 返回服务标识 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getServiceIdClaim() {
        return serviceIdClaim;
    }

    /**
     * 设置服务标识 Claim 名称。
     *
     * @param serviceIdClaim 非空 Claim 名称
     */
    public void setServiceIdClaim(String serviceIdClaim) {
        this.serviceIdClaim = requireClaim(serviceIdClaim);
    }

    /**
     * 返回候选权限 Claim 名称。
     *
     * @return Claim 名称
     */
    public String getAuthoritiesClaim() {
        return authoritiesClaim;
    }

    /**
     * 设置候选权限 Claim 名称。
     *
     * @param authoritiesClaim 非空 Claim 名称
     */
    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = requireClaim(authoritiesClaim);
    }

    /**
     * 校验安全配置只允许 HTTPS URI。
     *
     * @param uri  待校验 URI，可为空
     * @param name 配置字段名称
     * @return 原 URI 或 {@code null}
     * @throws IllegalArgumentException 当 URI 不是绝对 HTTPS 地址时抛出
     */
    private URI requireHttps(URI uri, String name) {
        if (uri != null && (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTPS URI");
        }
        return uri;
    }

    /**
     * 校验 JWT Claim 名称。
     *
     * @param claim 待校验 Claim 名称
     * @return 原 Claim 名称
     * @throws IllegalArgumentException 当名称格式不合法时抛出
     */
    private String requireClaim(String claim) {
        if (claim == null || !claim.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("claim name is invalid");
        }
        return claim;
    }
}
