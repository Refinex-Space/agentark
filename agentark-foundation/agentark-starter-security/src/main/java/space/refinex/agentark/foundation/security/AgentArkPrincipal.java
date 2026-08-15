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

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 表示协议中立的已认证主体，不拥有 User、Role 或 Membership 生命周期。
 *
 * @param subject         身份提供方内稳定且非空的 Subject
 * @param type            主体类型
 * @param authorities     已认证 Token 携带的候选权限声明
 * @param tenantSelection 可选租户选择，仍需资源级授权
 * @param serviceIdentity 服务主体的身份明细；非服务主体必须为空
 * @author refinex
 */
public record AgentArkPrincipal(
    String subject,
    PrincipalType type,
    Set<String> authorities,
    Optional<TenantSelection> tenantSelection,
    Optional<ServiceIdentity> serviceIdentity)
    implements Principal {

    /**
     * 校验主体不变量并防御性复制集合。
     *
     * @param subject         Subject
     * @param type            主体类型
     * @param authorities     候选权限声明
     * @param tenantSelection 可选租户选择
     * @param serviceIdentity 可选服务身份
     * @throws IllegalArgumentException 当 Subject 为空、权限为空字符串或主体类型不一致时抛出
     * @throws NullPointerException     当必需参数或 Optional 容器为 {@code null} 时抛出
     */
    public AgentArkPrincipal {
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new IllegalArgumentException("subject must contain 1 to 255 characters");
        }
        Objects.requireNonNull(type, "type must not be null");
        authorities = Set.copyOf(Objects.requireNonNull(authorities, "authorities must not be null"));
        tenantSelection = Objects.requireNonNull(tenantSelection, "tenantSelection must not be null");
        serviceIdentity = Objects.requireNonNull(serviceIdentity, "serviceIdentity must not be null");
        if (authorities.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("authorities must not contain blank values");
        }
        if ((type == PrincipalType.SERVICE) != serviceIdentity.isPresent()) {
            throw new IllegalArgumentException("service identity must match principal type");
        }
    }

    /**
     * 返回 Java Security 使用的稳定主体名称。
     *
     * @return 与 Subject 相同的主体名称
     */
    @Override
    public String getName() {
        return subject;
    }
}
