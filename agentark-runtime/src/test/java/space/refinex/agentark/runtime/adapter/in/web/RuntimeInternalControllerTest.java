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

package space.refinex.agentark.runtime.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.ServiceIdentity;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeInternalController.InternalTurnRequest;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeInternalController.InternalTurnResponse;
import space.refinex.agentark.runtime.application.RuntimeAdmissionService;
import space.refinex.agentark.runtime.application.RuntimeQueryService;
import space.refinex.agentark.runtime.domain.RuntimeAccessDeniedException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 Runtime Internal Turn 接单的服务 Audience、租户固定与输入 Hash 边界。
 *
 * @author refinex
 */
class RuntimeInternalControllerTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Runtime Internal Controller 测试实例。 */
    RuntimeInternalControllerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明正确 Audience 的 Scheduler 服务身份可幂等接单并得到稳定 Run 标识。 */
    @Test
    void acceptsAudienceBoundServiceCommand() {
        Fixture fixture = fixture();

        InternalTurnResponse response = fixture.controller().createTurn(
            authentication("agentark-runtime"), "scheduler:job:attempt-1",
            fixture.request()).block().getBody();

        assertThat(response).isNotNull();
        assertThat(response.runId()).isEqualTo(fixture.turn().currentRunId()
            .orElseThrow().asString());
        verify(fixture.admissionService()).acceptTurn(any());
    }

    /** 证明缺少 Runtime Audience 的服务 Token 无法调用内部接单契约。 */
    @Test
    void rejectsServiceWithWrongAudience() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller().createTurn(
            authentication("agentark-control"), "scheduler:job:attempt-1",
            fixture.request()).block())
            .isInstanceOf(RuntimeAccessDeniedException.class);
        verifyNoInteractions(fixture.admissionService());
    }

    /** 证明请求声明的输入 Hash 与正文不一致时不会写入 Runtime 事务。 */
    @Test
    void rejectsMismatchedInputHash() {
        Fixture fixture = fixture();
        InternalTurnRequest invalid = new InternalTurnRequest(
            fixture.request().organizationId(), fixture.request().projectId(),
            fixture.request().sessionId(), fixture.request().input(),
            Checksum.sha256("different").value(), fixture.request().priority());

        assertThatThrownBy(() -> fixture.controller().createTurn(
            authentication("agentark-runtime"), "scheduler:job:attempt-1", invalid).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inputHash");
        verifyNoInteractions(fixture.admissionService());
    }

    /**
     * 创建隔离的 Controller、Session、Turn 和依赖桩。
     *
     * @return 测试 Fixture
     */
    private Fixture fixture() {
        RuntimeAdmissionService admissionService = mock(RuntimeAdmissionService.class);
        RuntimeQueryService queryService = mock(RuntimeQueryService.class);
        RuntimeProviderCatalog providerCatalog = mock(RuntimeProviderCatalog.class);
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        SessionId sessionId = SessionId.generate();
        Session session = new Session(
            sessionId, organizationId, projectId, DeploymentId.generate(), RevisionId.generate(),
            SnapshotId.generate(), Checksum.sha256("snapshot"), Map.of(), Map.of(),
            SessionStatus.ACTIVE, 0, 0, NOW, NOW);
        String input = "{\"message\":\"hello\"}";
        Turn turn = new Turn(
            TurnId.generate(), organizationId, projectId, sessionId, 1,
            RuntimePayload.inline(input), Checksum.sha256(input), TurnStatus.ACCEPTED,
            Optional.of(RunId.generate()), FencingToken.unclaimed(), 0, NOW, NOW);
        when(queryService.session(sessionId)).thenReturn(session);
        when(providerCatalog.current()).thenReturn(new RuntimeProviderMetadata(
            "agentscope", "1.0.0", Set.of(1), Set.of("streaming")));
        when(admissionService.acceptTurn(any())).thenReturn(turn);
        RuntimeInternalController controller = new RuntimeInternalController(
            admissionService, queryService, providerCatalog);
        InternalTurnRequest request = new InternalTurnRequest(
            organizationId.asString(), projectId.asString(), sessionId.asString(), input,
            Checksum.sha256(input).value(), 0);
        return new Fixture(controller, admissionService, turn, request);
    }

    /**
     * 创建已认证的 Audience 受限服务身份。
     *
     * @param audience Token Audience
     * @return Spring Authentication
     */
    private TestingAuthenticationToken authentication(String audience) {
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "https://issuer.agentark.test", "scheduler-service", PrincipalType.SERVICE,
            Set.of(), Optional.empty(), Optional.of(new ServiceIdentity(
                "agentark-scheduler", Set.of(audience))));
        return new TestingAuthenticationToken(principal, null, "internal-service");
    }

    /**
     * @param controller       待测 Controller
     * @param admissionService 接单服务桩
     * @param turn             预期 Turn
     * @param request          有效请求
     * @author refinex
     */
    private record Fixture(
        RuntimeInternalController controller,
        RuntimeAdmissionService admissionService,
        Turn turn,
        InternalTurnRequest request) {
    }
}
