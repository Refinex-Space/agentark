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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.PromptVersionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.SecretRef;

/**
 * 验证 Agent Revision Snapshot 的不可变性、边界约束与 Secret 安全模型。
 *
 * @author refinex
 */
class AgentRevisionSnapshotTest {

  /** 验证 Snapshot 对依赖集合执行防御性复制并保留冻结时间。 */
  @Test
  void snapshotDefensivelyCopiesDependencyCollections() {
    AgentRevisionSnapshot baseline = SnapshotFixtures.validSnapshot();
    List<PromptSpec> prompts = new ArrayList<>(baseline.prompts());
    AgentRevisionSnapshot snapshot = copyWithPrompts(baseline, prompts);

    prompts.clear();

    assertThat(snapshot.prompts()).hasSize(1).isUnmodifiable();
    assertThat(snapshot.createdAt()).isEqualTo(Instant.parse("2026-08-15T06:00:00Z"));
  }

  /** 验证 Snapshot 修订号和运行时限制不能突破领域边界。 */
  @Test
  void snapshotRejectsInvalidRevisionAndRuntimeBounds() {
    AgentRevisionSnapshot baseline = SnapshotFixtures.validSnapshot();

    assertThatThrownBy(() -> copyWithRevisionNumber(baseline, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RuntimeLimits(Duration.ofMillis(1500), 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ModelParameters(null, 1)).isInstanceOf(NullPointerException.class);
  }

  /** 验证凭证模型只能表达 SecretRef，不能表示任何明文 Secret 字段。 */
  @Test
  void credentialModelCannotRepresentPlaintextSecretFields() {
    List<String> components =
        Arrays.stream(CredentialSpec.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(components).containsExactly("secretRef", "resolutionPolicy");
    assertThat(CredentialSpec.class.getRecordComponents()[0].getType()).isEqualTo(SecretRef.class);
    assertThat(components).doesNotContain("password", "token", "apiKey", "secretValue");
  }

  /** 验证冻结提示词内容必须与其 SHA-256 校验和一致。 */
  @Test
  void promptContentMustMatchItsFrozenChecksum() {
    assertThatThrownBy(
            () ->
                new PromptSpec(
                    PromptRole.SYSTEM,
                    PromptVersionId.generate(),
                    Checksum.sha256("different"),
                    "Review this change."))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * 复制 Snapshot 并替换提示词集合，用于验证集合防御性复制。
   *
   * @param snapshot 基准 Snapshot
   * @param prompts 新提示词集合
   * @return 替换提示词后的 Snapshot
   */
  private static AgentRevisionSnapshot copyWithPrompts(
      AgentRevisionSnapshot snapshot, List<PromptSpec> prompts) {
    return new AgentRevisionSnapshot(
        snapshot.schemaVersion(),
        snapshot.organizationId(),
        snapshot.projectId(),
        snapshot.snapshotId(),
        snapshot.agentId(),
        snapshot.revisionId(),
        snapshot.revisionNumber(),
        snapshot.createdAt(),
        snapshot.contentHash(),
        snapshot.runtimeProvider(),
        snapshot.agent(),
        snapshot.model(),
        prompts,
        snapshot.mcpServers(),
        snapshot.skills(),
        snapshot.knowledge(),
        snapshot.memory(),
        snapshot.workspace(),
        snapshot.sandbox(),
        snapshot.permissions(),
        snapshot.limits());
  }

  /**
   * 复制 Snapshot 并替换修订序号，用于验证修订号边界。
   *
   * @param snapshot 基准 Snapshot
   * @param revisionNumber 新修订序号
   * @return 替换修订序号后的 Snapshot
   */
  private static AgentRevisionSnapshot copyWithRevisionNumber(
      AgentRevisionSnapshot snapshot, long revisionNumber) {
    return new AgentRevisionSnapshot(
        snapshot.schemaVersion(),
        snapshot.organizationId(),
        snapshot.projectId(),
        snapshot.snapshotId(),
        snapshot.agentId(),
        snapshot.revisionId(),
        revisionNumber,
        snapshot.createdAt(),
        snapshot.contentHash(),
        snapshot.runtimeProvider(),
        snapshot.agent(),
        snapshot.model(),
        snapshot.prompts(),
        snapshot.mcpServers(),
        snapshot.skills(),
        snapshot.knowledge(),
        snapshot.memory(),
        snapshot.workspace(),
        snapshot.sandbox(),
        snapshot.permissions(),
        snapshot.limits());
  }
}
