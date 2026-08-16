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

package space.refinex.agentark.runtime.provider.agentscope.skill;

import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

import java.util.Objects;

/**
 * 在 Skill Repository 接收制品前强制核对 Snapshot 冻结的大小、媒体类型与 SHA-256。
 *
 * @author refinex
 */
public final class SkillArtifactVerifier {

    /**
     * 校验完整制品内容并返回防御性副本，调用方不得直接执行未通过校验的字节。
     *
     * @param binding   Snapshot 冻结的 Skill 绑定
     * @param content   Object Store 返回的完整制品字节
     * @param mediaType Object Store 返回的媒体类型
     * @return 已通过完整性校验的制品字节副本
     */
    public byte[] verify(SkillBinding binding, byte[] content, String mediaType) {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (!binding.artifact().mediaType().equals(mediaType)
            || binding.artifact().size() != content.length
            || !binding.artifact().checksum().equals(Checksum.sha256(content))) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SKILL_ARTIFACT_INVALID,
                "Skill artifact does not match Snapshot integrity metadata");
        }
        return content.clone();
    }
}
