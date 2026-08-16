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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

/**
 * 验证 Skill 制品完整性门禁拒绝 Hash、大小或媒体类型漂移。
 *
 * @author refinex
 */
class SkillArtifactVerifierTest {

    /** 待测试 Skill 制品校验器。 */
    private final SkillArtifactVerifier verifier = new SkillArtifactVerifier();

    /** 验证匹配制品返回副本，错误 Hash 被稳定分类。 */
    @Test
    void verifiesContentAndRejectsHashMismatch() {
        byte[] content = "verified-skill".getBytes(StandardCharsets.UTF_8);
        SkillBinding valid = binding(Checksum.sha256(content), content.length);

        byte[] verified = verifier.verify(valid, content, "application/gzip");

        assertThat(verified).isEqualTo(content).isNotSameAs(content);
        SkillBinding invalid = binding(Checksum.sha256("other"), content.length);
        assertThatThrownBy(() -> verifier.verify(invalid, content, "application/gzip"))
            .isInstanceOfSatisfying(AgentScopeProviderException.class, exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ProviderErrorCode.SKILL_ARTIFACT_INVALID));
    }

    /**
     * @param checksum Snapshot 预期校验和
     * @param size     Snapshot 预期字节数
     * @return Skill 绑定
     */
    private SkillBinding binding(Checksum checksum, long size) {
        return new SkillBinding("0198a4b0-0008-7008-8008-000000000008", ObjectRef.of(
            "object://skills/review.tgz", checksum, size, "application/gzip"));
    }
}
