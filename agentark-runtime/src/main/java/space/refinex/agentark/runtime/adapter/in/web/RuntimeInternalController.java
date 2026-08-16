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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeAdmissionService;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.application.RuntimeQueryService;
import space.refinex.agentark.runtime.domain.RuntimeAccessDeniedException;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;
import space.refinex.agentark.runtime.domain.RuntimeModels.Session;
import space.refinex.agentark.runtime.domain.RuntimeModels.Turn;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 提供只允许 Audience 受限服务身份调用的 Runtime v1 内部接单契约。
 *
 * @author refinex
 */
@RestController
public final class RuntimeInternalController {

    /**
     * Runtime Internal API 要求的服务 Audience。
     */
    private static final String REQUIRED_AUDIENCE = "agentark-runtime";

    /**
     * 内联输入最大字节数。
     */
    private static final int MAX_INLINE_INPUT_BYTES = 262_144;

    /**
     * Runtime 接单服务。
     */
    private final RuntimeAdmissionService admissionService;

    /**
     * Runtime 查询服务。
     */
    private final RuntimeQueryService queryService;

    /**
     * 当前 Runtime Provider 能力目录。
     */
    private final RuntimeProviderCatalog providerCatalog;

    /**
     * 创建 Runtime Internal Controller。
     *
     * @param admissionService Runtime 接单服务
     * @param queryService     Runtime 查询服务
     * @param providerCatalog  Runtime Provider 目录
     */
    public RuntimeInternalController(
        RuntimeAdmissionService admissionService,
        RuntimeQueryService queryService,
        RuntimeProviderCatalog providerCatalog) {
        this.admissionService = Objects.requireNonNull(
            admissionService, "admissionService must not be null");
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.providerCatalog = Objects.requireNonNull(
            providerCatalog, "providerCatalog must not be null");
    }

    /**
     * 由 Scheduler 等内部服务幂等创建 Turn，并在接单事务提交后返回稳定 Run 标识。
     *
     * @param authentication 已认证服务主体
     * @param idempotencyKey Scheduler 派生幂等键
     * @param request        内部 Turn 命令
     * @return 202 Accepted 与稳定 Turn/Run 标识
     */
    @PostMapping("/internal/v1/runtime/turns")
    public Mono<ResponseEntity<InternalTurnResponse>> createTurn(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InternalTurnRequest request) {
        return Mono.fromCallable(() -> {
            requireService(authentication);
            OrganizationId organizationId = OrganizationId.parse(request.organizationId());
            ProjectId projectId = ProjectId.parse(request.projectId());
            SessionId sessionId = SessionId.parse(request.sessionId());
            Session session = queryService.session(sessionId);
            if (!session.organizationId().equals(organizationId)
                || !session.projectId().equals(projectId)) {
                throw new RuntimeNotFoundException("session is not available");
            }
            requireInlineSize(request.input());
            Checksum inputHash = new Checksum(request.inputHash());
            if (!inputHash.equals(Checksum.sha256(request.input()))) {
                throw new IllegalArgumentException("inputHash does not match input");
            }
            RuntimeProviderMetadata provider = providerCatalog.current();
            Checksum requestHash = Checksum.sha256(
                request.organizationId() + "\n" + request.projectId() + "\n"
                    + request.sessionId() + "\n" + request.inputHash() + "\n"
                    + request.priority());
            Turn turn = admissionService.acceptTurn(new AcceptTurnCommand(
                organizationId, projectId, sessionId, RuntimePayload.inline(request.input()),
                inputHash, provider.providerId(), provider.compilerVersion(), request.priority(),
                idempotencyKey, requestHash));
            return ResponseEntity.accepted().body(new InternalTurnResponse(
                turn.id().asString(), turn.currentRunId().orElseThrow().asString(),
                turn.status().name()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 校验调用者是 Audience 包含 agentark-runtime 的非交互式服务身份。
     *
     * @param authentication Spring Security 认证对象
     */
    private void requireService(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)
            || principal.type() != PrincipalType.SERVICE
            || principal.serviceIdentity().isEmpty()
            || !principal.serviceIdentity().orElseThrow().audiences().contains(REQUIRED_AUDIENCE)) {
            throw new RuntimeAccessDeniedException(
                "runtime internal API requires an audience-bound service identity");
        }
    }

    /**
     * 校验内联输入不超过 Runtime 统一上限。
     *
     * @param input 输入 JSON 文本
     */
    private void requireInlineSize(String input) {
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INLINE_INPUT_BYTES) {
            throw new IllegalArgumentException("turn input exceeds inline payload limit");
        }
    }

    /**
     * @param organizationId 所属组织 UUIDv7
     * @param projectId      所属项目 UUIDv7
     * @param sessionId      已固定 Snapshot 的 Session UUIDv7
     * @param input          不含 Secret 的中立输入 JSON 文本
     * @param inputHash      输入内容规范 SHA-256
     * @param priority       Runtime Work Item 优先级，范围为 -1000 到 1000
     * @author refinex
     */
    public record InternalTurnRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotBlank String sessionId,
        @NotBlank @Size(max = MAX_INLINE_INPUT_BYTES) String input,
        @NotBlank String inputHash,
        @Min(-1000) @Max(1000) int priority) {
    }

    /**
     * @param turnId 本次接单创建的 Turn UUIDv7
     * @param runId  初始 Run UUIDv7
     * @param status Turn 状态，当前接单响应固定为 ACCEPTED
     * @author refinex
     */
    public record InternalTurnResponse(String turnId, String runId, String status) {
    }
}
