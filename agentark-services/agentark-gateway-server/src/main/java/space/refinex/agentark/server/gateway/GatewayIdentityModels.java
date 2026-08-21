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

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 集中定义 Gateway 内置 Identity 的领域投影与安全结果，不暴露密码摘要。
 *
 * @author refinex
 */
public final class GatewayIdentityModels {

    /**
     * 禁止实例化模型容器。
     */
    private GatewayIdentityModels() {
    }

    /**
     * 账号生命周期状态。
     *
     * @author refinex
     */
    public enum AccountStatus {
        /**
         * 允许登录。
         */
        ACTIVE,
        /**
         * 暂停登录但保留账号事实。
         */
        SUSPENDED,
        /**
         * 永久禁用但不删除审计关联。
         */
        DISABLED
    }

    /**
     * 内置账号与当前凭据投影。
     *
     * @param id                     账号 UUIDv7
     * @param username               原始用户名
     * @param email                  可空邮箱
     * @param displayName            展示名称
     * @param status                 生命周期状态
     * @param passwordChangeRequired 是否必须改密
     * @param authVersion            认证版本
     * @param version                管理乐观锁版本
     * @param lastLoginAt            可空最近登录时间
     * @param passwordHash           Argon2id PHC 摘要，只在服务内部使用
     * @param temporaryPassword      当前密码是否临时
     * @param lockedUntil            可空锁定截止时间
     * @param authorities            平台权限集合
     * @author refinex
     */
    public record Account(
        UUID id,
        String username,
        String email,
        String displayName,
        AccountStatus status,
        boolean passwordChangeRequired,
        long authVersion,
        long version,
        Instant lastLoginAt,
        String passwordHash,
        boolean temporaryPassword,
        Instant lockedUntil,
        Set<String> authorities) {

        /**
         * 防御性复制平台权限。
         */
        public Account {
            authorities = Set.copyOf(authorities);
        }
    }

    /**
     * 保存到 Redis SecurityContext 的本地主体，不包含密码或摘要。
     *
     * @param id          账号 UUIDv7
     * @param username    用户名
     * @param displayName 展示名称
     * @param email       可空邮箱
     * @param authVersion 认证版本
     * @param authorities 平台权限
     * @author refinex
     */
    public record LocalPrincipal(
        UUID id,
        String username,
        String displayName,
        String email,
        long authVersion,
        Set<String> authorities) implements Serializable, Principal {

        /**
         * 防御性复制权限集合。
         */
        public LocalPrincipal {
            authorities = Set.copyOf(authorities);
        }

        /**
         * 返回 Spring Session 索引使用的稳定用户名。
         *
         * @return 本地用户名
         */
        @Override
        public String getName() {
            return username;
        }
    }

    /**
     * 登录结果类型。
     *
     * @author refinex
     */
    public enum LoginStatus {
        /**
         * 已取得完整会话。
         */
        AUTHENTICATED,
        /**
         * 凭据正确但必须先修改临时密码。
         */
        PASSWORD_CHANGE_REQUIRED
    }

    /**
     * 成功密码验证后的安全结果。
     *
     * @param status    登录状态
     * @param principal 不含凭据的本地主体
     * @author refinex
     */
    public record LoginResult(LoginStatus status, LocalPrincipal principal) {
    }

    /**
     * 账号管理安全视图。
     *
     * @param id                     账号 UUIDv7
     * @param username               用户名
     * @param email                  可空邮箱
     * @param displayName            展示名称
     * @param status                 状态
     * @param passwordChangeRequired 是否必须改密
     * @param lockedUntil            可空锁定截止
     * @param lastLoginAt            可空最近登录
     * @param authorities            平台权限
     * @param version                乐观锁版本
     * @author refinex
     */
    public record AccountView(
        String id,
        String username,
        String email,
        String displayName,
        String status,
        boolean passwordChangeRequired,
        Instant lockedUntil,
        Instant lastLoginAt,
        Set<String> authorities,
        long version) {

        /**
         * 防御性复制权限集合。
         */
        public AccountView {
            authorities = Set.copyOf(authorities);
        }
    }

    /**
     * 新账号及一次性临时密码结果。
     *
     * @param account           账号安全视图
     * @param temporaryPassword 只在首次成功响应交付的临时密码；幂等重放时为空
     * @author refinex
     */
    public record CreatedAccount(AccountView account, String temporaryPassword) {
    }

    /**
     * 待投递 Control 的非敏感 Identity Outbox。
     *
     * @param id          投递记录 UUIDv7 标识
     * @param aggregateId 账号 UUIDv7
     * @param eventType   稳定事件类型
     * @param payloadJson 非敏感投影 JSON
     * @param attempts    已尝试次数
     * @author refinex
     */
    public record OutboxItem(
        UUID id, UUID aggregateId, String eventType, String payloadJson, int attempts) {
    }

    /**
     * 身份安全事件只读视图。
     *
     * @param id           事件 UUIDv7
     * @param accountId    可空目标账号 UUIDv7
     * @param eventType    事件类型
     * @param result       SUCCESS、FAILURE 或 DENIED
     * @param actorSubject 可空操作者 Subject
     * @param detailCode   可空脱敏原因码
     * @param occurredAt   发生 UTC 时间
     * @author refinex
     */
    public record SecurityEventView(
        String id,
        String accountId,
        String eventType,
        String result,
        String actorSubject,
        String detailCode,
        Instant occurredAt) {
    }
}
