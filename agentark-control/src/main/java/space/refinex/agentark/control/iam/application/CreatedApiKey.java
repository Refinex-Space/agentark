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

import space.refinex.agentark.control.iam.domain.ApiKey;

import java.util.Objects;

/**
 * 表示 API Key 创建成功后的单次明文交付对象，不能持久化、缓存或写日志。
 *
 * @param metadata  仅含摘要的持久化元数据
 * @param plaintext 只允许当前创建响应读取一次的完整 Key
 * @author refinex
 */
public record CreatedApiKey(ApiKey metadata, String plaintext) {

    /**
     * 校验单次交付对象，禁止空 Key。
     *
     * @param metadata  只含摘要的元数据
     * @param plaintext 完整明文 Key
     */
    public CreatedApiKey {
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (plaintext == null || !plaintext.matches("ark_[A-Za-z0-9_-]{12}_[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("plaintext API key has invalid format");
        }
    }
}
