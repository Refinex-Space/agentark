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

package space.refinex.agentark.control.iam.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * 汇总 IAM Mapper 使用的只读数据库行结构，领域对象由 Repository Adapter 显式转换。
 *
 * @author refinex
 */
public final class IamPersistenceRows {

    /**
     * 禁止实例化数据库行类型容器。
     */
    private IamPersistenceRows() {
    }

    /**
     * 表示 organization 查询行。
     *
     * @param id        主键 UUIDv7
     * @param slug      全局 Slug
     * @param name      展示名称
     * @param status    状态代码
     * @param version   乐观锁版本
     * @param createdAt 创建时刻
     * @param updatedAt 更新时间
     * @author refinex
     */
    public record OrganizationRow(UUID id, String slug, String name, String status, long version, Instant createdAt,
                                  Instant updatedAt) {
    }

    /**
     * 表示 project 查询行。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param slug           项目 Slug
     * @param name           展示名称
     * @param status         状态代码
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record ProjectRow(UUID id, UUID organizationId, String slug, String name, String status, long version,
                             Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 environment 查询行。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param key            环境 Key
     * @param name           展示名称
     * @param status         状态代码
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record EnvironmentRow(UUID id, UUID organizationId, UUID projectId, String key, String name, String status,
                                 long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 user_identity 查询行。
     *
     * @param id          主键 UUIDv7
     * @param issuer      外部 Issuer
     * @param subject     外部 Subject
     * @param displayName 可空展示名称
     * @param email       可空展示邮箱
     * @param status      状态代码
     * @param lastSeenAt  最近认证时刻
     * @param version     乐观锁版本
     * @param createdAt   创建时刻
     * @param updatedAt   更新时间
     * @author refinex
     */
    public record UserIdentityRow(UUID id, String issuer, String subject, String displayName, String email,
                                  String status,
                                  Instant lastSeenAt, long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 service_account 查询行。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param name           稳定名称
     * @param status         状态代码
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record ServiceAccountRow(UUID id, UUID organizationId, UUID projectId, String name, String status,
                                    long version,
                                    Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 membership 查询行。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param principalType  主体类型代码
     * @param principalId    主体 UUIDv7
     * @param status         状态代码
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record MembershipRow(UUID id, UUID organizationId, UUID projectId, String principalType, UUID principalId,
                                String status, long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 permission 查询行。
     *
     * @param id          主键 UUIDv7
     * @param key         权限键
     * @param description 中文说明
     * @param riskLevel   风险代码
     * @param createdAt   注册时刻
     * @author refinex
     */
    public record PermissionRow(UUID id, String key, String description, String riskLevel, Instant createdAt) {
    }

    /**
     * 表示 role 查询行，权限键由关联查询单独读取。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      可空项目 UUIDv7
     * @param key            角色键
     * @param name           展示名称
     * @param builtIn        内置标志
     * @param status         状态代码
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record RoleRow(UUID id, UUID organizationId, UUID projectId, String key, String name, boolean builtIn,
                          String status, long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * 表示 role_binding 查询行。
     *
     * @param id             主键 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      可空项目 UUIDv7
     * @param roleId         角色 UUIDv7
     * @param principalType  主体类型代码
     * @param principalId    主体 UUIDv7
     * @param scopeType      Scope 类型代码
     * @param scopeId        范围 UUIDv7
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record RoleBindingRow(UUID id, UUID organizationId, UUID projectId, UUID roleId, String principalType,
                                 UUID principalId, String scopeType, UUID scopeId, long version, Instant createdAt,
                                 Instant updatedAt) {
    }

    /**
     * 表示 api_key 查询行，Scope 由关联查询单独读取。
     *
     * @param id               主键 UUIDv7
     * @param organizationId   组织 UUIDv7
     * @param projectId        项目 UUIDv7
     * @param serviceAccountId 服务账号 UUIDv7
     * @param name             展示名称
     * @param prefix           公开前缀
     * @param digest           32 字节摘要
     * @param expiresAt        可空到期时刻
     * @param revokedAt        可空吊销时刻
     * @param version          乐观锁版本
     * @param createdAt        创建时刻
     * @param updatedAt        更新时间
     * @author refinex
     */
    public record ApiKeyRow(UUID id, UUID organizationId, UUID projectId, UUID serviceAccountId, String name,
                            String prefix, byte[] digest, Instant expiresAt, Instant revokedAt, long version,
                            Instant createdAt, Instant updatedAt) {
    }
}
