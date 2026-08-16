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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.release.application.RuntimeInternalContractService;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.AgentRevision;
import space.refinex.agentark.control.release.domain.ReleaseModels.StoredSnapshot;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.ServiceIdentity;
import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.kernel.id.SnapshotId;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 验证 Runtime Internal Contract 的服务身份 Audience 与 Snapshot 兼容性门禁。
 *
 * @author refinex
 */
class RuntimeInternalContractServiceTest {

    /** 固定测试时刻。 */
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    /** 验证正确服务 Audience、Provider、Schema 和能力可以读取完整 Snapshot。 */
    @Test
    void returnsSnapshotOnlyWhenRuntimeContractIsCompatible() {
        ReleaseRepository repository = mock(ReleaseRepository.class);
        StoredSnapshot snapshot = snapshot();
        when(repository.findSnapshotInternal(snapshot.revision().id()))
            .thenReturn(Optional.of(snapshot));
        RuntimeInternalContractService service = new RuntimeInternalContractService(repository);

        StoredSnapshot found = service.snapshot(
            servicePrincipal(Set.of("agentark-control")), snapshot.revision().id(),
            "agentscope-java-2", Set.of(1), Set.of("tool-calling", "streaming"));

        assertThat(found).isEqualTo(snapshot);
    }

    /** 验证普通用户或不含 Control Audience 的服务不能调用 Internal Contract。 */
    @Test
    void rejectsNonServiceAndWrongAudiencePrincipals() {
        RuntimeInternalContractService service =
            new RuntimeInternalContractService(mock(ReleaseRepository.class));
        AgentArkPrincipal user = new AgentArkPrincipal(
            "https://issuer.example.test", "user", PrincipalType.USER,
            Set.of(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.snapshot(
            user, RevisionId.generate(), "agentscope-java-2", Set.of(1), Set.of()))
            .isInstanceOf(IamAccessDeniedException.class);
        assertThatThrownBy(() -> service.snapshot(
            servicePrincipal(Set.of("agentark-runtime")), RevisionId.generate(),
            "agentscope-java-2", Set.of(1), Set.of()))
            .isInstanceOf(IamAccessDeniedException.class);
    }

    /** 验证 Provider、Schema 或必需能力不兼容时拒绝返回 Snapshot。 */
    @Test
    void rejectsIncompatibleRuntimeProviderSchemaOrCapabilities() {
        ReleaseRepository repository = mock(ReleaseRepository.class);
        StoredSnapshot snapshot = snapshot();
        when(repository.findSnapshotInternal(snapshot.revision().id()))
            .thenReturn(Optional.of(snapshot));
        RuntimeInternalContractService service = new RuntimeInternalContractService(repository);
        AgentArkPrincipal principal = servicePrincipal(Set.of("agentark-control"));

        assertThatThrownBy(() -> service.snapshot(
            principal, snapshot.revision().id(), "another-runtime", Set.of(1),
            Set.of("tool-calling")))
            .isInstanceOf(IamConflictException.class);
        assertThatThrownBy(() -> service.snapshot(
            principal, snapshot.revision().id(), "agentscope-java-2", Set.of(2),
            Set.of("tool-calling")))
            .isInstanceOf(IamConflictException.class);
        assertThatThrownBy(() -> service.snapshot(
            principal, snapshot.revision().id(), "agentscope-java-2", Set.of(1), Set.of()))
            .isInstanceOf(IamConflictException.class);
    }

    /**
     * 创建受测试 Audience 约束的服务主体。
     *
     * @param audiences 服务可访问的 Audience
     * @return 已认证服务主体
     */
    private AgentArkPrincipal servicePrincipal(Set<String> audiences) {
        return new AgentArkPrincipal(
            "https://issuer.example.test", "runtime", PrincipalType.SERVICE,
            Set.of(), Optional.empty(), Optional.of(new ServiceIdentity("agentark-runtime", audiences)));
    }

    /**
     * 创建要求 tool-calling 能力的 Snapshot。
     *
     * @return 测试 Snapshot
     */
    private StoredSnapshot snapshot() {
        AgentRevision revision = new AgentRevision(
            RevisionId.generate(), OrganizationId.generate(), ProjectId.generate(),
            AgentId.generate(), SnapshotId.generate(), 1, 1, "agentscope-java-2",
            Checksum.sha256("snapshot"), List.of("tool-calling"), NOW);
        return new StoredSnapshot(revision, "{}");
    }
}
