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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeApiModels.*;
import space.refinex.agentark.runtime.application.*;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.DecideApprovalCommand;
import space.refinex.agentark.runtime.domain.RuntimeAccessDeniedException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/**
 * 提供 Session、Turn、Run、Event、SSE、取消与 HITL Approval 的 Runtime Public API。
 *
 * @author refinex
 */
@RestController
public final class RuntimeController {

    /**
     * 最大内联 Turn 输入字节数，超限输入应在后续对象上传 API 中使用 ObjectRef。
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
     * Runtime 运行控制服务。
     */
    private final RuntimeControlService controlService;

    /**
     * Approval 与 Worker 短事务协调器。
     */
    private final RuntimeExecutionCoordinator executionCoordinator;

    /**
     * Runtime 授权服务。
     */
    private final RuntimeAuthorizationService authorizationService;

    /**
     * Event 回放与实时追平服务。
     */
    private final RuntimeEventStreamService eventStreamService;

    /**
     * API JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 当前 Runtime Provider 能力目录。
     */
    private final space.refinex.agentark.runtime.port.RuntimeProviderCatalog providerCatalog;

    /**
     * 创建 Runtime API Controller。
     *
     * @param admissionService     接单服务
     * @param queryService         查询服务
     * @param controlService       运行控制服务
     * @param executionCoordinator Approval 协调器
     * @param authorizationService 授权服务
     * @param eventStreamService   Event Stream 服务
     * @param objectMapper         JSON 解析器
     * @param providerCatalog      Provider 能力目录
     */
    public RuntimeController(
        RuntimeAdmissionService admissionService,
        RuntimeQueryService queryService,
        RuntimeControlService controlService,
        RuntimeExecutionCoordinator executionCoordinator,
        RuntimeAuthorizationService authorizationService,
        RuntimeEventStreamService eventStreamService,
        ObjectMapper objectMapper,
        space.refinex.agentark.runtime.port.RuntimeProviderCatalog providerCatalog) {
        this.admissionService = Objects.requireNonNull(
            admissionService, "admissionService must not be null");
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.controlService = Objects.requireNonNull(
            controlService, "controlService must not be null");
        this.executionCoordinator = Objects.requireNonNull(
            executionCoordinator, "executionCoordinator must not be null");
        this.authorizationService = Objects.requireNonNull(
            authorizationService, "authorizationService must not be null");
        this.eventStreamService = Objects.requireNonNull(
            eventStreamService, "eventStreamService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.providerCatalog = Objects.requireNonNull(
            providerCatalog, "providerCatalog must not be null");
    }

    /**
     * 创建固定 Deployment/Revision/Snapshot 的 Runtime Session。
     *
     * @param authentication 已认证主体
     * @param idempotencyKey 幂等键
     * @param request        创建请求
     * @return 201 Created Session
     */
    @PostMapping("/api/v1/runtime/sessions")
    public Mono<ResponseEntity<SessionResponse>> createSession(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateSessionRequest request) {
        return blocking(() -> {
            AgentArkPrincipal principal = principal(authentication);
            OrganizationId organizationId = OrganizationId.parse(request.organizationId());
            ProjectId projectId = ProjectId.parse(request.projectId());
            authorizationService.requireProject(
                principal, RuntimeAuthorizationService.EXECUTE, organizationId, projectId);
            Checksum requestHash = hash(request);
            Session session = admissionService.createSession(
                organizationId, projectId, DeploymentId.parse(request.deploymentId()),
                map(request.participantMetadata()), map(request.channelMetadata()),
                idempotencyKey, requestHash);
            return ResponseEntity.created(URI.create(
                    "/api/v1/runtime/sessions/" + session.id().asString()))
                .body(session(session));
        });
    }

    /**
     * 读取固定 Snapshot 的 Runtime Session。
     *
     * @param authentication 已认证主体
     * @param sessionId      Session UUIDv7
     * @return Session
     */
    @GetMapping("/api/v1/runtime/sessions/{sessionId}")
    public Mono<SessionResponse> getSession(
        Authentication authentication, @PathVariable String sessionId) {
        return blocking(() -> {
            Session session = queryService.session(SessionId.parse(sessionId));
            authorizationService.requireProject(
                principal(authentication), RuntimeAuthorizationService.READ,
                session.organizationId(), session.projectId());
            return session(session);
        });
    }

