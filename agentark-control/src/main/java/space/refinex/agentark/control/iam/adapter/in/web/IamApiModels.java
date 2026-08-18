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

package space.refinex.agentark.control.iam.adapter.in.web;

import jakarta.validation.constraints.*;
import space.refinex.agentark.control.iam.application.CreatedApiKey;
import space.refinex.agentark.control.iam.domain.ApiKey;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * 集中定义 IAM Public API 的输入与安全输出模型，禁止 API Key 摘要进入响应。
 *
 * @author refinex
 */
public final class IamApiModels {

    /**
     * 禁止实例化纯 API 模型容器。
     */
    private IamApiModels() {
        throw new IllegalStateException("API model container must not be instantiated");
    }

    /**
     * 创建组织请求。
     *
     * @param slug 全局唯一组织 Slug
     * @param name 组织展示名称
     * @author refinex
     */
    public record CreateOrganizationRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}[a-z0-9]") String slug,
        @NotBlank @Size(max = 128) String name) {

        /**
         * 校验 Bean Validation 注解之前的空容器不变量。
         */
        public CreateOrganizationRequest {
            // 具体字符与长度规则由 Bean Validation 统一返回字段错误。
        }
    }

    /**
     * 创建项目请求。
     *
     * @param slug 组织内唯一项目 Slug
     * @param name 项目展示名称
     * @author refinex
     */
    public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}[a-z0-9]") String slug,
        @NotBlank @Size(max = 128) String name) {

        /**
         * 创建项目请求模型。
         */
        public CreateProjectRequest {
            // 具体字符与长度规则由 Bean Validation 处理。
        }
    }

    /**
     * 创建环境请求。
     *
     * @param key  项目内唯一环境 Key
     * @param name 环境展示名称
     * @author refinex
     */
    public record CreateEnvironmentRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotBlank @Size(max = 128) String name) {

        /**
         * 创建环境请求模型。
         */
        public CreateEnvironmentRequest {
            // 具体字符与长度规则由 Bean Validation 处理。
        }
    }

    /**
     * 创建项目成员请求。
     *
     * @param principalKind USER 或 SERVICE_ACCOUNT
     * @param principalId   主体 UUIDv7
     * @author refinex
     */
    public record CreateMembershipRequest(
        @NotBlank String principalKind,
        @NotBlank String principalId) {

        /**
         * 创建成员关系请求模型。
         */
        public CreateMembershipRequest {
            // 枚举与强类型标识在应用入口解析并返回稳定错误。
        }
    }

    /**
     * 创建自定义角色请求。
     *
     * @param key         项目内唯一角色 Key
     * @param name        角色展示名称
     * @param permissions 非空权限键集合
     * @author refinex
     */
    public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{1,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @NotEmpty @Size(max = 64) Set<@NotBlank String> permissions) {

        /**
         * 防御性复制权限集合。
         */
        public CreateRoleRequest {
            permissions = permissions == null ? null : Set.copyOf(permissions);
        }
    }

    /**
     * 创建 Scope-aware 角色绑定请求。
     *
     * @param roleId        角色 UUIDv7
     * @param principalKind USER 或 SERVICE_ACCOUNT
     * @param principalId   被授权主体 UUIDv7
     * @param scopeType     PROJECT 或 ENVIRONMENT
     * @param scopeId       项目或环境 UUIDv7
     * @author refinex
     */
    public record CreateRoleBindingRequest(
        @NotBlank String roleId,
        @NotBlank String principalKind,
        @NotBlank String principalId,
        @NotBlank String scopeType,
        @NotBlank String scopeId) {

        /**
         * 创建角色绑定请求模型。
         */
        public CreateRoleBindingRequest {
            // 交叉 Scope 约束由应用服务结合数据库归属校验。
        }
    }

    /**
     * 创建服务账号请求。
     *
     * @param name 项目内唯一稳定名称
     * @author refinex
     */
    public record CreateServiceAccountRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}[a-z0-9]") String name) {

        /**
         * 创建服务账号请求模型。
         */
        public CreateServiceAccountRequest {
            // 名称规则由 Bean Validation 处理。
        }
    }

    /**
     * 创建 API Key 请求。
     *
     * @param serviceAccountId 服务账号 UUIDv7
     * @param name             Key 展示名称
     * @param scopes           相对服务账号权限收窄后的非空 Scope
     * @param expiresAt        可选未来到期时刻
     * @author refinex
     */
    public record CreateApiKeyRequest(
        @NotBlank String serviceAccountId,
        @NotBlank @Size(max = 128) String name,
        @NotEmpty @Size(max = 64) Set<@NotBlank String> scopes,
        Optional<@Future Instant> expiresAt) {

        /**
         * 防御性复制 Scope 并拒绝空 Optional 容器。
         */
        public CreateApiKeyRequest {
            scopes = scopes == null ? null : Set.copyOf(scopes);
            expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        }
    }

    /**
     * 吊销 API Key 请求。
     *
     * @param expectedVersion 调用方读取的乐观锁版本
     * @author refinex
     */
    public record RevokeApiKeyRequest(@Min(0) long expectedVersion) {

        /**
         * 创建吊销请求模型。
         */
        public RevokeApiKeyRequest {
            // 非负校验由 Bean Validation 统一返回。
        }
    }

    /**
     * API Key 非秘密元数据响应。
     *
     * @param id               API Key UUIDv7 标识
     * @param serviceAccountId 服务账号 UUIDv7
     * @param name             展示名称
     * @param prefix           可公开识别前缀
     * @param scopes           收窄权限集合
     * @param expiresAt        可选到期时刻
     * @param revokedAt        可选吊销时刻
     * @param version          乐观锁版本
     * @param createdAt        创建时刻
     * @author refinex
     */
    public record ApiKeyView(
        String id,
        String serviceAccountId,
        String name,
        String prefix,
        Set<String> scopes,
        Optional<Instant> expiresAt,
        Optional<Instant> revokedAt,
        long version,
        Instant createdAt) {

        /**
         * 防御性复制 Scope 与 Optional。
         */
        public ApiKeyView {
            scopes = Set.copyOf(scopes);
            expiresAt = java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            revokedAt = java.util.Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        }

        /**
         * 从只含摘要的领域对象创建安全视图。
         *
         * @param apiKey API Key 非明文领域对象
         * @return 不包含摘要字段的安全视图
         */
        public static ApiKeyView from(ApiKey apiKey) {
            return new ApiKeyView(
                apiKey.id().asString(),
                apiKey.serviceAccountId().asString(),
                apiKey.name(),
                apiKey.prefix(),
                apiKey.scopes(),
                apiKey.expiresAt(),
                apiKey.revokedAt(),
                apiKey.version(),
                apiKey.createdAt());
        }
    }

    /**
     * API Key 创建时唯一一次包含完整明文的响应。
     *
     * @param apiKey    非秘密元数据
     * @param plaintext 完整 API Key；调用方必须立即安全保存
     * @author refinex
     */
    public record CreatedApiKeyResponse(ApiKeyView apiKey, String plaintext) {

        /**
         * 校验单次交付响应不变量。
         */
        public CreatedApiKeyResponse {
            java.util.Objects.requireNonNull(apiKey, "apiKey must not be null");
            if (plaintext == null || plaintext.isBlank()) {
                throw new IllegalArgumentException("plaintext must not be blank");
            }
        }

        /**
         * 转换应用服务的单次交付对象。
         *
         * @param created 已创建 API Key
         * @return 安全单次交付响应
         */
        public static CreatedApiKeyResponse from(CreatedApiKey created) {
            return new CreatedApiKeyResponse(ApiKeyView.from(created.metadata()), created.plaintext());
        }
    }
}
