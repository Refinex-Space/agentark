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

package space.refinex.agentark.knowledge.domain;

import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.Objects;

/**
 * 集中执行四类 Profile 共享的纯领域校验，不承担序列化或持久化职责。
 *
 * @author refinex
 */
final class ProfileRules {

    /**
     * 禁止实例化纯校验规则。
     */
    private ProfileRules() {
    }

    /**
     * 校验不可变 Profile 的共同字段。
     *
     * @param key           项目内稳定 Key
     * @param versionNumber Key 下版本号
     * @param configJson    规范化 JSON
     * @param contentHash   配置内容摘要
     * @param status        发布状态
     * @param createdAt     创建时间
     */
    static void require(
        String key,
        long versionNumber,
        String configJson,
        Checksum contentHash,
        KnowledgeProfileStatus status,
        Instant createdAt) {
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (key == null
            || !key.matches("[a-z][a-z0-9-]{0,62}")
            || versionNumber <= 0
            || configJson == null
            || configJson.isBlank()
            || configJson.length() > 65535) {
            throw new IllegalArgumentException("knowledge profile fields are invalid");
        }
    }
}
