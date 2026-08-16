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

package space.refinex.agentark.runtime.provider.agentscope.prompt;

/**
 * 表示已验证内容 Hash 的不可变 Prompt 绑定。
 *
 * @param role            system、user 或 assistant
 * @param promptVersionId Prompt 版本标识
 * @param contentHash     Prompt 内容 SHA-256
 * @param content         发布时冻结的正文
 * @author refinex
 */
public record PromptBinding(
    String role, String promptVersionId, String contentHash, String content) {

    /**
     * 校验 Prompt 绑定字段完整。
     */
    public PromptBinding {
        if (role == null || role.isBlank() || promptVersionId == null || promptVersionId.isBlank()
            || contentHash == null || contentHash.isBlank() || content == null
            || content.isBlank()) {
            throw new IllegalArgumentException("prompt binding is invalid");
        }
    }
}
