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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import space.refinex.agentark.foundation.security.AudienceValidator;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 使用真实 Gateway 和临时 RSA JWT Decoder 启动浏览器 E2E，配置不进入生产 JAR。
 *
 * @author refinex
 */
public final class GatewayE2eApplication {

    /** 禁止实例化测试启动器。 */
    private GatewayE2eApplication() {
    }

    /**
     * 启动带 Test Classpath 安全配置的 Gateway。
     *
     * @param args Spring Boot 参数
     */
    public static void main(String[] args) {
        SpringApplication.from(AgentArkGatewayApplication::main)
            .with(E2eSecurityConfiguration.class)
            .run(args);
    }

    /**
     * 定义 Gateway E2E JWT Decoder。
     *
     * @author refinex
     */
    @TestConfiguration(proxyBeanMethods = false)
    public static class E2eSecurityConfiguration {

        /**
         * 创建面向 Gateway Audience 的真实 RS256 Decoder。
         *
         * @return E2E JWT Decoder
         */
        @Bean
        @Primary
        public JwtDecoder e2eJwtDecoder() {
            return decoder("agentark-gateway");
        }
    }

    /**
     * 从临时 X.509 RSA 公钥创建 Decoder。
     *
     * @param audience 当前服务 Audience
     * @return 带 Issuer 与 Audience Validator 的 Decoder
     */
    private static JwtDecoder decoder(String audience) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required("AGENTARK_E2E_PUBLIC_KEY"));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                JwtValidators.createDefaultWithIssuer(required("AGENTARK_E2E_ISSUER")),
                new AudienceValidator(java.util.Set.of(audience))));
            return decoder;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("E2E RSA public key is invalid", exception);
        }
    }

    /**
     * 读取必需且非空的 E2E 环境变量，不输出其内容。
     *
     * @param name 环境变量名称
     * @return 非空值
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for E2E");
        }
        return value;
    }
}
