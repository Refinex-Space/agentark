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

package space.refinex.agentark.knowledge.adapter.out.vector.qdrant;

import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.knowledge.port.EmbeddedChunk;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 Qdrant 1.18 REST API 实现版本隔离向量写入、校验、检索与删除。
 *
 * <p>所有 Filter 均由该类从可信 {@link VectorScope} 构造，调用方无法注入或移除租户条件。
 *
 * @author refinex
 */
public final class QdrantKnowledgeVectorStore implements KnowledgeVectorStore {

    /**
     * 需要创建 Payload Index 的强制字段。
     */
    private static final List<String> INDEX_FIELDS = List.of(
        "organization_id", "project_id", "knowledge_revision_id", "document_id");

    /**
     * Qdrant REST 配置。
     */
    private final QdrantProperties properties;

    /**
     * JDK HTTP 客户端。
     */
    private final HttpClient httpClient;

    /**
     * JSON 编解码器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 可选 API Key Provider。
     */
    private final QdrantApiKeyProvider apiKeyProvider;

    /**
     * Collection 和 Payload Index 的 Single Flight 初始化状态。
     */
    private final AtomicReference<CompletableFuture<Void>> initialization = new AtomicReference<>();

    /**
     * 创建 Qdrant REST Adapter。
     *
     * @param properties     Qdrant 配置
     * @param httpClient     HTTP Client
     * @param jsonMapper     JSON Mapper
     * @param apiKeyProvider API Key Provider
     */
    public QdrantKnowledgeVectorStore(
        QdrantProperties properties,
        HttpClient httpClient,
        JsonMapper jsonMapper,
        QdrantApiKeyProvider apiKeyProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.apiKeyProvider = Objects.requireNonNull(
            apiKeyProvider, "apiKeyProvider must not be null");
    }

    /**
     * 幂等写入固定 Revision Point，并把完整租户、文档和清单摘要写入 Payload。
     *
     * @param request 写入请求
     * @return 异步完成信号
     */
    @Override
    public CompletionStage<Void> upsert(VectorWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireDimension(request.chunks());
        List<Map<String, Object>> points = request.chunks().stream()
            .map(embedded -> point(request, embedded))
            .toList();
        return ensureCollection().thenCompose(ignored -> sendJson(
            "PUT", collectionPath("/points?wait=true"), Map.of("points", points)))
            .thenApply(ignored -> null);
    }

    /**
     * 精确统计同时匹配固定 Revision 和完整摘要的 Point。
     *
     * @param request 验证请求
     * @return 数量与摘要均匹配时为 {@code true}
     */
    @Override
    public CompletionStage<Boolean> verify(VectorVerificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> filter = scopeFilter(request.scope(), Set.of());
        List<Object> must = mutableMust(filter);
        must.add(match("revision_checksum", request.expectedChecksum().value()));
        return ensureCollection().thenCompose(ignored -> sendJson(
            "POST", collectionPath("/points/count"),
            Map.of("filter", filter, "exact", true)))
            .thenApply(json -> integer(required(required(json, "result"), "count"))
                == request.expectedCount());
    }

