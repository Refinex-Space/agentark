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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配 OIDC/JWK JWT Decoder、Issuer/Audience Validator 与方法安全基础，不定义 IAM 业务。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkSecurityProperties.class)
@ConditionalOnClass(JwtDecoder.class)
@ConditionalOnProperty(
    prefix = "agentark.foundation.security",
    name = "enabled",
    havingValue = "true")
public class AgentArkSecurityAutoConfiguration {

    /**
     * 创建安全基础自动配置。
     */
    public AgentArkSecurityAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建同时校验时间、Issuer 和 Audience 的 JWT Decoder。
     *
     * @param properties 安全配置属性
     * @return 支持 JWK 缓存和 Key Rotation 的 Nimbus Decoder
     * @throws IllegalStateException 当 Issuer/JWK/Audience 配置不完整时抛出
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(AgentArkSecurityProperties properties) {
        if (properties.getAudiences().isEmpty()) {
            throw new IllegalStateException("security audiences must be configured");
        }
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder;
        if (properties.getJwkSetUri() != null) {
            builder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri().toString());
        } else if (properties.getIssuerUri() != null) {
            builder = NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri().toString());
        } else {
            throw new IllegalStateException("issuerUri or jwkSetUri must be configured");
        }
        builder.jwsAlgorithms(algorithms -> {
            algorithms.clear();
            properties.getAllowedJwsAlgorithms().stream()
                .map(SignatureAlgorithm::valueOf)
                .forEach(algorithms::add);
        });
        NimbusJwtDecoder decoder = builder.build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(
            properties.getIssuerUri() == null
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(properties.getIssuerUri().toString()));
        validators.add(new AudienceValidator(properties.getAudiences()));
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
        return decoder;
    }

    /**
     * 创建 JWT 到协议中立 AgentArk Principal 的严格转换器。
     *
     * @param properties 安全配置属性
     * @return 校验租户层级、服务身份和候选权限的转换器
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentArkJwtPrincipalConverter agentArkJwtPrincipalConverter(
        AgentArkSecurityProperties properties) {
        return new AgentArkJwtPrincipalConverter(properties);
    }

    /**
     * 仅在安全 Starter 启用时打开 Spring Method Security，不声明任何具体权限规则。
     *
     * @author refinex
     */
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    public static class MethodSecurityConfiguration {

        /**
         * 创建无状态的方法安全配置。
         */
        public MethodSecurityConfiguration() {
            // 注解驱动配置不持有业务权限数据。
        }
    }
}
