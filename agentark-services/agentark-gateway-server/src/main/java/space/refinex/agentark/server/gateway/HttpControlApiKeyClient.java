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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Optional;
import java.util.Set;

/**
 * 通过版本化 Control 内部端点验证 API Key，且不把凭据写入响应、日志或缓存。
 *
 * @author refinex
 */
public final class HttpControlApiKeyClient implements ControlApiKeyClient {

    /**
     * Control 内部 API 客户端。
     */
    private final WebClient webClient;

    /**
     * 创建 Control API Key 客户端。
     *
     * @param webClient 已绑定 Control 基础地址的客户端
     */
    public HttpControlApiKeyClient(WebClient webClient) {
        this.webClient = java.util.Objects.requireNonNull(webClient, "webClient must not be null");
    }

    /**
     * 将原始凭据只发送给 Control；401 或 403 表示凭据无效，其他失败保留为服务错误。
     *
     * @param credential 完整 API Key
     * @return Control 验证得到的主体或空
     */
    @Override
    public Mono<Optional<AgentArkPrincipal>> verifyRemotely(String credential) {
        java.util.Objects.requireNonNull(credential, "credential must not be null");
        return webClient.post()
            .uri("/internal/v1/auth/api-keys:verify")
            .header(HttpHeaders.AUTHORIZATION, "ApiKey " + credential)
            .exchangeToMono(response -> {
                HttpStatusCode status = response.statusCode();
                if (status.is2xxSuccessful()) {
                    return response.bodyToMono(ApiKeyVerificationResponse.class)
                        .map(this::toPrincipal)
                        .map(Optional::of);
                }
                if (status.value() == 401 || status.value() == 403) {
                    return response.releaseBody().thenReturn(Optional.empty());
                }
                return response.createException().flatMap(Mono::error);
            });
    }

    /**
     * 将语言中立内部响应转换为 Foundation Principal，并重新执行强类型标识校验。
     *
     * @param response Control 非秘密验证响应
     * @return API Key 主体
     */
    private AgentArkPrincipal toPrincipal(ApiKeyVerificationResponse response) {
        if (!PrincipalType.API_KEY.name().equals(response.principalType())) {
            throw new IllegalArgumentException("control API key principal type is invalid");
        }
        TenantSelection tenantSelection = new TenantSelection(
            OrganizationId.parse(response.organizationId()),
            Optional.of(ProjectId.parse(response.projectId())),
            Optional.empty());
        return new AgentArkPrincipal(
            response.issuer(),
            response.subject(),
            PrincipalType.API_KEY,
            response.authorities(),
            Optional.of(tenantSelection),
            Optional.empty());
    }

    /**
     * 表示 Control API Key 内部验证响应，不包含明文、摘要或凭据元数据。
     *
     * @param issuer         凭据签发方
     * @param subject        服务账号主体
     * @param principalType  主体类型
     * @param authorities    收窄后的权限集合
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @author refinex
     */
    public record ApiKeyVerificationResponse(
        String issuer,
        String subject,
        String principalType,
        Set<String> authorities,
        String organizationId,
        String projectId) {

        /**
         * 校验响应必需字段并防御性复制权限集合。
         *
         * @param issuer         凭据签发方
         * @param subject        服务账号主体
         * @param principalType  主体类型
         * @param authorities    权限集合
         * @param organizationId 组织标识
         * @param projectId      项目标识
         */
        public ApiKeyVerificationResponse {
            issuer = requireText(issuer, "issuer");
            subject = requireText(subject, "subject");
            principalType = requireText(principalType, "principalType");
            authorities = Set.copyOf(java.util.Objects.requireNonNull(
                authorities, "authorities must not be null"));
            organizationId = requireText(organizationId, "organizationId");
            projectId = requireText(projectId, "projectId");
        }

        /**
         * 校验内部响应文本字段。
         *
         * @param value 字段值
         * @param name  字段名
         * @return 原始非空白值
         */
        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
