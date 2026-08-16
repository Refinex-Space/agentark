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

package space.refinex.agentark.control.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import space.refinex.agentark.control.release.application.CanonicalSnapshotSerializer;
import space.refinex.agentark.foundation.web.AgentArkJacksonModule;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.*;
import space.refinex.agentark.kernel.snapshot.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Canonical Snapshot 的稳定 Hash、契约字段和 SecretRef 安全边界。
 *
 * @author refinex
 */
class CanonicalSnapshotSerializerTest {

    /** JSON 映射器。 */
    private final JsonMapper mapper = JsonMapper.builder()
        .addModule(new AgentArkJacksonModule()).build();

    /** 验证相同 Snapshot 重复序列化得到相同字节等价 JSON 和内容 Hash。 */
    @Test
    void computesStableHashExcludingTopLevelContentHash() throws Exception {
        CanonicalSnapshotSerializer serializer = new CanonicalSnapshotSerializer(mapper);
        AgentRevisionSnapshot input = snapshot();

        var first = serializer.serialize(input);
        var second = serializer.serialize(input);
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = new LinkedHashMap<>(
            mapper.readValue(first.canonicalJson(), Map.class));
        fields.remove("contentHash");

        assertThat(first.canonicalJson()).isEqualTo(second.canonicalJson());
        assertThat(first.snapshot().contentHash())
            .isEqualTo(Checksum.sha256(mapper.writeValueAsString(fields)));
        assertThat(first.canonicalJson())
            .contains("\"schemaVersion\":1")
            .contains("\"entrypoint\":\"harness\"")
            .contains("\"secretRef\":\"secret://project/model-production\"")
            .doesNotContain("apiKey")
            .doesNotContain("password")
            .doesNotContain("plaintext");
    }

    /** @return 语义完整的 Snapshot */
    private AgentRevisionSnapshot snapshot() {
        CredentialSpec credential = new CredentialSpec(
            SecretRef.parse("secret://project/model-production"),
            SecretResolutionPolicy.LATEST_ENABLED);
        return new AgentRevisionSnapshot(
            SchemaVersion.initial(), OrganizationId.generate(), ProjectId.generate(),
            SnapshotId.generate(), AgentId.generate(), RevisionId.generate(), 1,
            Instant.parse("2026-08-16T00:00:00Z"), Checksum.sha256("temporary"),
            new RuntimeProviderId("agentscope-java-2"),
            new AgentSpec("review-agent", AgentEntrypoint.HARNESS, List.of("tool-calling")),
            new ModelSpec("openai-compatible", "model-x",
                new ModelParameters(new BigDecimal("0.2"), 4096), credential),
            List.of(new PromptSpec(
                PromptRole.SYSTEM, PromptVersionId.generate(), Checksum.sha256("Review."), "Review.")),
            List.of(), List.of(), List.of(),
            new MemorySpec(MemoryProfileVersionId.generate()),
            new WorkspaceSpec(WorkspaceProfileVersionId.generate()),
            new SandboxSpec(SandboxProfileVersionId.generate()),
            new PermissionSpec(PermissionDecision.DENY, List.of()),
            new RuntimeLimits(Duration.ofMinutes(5), 10, 2));
    }
}
