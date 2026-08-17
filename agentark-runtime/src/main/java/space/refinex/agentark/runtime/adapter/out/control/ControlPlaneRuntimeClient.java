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

package space.refinex.agentark.runtime.adapter.out.control;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.DeploymentDescriptor;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.DeploymentResolver;
import space.refinex.agentark.runtime.port.GovernanceAuditClient;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;
import space.refinex.agentark.runtime.port.RuntimeQuotaPort;
import space.refinex.agentark.runtime.port.UsageGovernanceClient;
import space.refinex.agentark.runtime.port.UsageGovernanceStore.UsageExportRecord;
import space.refinex.agentark.runtime.port.SnapshotLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 通过受保护的 Control Internal API 解析 Deployment 与 Snapshot，并实现 ETag 缓存降级。
 *
 * @author refinex
 */
public final class ControlPlaneRuntimeClient
    implements DeploymentResolver, SnapshotLoader, RuntimeQuotaPort, UsageGovernanceClient,
    GovernanceAuditClient {

    /**
     * 内部请求最大等待时间。
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Control WebClient 客户端。
     */
    private final WebClient webClient;

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 服务身份 Token 提供器；Token 只进入 Authorization Header。
     */
    private final Supplier<String> serviceToken;

    /**
     * 当前 Runtime Provider 能力。
     */
    private final RuntimeProviderCatalog providerCatalog;

    /**
     * Revision 对应不可变 Snapshot 缓存。
     */
    private final Map<RevisionId, CachedSnapshot> snapshotCache = new ConcurrentHashMap<>();

    /**
     * 创建 Control Internal Contract 客户端。
     *
     * @param webClient       已固定 Control Base URL 的 WebClient
     * @param objectMapper    JSON 解析器
     * @param serviceToken    服务身份 Token 提供器
     * @param providerCatalog 当前 Provider 能力目录
     */
    public ControlPlaneRuntimeClient(
        WebClient webClient,
        ObjectMapper objectMapper,
        Supplier<String> serviceToken,
        RuntimeProviderCatalog providerCatalog) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken must not be null");
        this.providerCatalog = Objects.requireNonNull(
            providerCatalog, "providerCatalog must not be null");
    }

    /**
     * 解析新 Session 使用的 Deployment；Control 不可用时不得使用陈旧 Deployment 接单。
     *
     * @param deploymentId Deployment 标识
     * @return Deployment 描述
     */
    @Override
    public DeploymentDescriptor resolve(DeploymentId deploymentId) {
        try {
            String json = webClient.get()
                .uri("/internal/v1/deployments/{deploymentId}", deploymentId.asString())
                .headers(this::authorize)
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
            if (json == null || json.isBlank()) {
                throw new RuntimeConflictException("control returned an empty deployment descriptor");
            }
            JsonNode root = read(json);
            return new DeploymentDescriptor(
                DeploymentId.parse(text(root, "deploymentId")),
                OrganizationId.parse(text(root, "organizationId")),
                ProjectId.parse(text(root, "projectId")),
                RevisionId.parse(text(root, "desiredRevisionId")),
                "ENABLED".equals(text(root, "desiredStatus")),
                text(root, "runtimeProvider"), integer(root, "schemaVersion"),
                strings(root, "requiredCapabilities"));
        } catch (WebClientResponseException.NotFound exception) {
            throw new RuntimeNotFoundException("deployment is not available");
        } catch (WebClientResponseException exception) {
            throw new RuntimeConflictException(
                "control rejected deployment resolution with status "
                    + exception.getStatusCode().value());
        } catch (WebClientRequestException exception) {
            throw new RuntimeConflictException("control is unavailable for deployment resolution");
        }
    }

    /**
     * 使用 ETag 加载不可变 Snapshot；仅网络错误或 5xx 时允许复用已验证缓存。
     *
     * @param revisionId Revision 标识
     * @return Snapshot 描述
     */
    @Override
    public SnapshotDescriptor load(RevisionId revisionId) {
        CachedSnapshot cached = snapshotCache.get(revisionId);
        RuntimeProviderMetadata provider = providerCatalog.current();
        try {
            SnapshotResponse response = webClient.get()
                .uri("/internal/v1/agent-revisions/{revisionId}/snapshot", revisionId.asString())
                .headers(headers -> {
                    authorize(headers);
                    headers.set("X-AgentArk-Runtime-Provider", provider.providerId());
                    headers.set("X-AgentArk-Snapshot-Schema-Versions", joinIntegers(
                        provider.supportedSchemas()));
                    headers.set("X-AgentArk-Runtime-Capabilities", String.join(",",
                        new TreeSet<>(provider.capabilities())));
                    if (cached != null) {
                        headers.setIfNoneMatch(java.util.List.of(cached.etag()));
                    }
                })
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode() == HttpStatus.NOT_MODIFIED) {
                        return Mono.just(new SnapshotResponse(null, cached == null
                            ? null : cached.etag(), true));
                    }
                    if (clientResponse.statusCode().is2xxSuccessful()) {
                        String etag = clientResponse.headers().asHttpHeaders().getETag();
                        return clientResponse.bodyToMono(String.class)
                            .map(body -> new SnapshotResponse(body, etag, false));
                    }
                    return clientResponse.createException().flatMap(Mono::error);
                })
                .block(REQUEST_TIMEOUT);
            if (response == null) {
                throw new RuntimeConflictException("control returned no snapshot response");
            }
            if (response.notModified()) {
                if (cached == null) {
                    throw new RuntimeConflictException("control returned 304 without local cache");
                }
                return cached.snapshot();
            }
            SnapshotDescriptor snapshot = snapshot(revisionId, response.body());
            String etag = response.etag() == null || response.etag().isBlank()
                ? '"' + snapshot.contentHash().hex() + '"' : response.etag();
            snapshotCache.put(revisionId, new CachedSnapshot(snapshot, etag));
            return snapshot;
        } catch (WebClientResponseException.NotFound exception) {
            throw new RuntimeNotFoundException("snapshot is not available");
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError() && cached != null) {
                return cached.snapshot();
            }
            throw new RuntimeConflictException(
                "control rejected snapshot loading with status "
                    + exception.getStatusCode().value());
        } catch (WebClientRequestException exception) {
            if (cached != null) {
                return cached.snapshot();
            }
            throw new RuntimeConflictException("control is unavailable and snapshot is not cached");
        }
    }

    /**
     * 通过 Control Internal API 幂等申请项目并发 Run Reservation；Control 不可用时失败关闭新接单。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param idempotencyKey Turn 接单幂等键
     * @param subjectRef     Session 引用
     * @param ttl            Reservation TTL
     * @return 中立 Reservation 结果
     */
    @Override
    public Reservation reserveConcurrentRun(
        OrganizationId organizationId,
        ProjectId projectId,
        String idempotencyKey,
        String subjectRef,
        Duration ttl) {
        try {
            String json = webClient.post()
                .uri("/internal/v1/governance/quota-reservations")
                .headers(this::authorize)
                .bodyValue(Map.of(
                    "organizationId", organizationId.asString(),
                    "projectId", projectId.asString(),
                    "scopeType", "PROJECT",
                    "scopeRef", projectId.asString(),
                    "metric", "CONCURRENT_RUN",
                    "idempotencyKey", idempotencyKey,
                    "subjectRef", subjectRef,
                    "amount", 1,
                    "ttlSeconds", ttl.toSeconds()))
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
            JsonNode root = read(json);
            boolean allowed = bool(root, "allowed");
            return new Reservation(
                allowed, nullableText(root, "reservationId"), nullableText(root, "action"));
        } catch (WebClientResponseException exception) {
            throw new RuntimeConflictException(
                "control rejected quota reservation with status "
                    + exception.getStatusCode().value());
        } catch (WebClientRequestException exception) {
            throw new RuntimeConflictException("control is unavailable for quota reservation");
        }
    }

    /**
     * 本地接单失败后幂等释放 Control Reservation。
     *
     * @param reservationId Reservation UUIDv7 字符串
     */
    @Override
    public void release(String reservationId) {
        try {
            webClient.post()
                .uri(
                    "/internal/v1/governance/quota-reservations/{reservationId}:transition",
                    reservationId)
                .headers(this::authorize)
                .bodyValue(Map.of("target", "RELEASED"))
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException | WebClientRequestException exception) {
            // Control TTL 是最终回收边界；释放失败不能覆盖原始接单异常。
        }
    }

    /**
     * 将 Runtime 原始 Usage 以来源 UUID 幂等提交到 Control Governance。
     *
     * @param record Usage 投影
     */
    @Override
    public void export(UsageExportRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourcePlane", "RUNTIME");
        body.put("sourceRecordId", record.id().asString());
        body.put("organizationId", record.organizationId().asString());
        body.put("projectId", record.projectId().asString());
        body.put("revisionId", record.revisionId().asString());
        body.put("deploymentId", record.deploymentId().asString());
        body.put("sessionId", record.sessionId().asString());
        body.put("turnId", record.turnId().asString());
        body.put("runId", record.runId().asString());
        body.put("usageType", record.usageType());
        body.put("provider", record.provider());
        record.model().ifPresent(value -> body.put("model", value));
        record.tool().ifPresent(value -> body.put("tool", value));
        body.put("inputTokens", record.inputTokens());
        body.put("outputTokens", record.outputTokens());
        body.put("cachedTokens", record.cachedTokens());
        body.put("embeddingTokens", record.embeddingTokens());
        body.put("toolCalls", record.toolCalls());
        body.put("sandboxDurationMs", record.sandboxDurationMs());
        body.put("estimated", record.estimated());
        body.put("costAmount", record.costAmount());
        body.put("occurredAt", record.occurredAt().toString());
        record.currency().ifPresent(value -> body.put("currency", value));
        try {
            webClient.post()
                .uri("/internal/v1/governance/usage-records")
                .headers(this::authorize)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException exception) {
            throw new RuntimeConflictException(
                "control rejected usage governance with status "
                    + exception.getStatusCode().value());
        } catch (WebClientRequestException exception) {
            throw new RuntimeConflictException("control is unavailable for usage governance");
        }
    }

    /**
     * 在 Runtime 本地事实提交后幂等汇聚高风险操作 Audit；Control 不可用时保留本地事实并等待运维重放。
     *
     * @param record 不含正文、参数或凭据的审计投影
     */
    @Override
    public void append(AuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceEventId", record.sourceEventId());
        body.put("sourcePlane", "RUNTIME");
        body.put("organizationId", record.organizationId().asString());
        body.put("projectId", record.projectId().asString());
        body.put("principalType",
            record.principalRef().equals("runtime-system") ? "SERVICE" : "USER");
        body.put("principalRef", record.principalRef());
        body.put("scopeType", "PROJECT");
        body.put("scopeRef", record.projectId().asString());
        body.put("action", record.action());
        body.put("result", record.result());
        body.put("resourceType", record.resourceType());
        body.put("resourceRef", record.resourceRef());
        body.put("diffSummary", record.diffSummary());
        record.traceId().ifPresent(value -> body.put("traceId", value));
        body.put("occurredAt", record.occurredAt().toString());
        try {
            webClient.post()
                .uri("/internal/v1/governance/audit-events")
                .headers(this::authorize)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException | WebClientRequestException exception) {
            // Runtime Event/Outbox 保留可重放事实；Control 暂时不可用不得覆盖已提交运行终态。
        }
    }

    /**
     * 将服务身份写入请求头；禁止记录或缓存 Token。
     *
     * @param headers HTTP Headers
     */
    private void authorize(HttpHeaders headers) {
        String token = serviceToken.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("runtime service identity token is not configured");
        }
        headers.setBearerAuth(token);
    }

    /**
     * 从 Canonical JSON 构造 Snapshot Envelope。
     *
     * @param revisionId 期望 Revision
     * @param json       Canonical Snapshot JSON
     * @return Snapshot 描述
     */
    private SnapshotDescriptor snapshot(RevisionId revisionId, String json) {
        JsonNode root = read(json);
        RevisionId actualRevision = RevisionId.parse(text(root, "revisionId"));
        if (!actualRevision.equals(revisionId)) {
            throw new RuntimeConflictException("control snapshot revision does not match request");
        }
        return new SnapshotDescriptor(
            actualRevision, SnapshotId.parse(text(root, "snapshotId")),
            new Checksum(text(root, "contentHash")), integer(root, "schemaVersion"),
            text(root, "runtimeProvider"), json);
    }

    /**
     * 解析 JSON 并保留原错误上下文类型。
     *
     * @param json JSON 文本
     * @return JSON 根节点
     */
    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new RuntimeConflictException("control response is not valid JSON");
        }
    }

    /**
     * 读取必需文本字段。
     *
     * @param root JSON 根节点
     * @param name 字段名
     * @return 非空文本
     */
    private String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new RuntimeConflictException("control response field is invalid: " + name);
        }
        return value.stringValue();
    }

    /**
     * 读取必需布尔字段。
     *
     * @param root JSON 根节点
     * @param name 字段名
     * @return 布尔值
     */
    private boolean bool(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isBoolean()) {
            throw new RuntimeConflictException("control response field is invalid: " + name);
        }
        return value.booleanValue();
    }

    /**
     * 读取可选文本字段；JSON null 返回空。
     *
     * @param root JSON 根节点
     * @param name 字段名
     * @return 可选文本
     */
    private Optional<String> nullableText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new RuntimeConflictException("control response field is invalid: " + name);
        }
        return Optional.of(value.stringValue());
    }

    /**
     * 读取必需正整数。
     *
     * @param root JSON 根节点
     * @param name 字段名
     * @return 正整数
     */
    private int integer(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.canConvertToInt() || value.intValue() < 1) {
            throw new RuntimeConflictException("control response field is invalid: " + name);
        }
        return value.intValue();
    }

    /**
     * 读取去重文本集合。
     *
     * @param root JSON 根节点
     * @param name 字段名
     * @return 文本集合
     */
    private Set<String> strings(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isArray()) {
            throw new RuntimeConflictException("control response field is invalid: " + name);
        }
        Set<String> values = new HashSet<>();
        value.forEach(item -> {
            if (!item.isString() || item.stringValue().isBlank()
                || !values.add(item.stringValue())) {
                throw new RuntimeConflictException(
                    "control response array is invalid: " + name);
            }
        });
        return Set.copyOf(values);
    }

    /**
     * 将支持的 Schema 版本排序后编码为 Header。
     *
     * @param versions Schema 版本
     * @return 逗号分隔 Header
     */
    private String joinIntegers(Set<Integer> versions) {
        return versions.stream().sorted().map(String::valueOf)
            .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    /**
     * 缓存已验证 Snapshot 与 Control ETag。
     *
     * @param snapshot Snapshot 快照
     * @param etag     HTTP ETag 标识
     * @author refinex
     */
    private record CachedSnapshot(SnapshotDescriptor snapshot, String etag) {

        /**
         * 校验缓存条目完整。
         */
        private CachedSnapshot {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            if (etag == null || etag.isBlank()) {
                throw new IllegalArgumentException("etag must not be blank");
            }
        }
    }

    /**
     * 表达 Snapshot HTTP 响应。
     *
     * @param body        Canonical JSON 正文
     * @param etag        HTTP ETag 标识
     * @param notModified 是否命中 304
     * @author refinex
     */
    private record SnapshotResponse(String body, String etag, boolean notModified) {
    }
}
