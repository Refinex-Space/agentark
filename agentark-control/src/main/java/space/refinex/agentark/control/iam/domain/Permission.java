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

package space.refinex.agentark.control.iam.domain;

import space.refinex.agentark.kernel.id.PermissionId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示由平台版本管理的稳定权限注册项。
 *
 * @param id          权限标识
 * @param key         全局唯一权限键
 * @param description 中文职责说明
 * @param riskLevel   操作风险级别
 * @param createdAt   注册时刻
 * @author refinex
 */
public record Permission(
    PermissionId id,
    String key,
    String description,
    PermissionRiskLevel riskLevel,
    Instant createdAt) {

    /**
     * 校验权限注册项格式和风险说明。
     *
     * @param id          权限标识
     * @param key         权限键
     * @param description 职责说明
     * @param riskLevel   风险级别
     * @param createdAt   注册时刻
     */
    public Permission {
        Objects.requireNonNull(id, "id must not be null");
        if (key == null || !key.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("permission key must use resource:action format");
        }
        description = IamFieldPolicy.text(description, "description", 255);
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
    }
}
