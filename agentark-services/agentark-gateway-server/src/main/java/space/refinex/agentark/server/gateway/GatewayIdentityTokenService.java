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

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.LocalPrincipal;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 使用部署 RSA 私钥签发短期内部用户/服务 JWT，并公开不含私钥的 JWK Set。
 *
 * @author refinex
 */
public final class GatewayIdentityTokenService {

    /**
     * 内部 JWT Encoder。
     */
    private final JwtEncoder encoder;

    /**
     * 可公开 JWK Set JSON。
     */
    private final Map<String, Object> publicJwkSet;

    /**
     * Identity 配置。
     */
    private final GatewayIdentityProperties properties;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 从 PKCS#8 Secret 构建签名器。
     *
     * @param properties Identity 配置
     * @param clock      UTC 时钟
     */
    public GatewayIdentityTokenService(GatewayIdentityProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        RSAKey key = rsaKey(properties.getSigningPrivateKey(), properties.getSigningKeyId());
        JWKSource<SecurityContext> source = (selector, context) -> selector.select(new JWKSet(key));
        this.encoder = new NimbusJwtEncoder(source);
        this.publicJwkSet = Map.copyOf(new JWKSet(key.toPublicJWK()).toJSONObject());
    }

    /**
     * 为本地浏览器 Session 签发面向四平面的短期用户 Token。
     *
     * @param principal 已验证本地主体
     * @return RS256 JWT 字符串
     */
    public String issueUserToken(LocalPrincipal principal) {
        return issue(
            principal.id().toString(),
            "USER",
            principal.authorities(),
            Map.of("auth_version", principal.authVersion(), "preferred_username", principal.username()));
    }

    /**
     * 为 Gateway Identity Outbox 投影签发仅面向 Control 的服务 Token。
     *
     * @return RS256 服务 JWT 字符串
     */
    public String issueGatewayServiceToken() {
        return issue(
            "agentark-gateway",
            "SERVICE",
            Set.of("identity.projection.write"),
            Map.of("service_id", "agentark-gateway"));
    }

    /**
     * 返回只含公开参数的 JWK Set。
     */
    public Map<String, Object> publicJwkSet() {
        return publicJwkSet;
    }

    /**
     * 组合稳定 Claims 并执行 RS256 签名。
     */
    private String issue(
        String subject, String principalType, Set<String> authorities, Map<String, Object> extras) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(properties.getIssuer().toString())
            .subject(subject)
            .audience(List.of(
                "agentark-gateway", "agentark-control", "agentark-runtime", "agentark-scheduler"))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.getAccessTokenTtl()))
            .id(GatewayIdentityIds.generate().toString())
            .claim("principal_type", principalType)
            .claim("scope", String.join(" ", new TreeSet<>(authorities)));
        extras.forEach(claims::claim);
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId(properties.getSigningKeyId())
            .build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims.build())).getTokenValue();
    }

    /**
     * 解析 PEM 私钥并从 CRT 参数推导公开 RSA JWK。
     */
    private static RSAKey rsaKey(String pem, String keyId) {
        try {
            String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(
                    privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
        } catch (Exception exception) {
            throw new IllegalStateException("identity RSA private key is invalid", exception);
        }
    }
}