    /**
     * 使用 Adapter 强制构造的租户、Revision 与文档 ACL Filter 查询。
     *
     * @param request 检索请求
     * @return Provider 中立命中列表
     */
    @Override
    public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.allowedDocumentIds().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        requireDimension(request.queryVector());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", floats(request.queryVector()));
        body.put("filter", scopeFilter(request.scope(), request.allowedDocumentIds()));
        body.put("limit", request.limit());
        body.put("score_threshold", request.scoreThreshold());
        body.put("with_payload", true);
        return ensureCollection().thenCompose(ignored -> sendJson(
            "POST", collectionPath("/points/query"), body))
            .thenApply(this::mapSearchHits);
    }

    /**
     * 只删除固定组织、项目和 Revision 范围内的 Point。
     *
     * @param scope 可信租户范围
     * @return 异步完成信号
     */
    @Override
    public CompletionStage<Void> delete(VectorScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return ensureCollection().thenCompose(ignored -> sendJson(
            "POST", collectionPath("/points/delete?wait=true"),
            Map.of("filter", scopeFilter(scope, Set.of()))))
            .thenApply(ignored -> null);
    }

    /**
     * 初始化 Collection 和四个 Keyword Payload Index，同一进程并发调用只执行一次。
     *
     * @return 初始化完成信号
     */
    private CompletionStage<Void> ensureCollection() {
        CompletableFuture<Void> existing = initialization.get();
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Void> created = initializeCollection().toCompletableFuture();
        if (initialization.compareAndSet(null, created)) {
            created.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    initialization.compareAndSet(created, null);
                }
            });
            return created;
        }
        return initialization.get();
    }

    /**
     * 检查 Collection；不存在时创建，并幂等创建 Payload Index。
     *
     * @return 初始化完成信号
     */
    private CompletionStage<Void> initializeCollection() {
        return send("GET", collectionPath(""), Optional.empty())
            .thenCompose(response -> {
                if (response.statusCode() == 200) {
                    return CompletableFuture.completedFuture(null);
                }
                if (response.statusCode() != 404) {
                    return CompletableFuture.failedFuture(unavailable(
                        "qdrant collection lookup failed with status " + response.statusCode(), null));
                }
                return sendJson("PUT", collectionPath(""), Map.of(
                    "vectors", Map.of("size", properties.dimension(), "distance", "Cosine")))
                    .thenApply(ignored -> null);
            })
            .thenCompose(ignored -> {
                CompletableFuture<?>[] indexes = INDEX_FIELDS.stream()
                    .map(field -> sendJson("PUT", collectionPath("/index?wait=true"),
                        Map.of("field_name", field, "field_schema", "keyword"))
                        .toCompletableFuture())
                    .toArray(CompletableFuture[]::new);
                return CompletableFuture.allOf(indexes);
            });
    }

    /**
     * 构造一个 Qdrant Point 及强制 Payload。
     *
     * @param request  写入请求
     * @param embedded 带向量 Chunk
     * @return JSON 兼容 Point Map
     */
    private Map<String, Object> point(VectorWriteRequest request, EmbeddedChunk embedded) {
        KnowledgeChunk chunk = embedded.chunk();
        DocumentId documentId = request.documentIds().get(chunk.documentRevisionId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organization_id", request.scope().organizationId().asString());
        payload.put("project_id", request.scope().projectId().asString());
        payload.put("knowledge_revision_id", request.scope().knowledgeRevisionId().asString());
        payload.put("document_id", documentId.asString());
        payload.put("document_revision_id", chunk.documentRevisionId().asString());
        payload.put("chunk_key", chunk.key());
        payload.put("chunk_text", chunk.text());
        payload.put("chunk_metadata", chunk.metadata());
        payload.put("source_trust", "UNTRUSTED_EXTERNAL");
        payload.put("revision_checksum", request.revisionChecksum().value());
        return Map.of(
            "id", deterministicPointId(request.scope(), chunk).toString(),
            "vector", floats(embedded.vector()),
            "payload", payload);
    }

    /**
     * 从可信 Scope 和可选文档白名单构造不可删除的 Filter。
     *
     * @param scope              可信范围
     * @param allowedDocumentIds 已授权文档白名单，空集合表示不附加文档条件
     * @return Qdrant Filter
     */
    private Map<String, Object> scopeFilter(
        VectorScope scope, Set<DocumentId> allowedDocumentIds) {
        List<Object> must = new ArrayList<>();
        must.add(match("organization_id", scope.organizationId().asString()));
        must.add(match("project_id", scope.projectId().asString()));
        must.add(match("knowledge_revision_id", scope.knowledgeRevisionId().asString()));
        if (!allowedDocumentIds.isEmpty()) {
            List<String> values = allowedDocumentIds.stream()
                .map(DocumentId::asString).sorted().toList();
            must.add(Map.of("key", "document_id", "match", Map.of("any", values)));
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", must);
        return filter;
    }

    /**
     * 返回 Filter 内可变的 must 列表。
     *
     * @param filter Filter Map
     * @return must 列表
     */
    @SuppressWarnings("unchecked")
    private List<Object> mutableMust(Map<String, Object> filter) {
        return (List<Object>) filter.get("must");
    }

    /**
     * 创建 Keyword 精确匹配条件。
     *
     * @param key   Payload 字段
     * @param value 匹配值
     * @return Qdrant 条件
     */
    private Map<String, Object> match(String key, String value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    /**
     * 把 Query API 响应转换为 Provider 中立命中。
     *
     * @param root Qdrant 响应
     * @return 命中列表
     */
    private List<VectorSearchHit> mapSearchHits(JsonNode root) {
        JsonNode points = required(required(root, "result"), "points");
        if (!points.isArray()) {
            throw unavailable("qdrant query result is not an array", null);
        }
        List<VectorSearchHit> hits = new ArrayList<>();
        for (JsonNode point : points) {
            JsonNode payload = required(point, "payload");
            Map<String, String> metadata = new LinkedHashMap<>();
            JsonNode metadataNode = payload.get("chunk_metadata");
            if (metadataNode != null && metadataNode.isObject()) {
                metadataNode.forEachEntry((key, value) -> metadata.put(key, value.asString()));
            }
            metadata.put("source_trust", "UNTRUSTED_EXTERNAL");
            KnowledgeChunk chunk = new KnowledgeChunk(
                text(payload, "chunk_key"),
                DocumentRevisionId.parse(text(payload, "document_revision_id")),
                text(payload, "chunk_text"), metadata);
            hits.add(new VectorSearchHit(
                chunk, DocumentId.parse(text(payload, "document_id")),
                decimal(required(point, "score"))));
        }
        return List.copyOf(hits);
    }

    /**
     * 向 Qdrant 发送 JSON 请求并解析成功响应。
     *
     * @param method HTTP 方法
     * @param uri    目标 URI
     * @param body   JSON 兼容请求体
     * @return JSON 响应
     */
    private CompletionStage<JsonNode> sendJson(String method, URI uri, Object body) {
        String json;
        try {
            json = jsonMapper.writeValueAsString(body);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(unavailable(
                "qdrant request serialization failed", exception));
        }
        return send(method, uri, Optional.of(json)).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable(
                    "qdrant request failed with status " + response.statusCode(), null);
            }
            try {
                return jsonMapper.readTree(response.body());
            } catch (RuntimeException exception) {
                throw unavailable("qdrant response parsing failed", exception);
            }
        });
    }

    /**
     * 发送一个受超时和可选 API Key 保护的 HTTP 请求。
     *
     * @param method HTTP 方法
     * @param uri    目标 URI
     * @param body   可选 JSON 请求体
     * @return 原始响应
     */
    private CompletionStage<HttpResponse<String>> send(
        String method, URI uri, Optional<String> body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(properties.timeout())
            .header("Accept", "application/json");
        if (body.isPresent()) {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body.orElseThrow()));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        Optional<char[]> resolved = apiKeyProvider.resolve();
        if (resolved.isPresent()) {
            char[] secret = resolved.orElseThrow();
            try {
                builder.header("api-key", new String(secret));
            } finally {
                Arrays.fill(secret, '\0');
            }
        }
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
            .exceptionally(failure -> {
                Throwable cause = failure instanceof CompletionException
                    ? failure.getCause() : failure;
                throw unavailable("qdrant request transport failed", cause);
            });
    }

    /**
     * 创建 Collection 子路径 URI。
     *
     * @param suffix Collection 后缀
     * @return 完整 URI
     */
    private URI collectionPath(String suffix) {
        return URI.create(properties.endpoint() + "/collections/"
            + properties.collection() + suffix);
    }

    /**
     * 检查所有向量均匹配 Collection 固定维度。
     *
     * @param chunks 带向量 Chunk
     */
    private void requireDimension(List<EmbeddedChunk> chunks) {
        if (chunks.stream().anyMatch(chunk -> chunk.vector().length != properties.dimension())) {
            throw new IllegalArgumentException("vector dimension does not match qdrant collection");
        }
    }

    /**
     * 检查查询向量匹配 Collection 固定维度。
     *
     * @param vector 查询向量
     */
    private void requireDimension(float[] vector) {
        if (vector.length != properties.dimension()) {
            throw new IllegalArgumentException("query vector dimension does not match qdrant collection");
        }
    }

    /**
     * 把 float 数组转换为 JSON 数值列表。
     *
     * @param vector 浮点向量
     * @return 数值列表
     */
    private List<Float> floats(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return List.copyOf(values);
    }

    /**
     * 根据固定 Scope、文档修订和 Chunk Key 生成稳定 UUID Point ID。
     *
     * @param scope Scope
     * @param chunk Chunk
     * @return 稳定 UUID
     */
    private UUID deterministicPointId(VectorScope scope, KnowledgeChunk chunk) {
        try {
            String source = scope.organizationId().asString() + ':'
                + scope.projectId().asString() + ':'
                + scope.knowledgeRevisionId().asString() + ':'
                + chunk.documentRevisionId().asString() + ':' + chunk.key();
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    /**
     * 读取必需 JSON 字段。
     *
     * @param node  JSON 节点
     * @param field 字段名称
     * @return 字段节点
     */
    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw unavailable("qdrant response misses field " + field, null);
        }
        return value;
    }

    /**
     * 读取必需文本字段。
     *
     * @param node  JSON 节点
     * @param field 字段名称
     * @return 文本值
     */
    private String text(JsonNode node, String field) {
        String value = required(node, field).asString();
        if (value.isBlank()) {
            throw unavailable("qdrant response contains blank field " + field, null);
        }
        return value;
    }

    /**
     * 读取整数字段。
     *
     * @param node JSON 节点
     * @return 整数值
     */
    private int integer(JsonNode node) {
        if (!node.canConvertToInt()) {
            throw unavailable("qdrant response count is invalid", null);
        }
        return node.intValue();
    }

    /**
     * 读取有限零到一分数字段。
     *
     * @param node JSON 节点
     * @return 分数
     */
    private double decimal(JsonNode node) {
        double value = node.doubleValue();
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw unavailable("qdrant response score is invalid", null);
        }
        return value;
    }

    /**
     * 创建不携带响应正文的稳定异常。
     *
     * @param message 安全上下文
     * @param cause   原始异常
     * @return Qdrant 不可用异常
     */
    private QdrantUnavailableException unavailable(String message, Throwable cause) {
        return new QdrantUnavailableException(message, cause);
    }
}
