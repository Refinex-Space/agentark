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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

/**
 * 验证 Security Starter 默认关闭、显式 OIDC/JWK 启用和 Audience 防重放行为。
 *
 * @author refinex
 */
class AgentArkSecurityAutoConfigurationTest {

  /** 安全自动配置测试运行器。 */
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AgentArkSecurityAutoConfiguration.class));

  /** 验证未显式启用时不创建 JWT Decoder 或方法安全基础。 */
  @Test
  void remainsDisabledByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
  }

  /** 验证 HTTPS JWK 和 Audience 配置可创建 Decoder 且不在启动时访问网络。 */
  @Test
  void configuresJwtDecoderWhenExplicitlyEnabled() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.security.enabled=true",
            "agentark.foundation.security.jwk-set-uri=https://id.example.test/.well-known/jwks.json",
            "agentark.foundation.security.audiences[0]=agentark-control")
        .run(
            context -> {
              assertThat(context).hasSingleBean(JwtDecoder.class);
              assertThat(context).hasSingleBean(AgentArkSecurityProperties.class);
              assertThat(context).hasSingleBean(AgentArkJwtPrincipalConverter.class);
            });
  }

  /** 验证启用但缺少 Audience 时启动失败，不能退化为接受任意服务 Token。 */
  @Test
  void rejectsEnabledConfigurationWithoutAudience() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.security.enabled=true",
            "agentark.foundation.security.jwk-set-uri=https://id.example.test/.well-known/jwks.json")
        .run(context -> assertThat(context).hasFailed());
  }

  /** 验证对称或未知签名算法无法进入 Resource Server 白名单。 */
  @Test
  void rejectsUnsafeSignatureAlgorithm() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.security.enabled=true",
            "agentark.foundation.security.jwk-set-uri=https://id.example.test/.well-known/jwks.json",
            "agentark.foundation.security.audiences[0]=agentark-gateway",
            "agentark.foundation.security.allowed-jws-algorithms[0]=HS256")
        .run(context -> assertThat(context).hasFailed());
  }

  /** 验证默认拒绝明文 HTTP 身份端点，避免本地放宽意外进入生产。 */
  @Test
  void rejectsHttpIdentityEndpointByDefault() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.security.enabled=true",
            "agentark.foundation.security.jwk-set-uri=http://identity:8080/realms/agentark/protocol/openid-connect/certs",
            "agentark.foundation.security.audiences[0]=agentark-gateway")
        .run(context -> assertThat(context).hasFailed());
  }

  /** 验证受控本地 Profile 可显式允许 Compose 内 HTTP JWK 端点。 */
  @Test
  void allowsHttpIdentityEndpointOnlyWhenExplicitlyEnabled() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.security.enabled=true",
            "agentark.foundation.security.insecure-http-enabled=true",
            "agentark.foundation.security.jwk-set-uri=http://identity:8080/realms/agentark/protocol/openid-connect/certs",
            "agentark.foundation.security.audiences[0]=agentark-gateway")
        .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
  }

  /** 验证 Audience Validator 接受白名单交集并拒绝跨服务 Audience。 */
  @Test
  void validatesAudienceWhitelist() {
    AudienceValidator validator = new AudienceValidator(Set.of("agentark-runtime"));
    Jwt accepted = jwt(List.of("agentark-runtime"));
    Jwt rejected = jwt(List.of("agentark-control"));

    assertThat(validator.validate(accepted).hasErrors()).isFalse();
    assertThat(validator.validate(rejected).hasErrors()).isTrue();
  }

  /** 验证受信 JWT 被转换为包含强类型租户选择和去重权限的用户主体。 */
  @Test
  void convertsJwtClaimsToTenantPrincipal() {
    OrganizationId organizationId = OrganizationId.generate();
    ProjectId projectId = ProjectId.generate();
    EnvironmentId environmentId = EnvironmentId.generate();
    AgentArkJwtPrincipalConverter converter =
        new AgentArkJwtPrincipalConverter(new AgentArkSecurityProperties());
    Jwt jwt =
        jwt(
            Map.of(
                "sub",
                "user-1",
                "aud",
                List.of("agentark-control"),
                "org_id",
                organizationId.asString(),
                "project_id",
                projectId.asString(),
                "environment_id",
                environmentId.asString(),
                "scope",
                "agent:read agent:write"));

    AgentArkPrincipal principal = converter.convert(jwt);

    assertThat(principal.type()).isEqualTo(PrincipalType.USER);
    assertThat(principal.authorities()).containsExactlyInAnyOrder("agent:read", "agent:write");
    assertThat(principal.tenantSelection())
        .contains(
            new TenantSelection(
                organizationId,
                java.util.Optional.of(projectId),
                java.util.Optional.of(environmentId)));
  }

  /** 验证项目 Claim 缺少组织 Claim 时转换失败，不能形成不完整租户上下文。 */
  @Test
  void rejectsProjectClaimWithoutOrganization() {
    AgentArkJwtPrincipalConverter converter =
        new AgentArkJwtPrincipalConverter(new AgentArkSecurityProperties());
    Jwt jwt =
        jwt(
            Map.of(
                "sub", "user-1",
                "aud", List.of("agentark-control"),
                "project_id", ProjectId.generate().asString()));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires organization");
  }

  /**
   * 创建仅用于 Audience 测试且时间有效的 JWT。
   *
   * @param audience Audience 列表
   * @return 测试 JWT
   */
  private Jwt jwt(List<String> audience) {
    return jwt(Map.of("sub", "subject", "aud", audience));
  }

  /**
   * 创建带自定义受信声明且时间有效的 JWT。
   *
   * @param claims JWT Claim
   * @return 测试 JWT
   */
  private Jwt jwt(Map<String, Object> claims) {
    Instant now = Instant.now();
    Map<String, Object> completeClaims = new java.util.HashMap<>(claims);
    completeClaims.putIfAbsent("iss", "https://issuer.example.test");
    return new Jwt(
        "test-token",
        now.minusSeconds(1),
        now.plusSeconds(60),
        Map.of("alg", "none"),
        completeClaims);
  }
}
