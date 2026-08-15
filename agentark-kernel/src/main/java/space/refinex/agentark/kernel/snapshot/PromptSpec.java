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

package space.refinex.agentark.kernel.snapshot;

import java.util.Objects;

import space.refinex.agentark.kernel.id.PromptVersionId;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 表示不可变 Prompt 正文、角色以及解析来源版本，并强制正文与校验和一致。
 *
 * @param role            Provider 中立 Prompt 角色
 * @param promptVersionId Control Catalog 中的 Prompt 版本标识
 * @param contentHash     Prompt UTF-8 正文的 SHA-256 校验和
 * @param content         发布时内嵌的不可变 Prompt 正文
 * @author refinex
 */
public record PromptSpec(
    PromptRole role, PromptVersionId promptVersionId, Checksum contentHash, String content) {

    /**
     * 校验并创建 Prompt 规范，拒绝正文与校验和不一致的对象。
     *
     * @param role            Prompt 角色
     * @param promptVersionId Prompt 版本标识
     * @param contentHash     正文校验和
     * @param content         Prompt 正文，最长 1,000,000 字符
     * @throws NullPointerException     当角色、版本或校验和为 {@code null} 时抛出
     * @throws IllegalArgumentException 当正文为空、超长或与校验和不一致时抛出
     */
    public PromptSpec {
        Objects.requireNonNull(role, "PromptSpec role must not be null");
        Objects.requireNonNull(promptVersionId, "PromptSpec promptVersionId must not be null");
        Objects.requireNonNull(contentHash, "PromptSpec contentHash must not be null");
        SnapshotRequirements.text(content, "PromptSpec content", 1_000_000);
        if (!contentHash.equals(Checksum.sha256(content))) {
            throw new IllegalArgumentException("PromptSpec contentHash does not match content");
        }
    }
}