    /**
     * 原子接收 Turn，事务提交后返回 202 和稳定 Run 标识。
     *
     * @param authentication 已认证主体
     * @param sessionId      Session UUIDv7
     * @param idempotencyKey 幂等键
     * @param request        Turn 请求
     * @return 202 Accepted Turn
     */
    @PostMapping("/api/v1/runtime/sessions/{sessionId}/turns")
    public Mono<ResponseEntity<TurnResponse>> createTurn(
        Authentication authentication,
        @PathVariable String sessionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateTurnRequest request) {
        return blocking(() -> {
            SessionId parsedSessionId = SessionId.parse(sessionId);
            Session session = queryService.session(parsedSessionId);
            OrganizationId organizationId = OrganizationId.parse(request.organizationId());
            ProjectId projectId = ProjectId.parse(request.projectId());
            if (!session.organizationId().equals(organizationId)
                || !session.projectId().equals(projectId)) {
                throw new space.refinex.agentark.runtime.domain.RuntimeNotFoundException(
                    "session is not available");
            }
            authorizationService.requireProject(
                principal(authentication), RuntimeAuthorizationService.EXECUTE,
                organizationId, projectId);
            String input = json(request.input());
            requireInlineSize(input);
            RuntimeProviderMetadata provider = providerCatalog.current();
            Turn turn = admissionService.acceptTurn(new AcceptTurnCommand(
                organizationId, projectId, parsedSessionId, RuntimePayload.inline(input),
                Checksum.sha256(input), provider.providerId(), provider.compilerVersion(),
                request.priority(), idempotencyKey, hash(request)));
            TurnResponse body = turn(turn);
            return ResponseEntity.accepted()
                .location(URI.create("/api/v1/runtime/runs/" + body.runId()))
                .body(body);
        });
    }

    /**
     * 读取 Run Attempt 当前状态。
     *
     * @param authentication 已认证主体
     * @param runId          Run UUIDv7
     * @return Run
     */
    @GetMapping("/api/v1/runtime/runs/{runId}")
    public Mono<RunResponse> getRun(
        Authentication authentication, @PathVariable String runId) {
        return blocking(() -> {
            Run run = queryService.run(RunId.parse(runId));
            authorize(principal(authentication), RuntimeAuthorizationService.READ, run);
            return run(run);
        });
    }

    /**
     * 幂等取消 Run；SSE 断开不会调用本端点。
     *
     * @param authentication 已认证主体
     * @param runId          Run UUIDv7
     * @return 202 Accepted
     */
    @PostMapping("/api/v1/runtime/runs/{runId}:cancel")
    public Mono<ResponseEntity<Void>> cancelRun(
        Authentication authentication, @PathVariable String runId) {
        return blocking(() -> {
            Run run = queryService.run(RunId.parse(runId));
            authorize(principal(authentication), RuntimeAuthorizationService.CANCEL, run);
            controlService.cancel(run.id(), "USER_REQUESTED");
            return ResponseEntity.accepted().build();
        });
    }

    /**
     * 按 Session Sequence 游标读取已持久 Event。
     *
     * @param authentication 已认证主体
     * @param runId          Run UUIDv7
     * @param after          已消费序号
     * @param limit          最大数量
     * @return Event 列表
     */
    @GetMapping("/api/v1/runtime/runs/{runId}/events")
    public Mono<List<EventResponse>> listEvents(
        Authentication authentication,
        @PathVariable String runId,
        @RequestParam(defaultValue = "0") long after,
        @RequestParam(defaultValue = "100") int limit) {
        return blocking(() -> {
            Run run = queryService.run(RunId.parse(runId));
            authorize(principal(authentication), RuntimeAuthorizationService.READ, run);
            return queryService.events(run.id(), after, limit).stream()
                .map(this::event).toList();
        });
    }

