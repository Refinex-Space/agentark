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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.McpServerVersionId;
import space.refinex.agentark.kernel.id.MemoryProfileVersionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.PromptVersionId;
import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.kernel.id.SandboxProfileVersionId;
import space.refinex.agentark.kernel.id.SkillVersionId;
import space.refinex.agentark.kernel.id.SnapshotId;
import space.refinex.agentark.kernel.id.WorkspaceProfileVersionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SchemaVersion;
import space.refinex.agentark.kernel.ref.SecretRef;

/**
 * 提供语义完整且可重复使用的 Snapshot 测试夹具。
 *
 * @author refinex
 */
final class SnapshotFixtures {

  /** 禁止实例化纯静态测试夹具。 */
  private SnapshotFixtures() {}

  /**
   * 构造满足全部领域约束的 Agent Revision Snapshot。
   *
   * @return 可用于测试的合法 Snapshot
   */
  static AgentRevisionSnapshot validSnapshot() {
    CredentialSpec credential =
        new CredentialSpec(
            SecretRef.parse("secret://project/model-production"),
            SecretResolutionPolicy.LATEST_ENABLED);
    return new AgentRevisionSnapshot(
        SchemaVersion.initial(),
        OrganizationId.generate(),
        ProjectId.generate(),
        SnapshotId.generate(),
        AgentId.generate(),
        RevisionId.generate(),
        1,
        Instant.parse("2026-08-15T06:00:00Z"),
        Checksum.sha256("snapshot"),
        new RuntimeProviderId("agentscope-java-2"),
        new AgentSpec("code-review-agent", AgentEntrypoint.HARNESS, List.of("tool-calling")),
        new ModelSpec(
            "dashscope", "qwen-plus", new ModelParameters(new BigDecimal("0.2"), 8192), credential),
        List.of(
            new PromptSpec(
                PromptRole.SYSTEM,
                PromptVersionId.generate(),
                Checksum.sha256("Review this change."),
                "Review this change.")),
        List.of(
            new McpSpec(
                McpServerVersionId.generate(),
                McpTransport.STREAMABLE_HTTP,
                URI.create("https://mcp.example.com"),
                Optional.of(credential),
                List.of("repository.read"))),
        List.of(
            new SkillSpec(
                SkillVersionId.generate(),
                ObjectRef.of(
                    "s3://agentark-skills/review.tgz",
                    Checksum.sha256("skill"),
                    512,
                    "application/gzip"))),
        List.of(
            new KnowledgeSpec(
                KnowledgeRevisionId.generate(),
                new RetrievalSpec(8, new BigDecimal("0.72"), "default-reranker"))),
        new MemorySpec(MemoryProfileVersionId.generate(), Map.of("strategy", "session")),
        new WorkspaceSpec(WorkspaceProfileVersionId.generate(), Map.of("mode", "isolated")),
        new SandboxSpec(SandboxProfileVersionId.generate(), Map.of("network", "deny")),
        new PermissionSpec(
            PermissionDecision.ASK,
            List.of(new PermissionRuleSpec("tool:filesystem.write", PermissionDecision.DENY))),
        new RuntimeLimits(Duration.ofMinutes(10), 64, 8));
  }
}
