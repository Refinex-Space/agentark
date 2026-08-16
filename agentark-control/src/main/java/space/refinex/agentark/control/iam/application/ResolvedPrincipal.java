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

package space.refinex.agentark.control.iam.application;

import space.refinex.agentark.control.iam.domain.PrincipalKind;

import java.util.Objects;
import java.util.UUID;

/**
 * 表示已认证协议主体映射到 IAM 持久主体后的稳定引用。
 *
 * @param kind 主体类别
 * @param id   用户身份或服务账号 UUIDv7
 * @author refinex
 */
public record ResolvedPrincipal(PrincipalKind kind, UUID id) {

    /**
     * 校验持久主体引用。
     *
     * @param kind 主体类别
     * @param id   主体 UUIDv7
     */
    public ResolvedPrincipal {
        Objects.requireNonNull(kind, "kind must not be null");
        if (id == null || id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException("id must be an RFC 9562 UUIDv7");
        }
    }
}