    /**
     * 使用 Last-Event-ID 回放后切换实时追平，并每十五秒发送不持久化 Heartbeat。
     *
     * @param authentication 已认证主体
     * @param runId          Run UUIDv7
     * @param lastEventId    可选 Session Sequence
     * @return SSE Event 流
     */
    @GetMapping(
        value = "/api/v1/runtime/runs/{runId}/events:stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<EventResponse>> streamEvents(
        Authentication authentication,
        @PathVariable String runId,
        @RequestHeader(value = "Last-Event-ID", defaultValue = "0") String lastEventId) {
        return Flux.defer(() -> {
            RunId parsed = RunId.parse(runId);
            Run run = queryService.run(parsed);
            authorize(principal(authentication), RuntimeAuthorizationService.READ, run);
            long cursor = parseCursor(lastEventId);
            Flux<ServerSentEvent<EventResponse>> events = eventStreamService.stream(parsed, cursor)
                .map(item -> ServerSentEvent.<EventResponse>builder(event(item))
                    .id(Long.toString(item.sessionSequence()))
                    .event(item.type())
                    .build());
            Flux<ServerSentEvent<EventResponse>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(ignored -> ServerSentEvent.<EventResponse>builder()
                    .comment("heartbeat")
                    .build());
            return Flux.merge(events, heartbeat);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 按项目租户选择列出 HITL Approval。
     *
     * @param authentication 已认证主体
     * @param status         可选状态
     * @param after          可选 UUIDv7 游标
     * @param limit          最大数量
     * @return Approval 游标页
     */
    @GetMapping("/api/v1/runtime/approvals")
    public Mono<ApprovalPage> listApprovals(
        Authentication authentication,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String after,
        @RequestParam(defaultValue = "50") int limit) {
        return blocking(() -> {
            AgentArkPrincipal principal = principal(authentication);
            var tenant = principal.tenantSelection().orElseThrow(() ->
                new RuntimeAccessDeniedException("runtime project selection is required"));
            ProjectId projectId = tenant.projectId().orElseThrow(() ->
                new RuntimeAccessDeniedException("runtime project selection is required"));
            authorizationService.requireProject(
                principal, RuntimeAuthorizationService.APPROVE,
                tenant.organizationId(), projectId);
            List<ApprovalResponse> items = queryService.approvals(
                    projectId, optionalStatus(status), optionalApprovalId(after), limit)
                .stream().map(this::approval).toList();
            String next = items.size() == limit
                ? items.getLast().approvalId() : null;
            return new ApprovalPage(items, next);
        });
    }

    /**
     * 对参数 Hash 固定的 Approval 做幂等决策，并在全部决策后重新入队。
     *
     * @param authentication 已认证主体
     * @param approvalId     Approval UUIDv7
     * @param idempotencyKey 幂等键
     * @param request        决策请求
     * @return 已决 Approval
     */
    @PostMapping("/api/v1/runtime/approvals/{approvalId}:decide")
    public Mono<ApprovalResponse> decideApproval(
        Authentication authentication,
        @PathVariable String approvalId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody DecideApprovalRequest request) {
        return blocking(() -> {
            Approval approval = queryService.approval(ApprovalId.parse(approvalId));
            AgentArkPrincipal principal = principal(authentication);
            authorizationService.requireProject(
                principal, RuntimeAuthorizationService.APPROVE,
                approval.organizationId(), approval.projectId());
            ApprovalStatus target = decisionStatus(request.decision());
            Approval decided = executionCoordinator.decideApproval(new DecideApprovalCommand(
                approval.id(), request.expectedVersion(), target, principal.getName(),
                idempotencyKey, hash(request)));
            if (decided.status() == ApprovalStatus.EXPIRED) {
                throw new space.refinex.agentark.runtime.domain.RuntimeConflictException(
                    "approval has expired");
            }
            return approval(decided);
        });
    }

    /**
     * 从 Spring Security Authentication 提取 AgentArkPrincipal。
     *
     * @param authentication 已认证上下文
     * @return AgentArk Principal
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new RuntimeAccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }

    /**
     * 按 Run 租户字段执行资源级授权。
     *
     * @param principal  主体
     * @param permission 权限
     * @param run        Run
     */
    private void authorize(AgentArkPrincipal principal, String permission, Run run) {
        authorizationService.requireProject(
            principal, permission, run.organizationId(), run.projectId());
    }

    /**
     * 将阻塞数据库或 Internal HTTP 工作移出 Netty Event Loop。
     *
     * @param supplier 阻塞工作
     * @param <T>      返回类型
     * @return 异步结果
     */
    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> supplier) {
        return Mono.fromCallable(supplier).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将 Session 映射为稳定 API Response。
     *
     * @param value Session
     * @return Session Response
     */
    private SessionResponse session(Session value) {
        return new SessionResponse(
            value.id().asString(), value.organizationId().asString(),
            value.projectId().asString(), value.deploymentId().asString(),
            value.revisionId().asString(), value.snapshotId().asString(),
            value.snapshotHash().toString(), value.status().name(), value.createdAt());
    }

    /**
     * 将 Turn 映射为稳定 API Response。
     *
     * @param value Turn
     * @return Turn Response
     */
    private TurnResponse turn(Turn value) {
        return new TurnResponse(
            value.id().asString(), value.currentRunId().orElseThrow().asString(),
            value.sequence(), value.status().name(), value.createdAt());
    }

    /**
     * 将 Run 映射为稳定 API Response。
     *
     * @param value Run
     * @return Run Response
     */
    private RunResponse run(Run value) {
        return new RunResponse(
            value.id().asString(), value.sessionId().asString(), value.turnId().asString(),
            value.attemptNumber(), value.status().name(), value.runtimeProvider(),
            value.compilerVersion(), value.fencingToken().value(),
            value.startedAt().orElse(null), value.endedAt().orElse(null),
            value.errorCode().orElse(null));
    }

    /**
     * 将 Runtime Event 映射为稳定 API Response。
     *
     * @param value Runtime Event
     * @return Event Response
     */
    private EventResponse event(RuntimeEvent value) {
        JsonNode payload = value.payload().inlineJson()
            .map(this::readJson).orElse(null);
        ObjectRefResponse payloadRef = value.payload().objectRef()
            .map(ref -> new ObjectRefResponse(
                ref.uri().toString(), ref.checksum().toString(), ref.size(), ref.mediaType()))
            .orElse(null);
        return new EventResponse(
            value.schemaVersion(), value.id().asString(), value.sessionSequence(),
            value.runSequence(), value.type(), value.occurredAt(),
            value.organizationId().asString(), value.projectId().asString(),
            value.sessionId().asString(), value.turnId().asString(), value.runId().asString(),
            value.traceId(), value.fencingToken().value(), payload, payloadRef);
    }

    /**
     * 将 Approval 映射为稳定 API Response。
     *
     * @param value Approval
     * @return Approval Response
     */
    private ApprovalResponse approval(Approval value) {
        return new ApprovalResponse(
            value.id().asString(), value.runId().asString(),
            value.organizationId().asString(), value.projectId().asString(),
            value.toolName(), value.action(), value.argumentHash().toString(),
            value.policyVersion(), value.status().name(), value.expectedVersion(),
            value.expiresAt(), value.createdAt());
    }

    /**
     * 规范序列化请求以计算幂等 Hash。
     *
     * @param value 请求对象
     * @return SHA-256
     */
    private Checksum hash(Object value) {
        return Checksum.sha256(json(value));
    }

    /**
     * 序列化 JSON；失败保留异常上下文。
     *
     * @param value 对象
     * @return JSON 文本
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("request cannot be serialized", exception);
        }
    }

    /**
     * 解析内联 Event JSON。
     *
     * @param value JSON 文本
     * @return JSON Node
     */
    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("persisted runtime event payload is invalid", exception);
        }
    }

    /**
     * 校验内联输入大小上限。
     *
     * @param value UTF-8 JSON 文本
     */
    private void requireInlineSize(String value) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            > MAX_INLINE_INPUT_BYTES) {
            throw new IllegalArgumentException("turn input exceeds inline payload limit");
        }
    }

    /**
     * 复制可空元数据 Map。
     *
     * @param value 可空 Map
     * @return 不可变 Map
     */
    private Map<String, String> map(Map<String, String> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    /**
     * 解析 SSE Session Sequence 游标。
     *
     * @param value Last-Event-ID
     * @return 非负序号
     */
    private long parseCursor(String value) {
        try {
            long cursor = Long.parseLong(value);
            if (cursor < 0) {
                throw new NumberFormatException("negative cursor");
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID is invalid", exception);
        }
    }

    /**
     * 解析可选 Approval 状态。
     *
     * @param value 可空状态
     * @return 可选状态
     */
    private Optional<ApprovalStatus> optionalStatus(String value) {
        return value == null ? Optional.empty()
            : Optional.of(ApprovalStatus.valueOf(value.toUpperCase(Locale.ROOT)));
    }

    /**
     * 解析可选 Approval UUIDv7 游标。
     *
     * @param value 可空游标
     * @return 可选 Approval 标识
     */
    private Optional<ApprovalId> optionalApprovalId(String value) {
        return value == null ? Optional.empty() : Optional.of(ApprovalId.parse(value));
    }

    /**
     * 解析显式审批决策。
     *
     * @param value 决策字符串
     * @return APPROVED 或 REJECTED
     */
    private ApprovalStatus decisionStatus(String value) {
        ApprovalStatus status = ApprovalStatus.valueOf(value.toUpperCase(Locale.ROOT));
        if (status != ApprovalStatus.APPROVED && status != ApprovalStatus.REJECTED) {
            throw new IllegalArgumentException("approval decision must be APPROVED or REJECTED");
        }
        return status;
    }
}
