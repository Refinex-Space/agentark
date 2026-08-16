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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import space.refinex.agentark.control.release.adapter.in.web.ReleaseController;
import space.refinex.agentark.control.release.application.AgentPublisher;
import space.refinex.agentark.control.release.application.ReleaseApplicationService;
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
 * 验证 Snapshot Internal API 的 ETag 与 If-None-Match 缓存语义。
 *
 * @author refinex
 */
class ReleaseControllerTest {

    /** 验证首次读取返回 Canonical JSON，后续匹配 ETag 时返回 304 且无响应体。 */
    @Test
    void returnsNotModifiedWhenSnapshotEtagMatches() {
        StoredSnapshot snapshot = snapshot();
        ReleaseRepository repository = mock(ReleaseRepository.class);
        when(repository.findSnapshotInternal(snapshot.revision().id()))
            .thenReturn(Optional.of(snapshot));
        ReleaseController controller = new ReleaseController(
            mock(ReleaseApplicationService.class), mock(AgentPublisher.class),
            new RuntimeInternalContractService(repository));
        Authentication authentication = authentication();

        ResponseEntity<String> first = controller.internalSnapshot(
            authentication, snapshot.revision().id().asString(), "agentscope-java-2",
            "1", "tool-calling", null);
        String etag = first.getHeaders().getETag();
        ResponseEntity<String> cached = controller.internalSnapshot(
            authentication, snapshot.revision().id().asString(), "agentscope-java-2",
            "1", "tool-calling", etag);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo("{\"schemaVersion\":1}");
        assertThat(etag).isEqualTo("\"" + snapshot.revision().contentHash().hex() + "\"");
        assertThat(cached.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(cached.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo(etag);
        assertThat(cached.getBody()).isNull();
    }

    /**
     * 创建受 Control Audience 约束的 Spring Security 服务身份。
     *
     * @return 已认证服务上下文
     */
    private Authentication authentication() {
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "https://issuer.example.test", "runtime", PrincipalType.SERVICE,
            Set.of(), Optional.empty(), Optional.of(new ServiceIdentity(
                "agentark-runtime", Set.of("agentark-control"))));
        return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", List.of());
    }

    /**
     * 创建带固定内容 Hash 的测试 Snapshot。
     *
     * @return 测试 Snapshot
     */
    private StoredSnapshot snapshot() {
        AgentRevision revision = new AgentRevision(
            RevisionId.generate(), OrganizationId.generate(), ProjectId.generate(),
            AgentId.generate(), SnapshotId.generate(), 1, 1, "agentscope-java-2",
            Checksum.sha256("snapshot"), List.of("tool-calling"),
            Instant.parse("2026-08-16T00:00:00Z"));
        return new StoredSnapshot(revision, "{\"schemaVersion\":1}");
    }
}
