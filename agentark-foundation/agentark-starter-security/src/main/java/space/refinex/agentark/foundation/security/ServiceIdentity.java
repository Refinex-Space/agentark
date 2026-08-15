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

import java.util.Set;

/**
 * 表示通过短期凭据或 mTLS 认证的内部服务身份及其受限 Audience。
 *
 * @param serviceId 服务稳定标识，不是共享 Token
 * @param audiences 允许访问的不可变 Audience 集合
 * @author refinex
 */
public record ServiceIdentity(String serviceId, Set<String> audiences) {

    /**
     * 校验并创建服务身份。
     *
     * @param serviceId 服务稳定标识
     * @param audiences 非空 Audience 集合
     * @throws IllegalArgumentException 当服务标识格式不合法或 Audience 为空时抛出
     * @throws NullPointerException     当 Audience 集合为 {@code null} 时抛出
     */
    public ServiceIdentity {
        if (serviceId == null || !serviceId.matches("[a-z][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException("serviceId must be a stable lowercase identifier");
        }
        audiences =
            Set.copyOf(java.util.Objects.requireNonNull(audiences, "audiences must not be null"));
        if (audiences.isEmpty() || audiences.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("audiences must contain non-blank values");
        }
    }
}
