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

package space.refinex.agentark.knowledge.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.knowledge.adapter.in.web.KnowledgeApiModels.*;
import space.refinex.agentark.knowledge.application.KnowledgeApplicationService;
import space.refinex.agentark.knowledge.application.KnowledgeConflictException;
import space.refinex.agentark.knowledge.domain.*;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 暴露项目 Scope 的 Knowledge Base、文档、Profile、Revision 与摄取描述 Public API。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@PreAuthorize("isAuthenticated()")
public class KnowledgeController {

    /**
     * Knowledge 应用服务。
     */
    private final KnowledgeApplicationService service;

    /**
     * 应用统一 JSON Mapper。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 Knowledge Public API。
     *
     * @param service    Knowledge 应用服务
     * @param jsonMapper 应用统一 JSON Mapper
     */
    public KnowledgeController(KnowledgeApplicationService service, JsonMapper jsonMapper) {
        this.service = java.util.Objects.requireNonNull(service, "service must not be null");
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建请求
     * @return 带 Location 的新 Knowledge Base
     */
    @PostMapping("/knowledge-bases")
    public ResponseEntity<KnowledgeBaseView> createKnowledgeBase(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        KnowledgeBase created = service.createKnowledgeBase(
            principal(authentication), ProjectId.parse(projectId), request.key(), request.name(),
            request.description());
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/knowledge-bases/" + created.id().value()))
            .body(KnowledgeBaseView.from(created));
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param cursor         可选不透明游标
     * @param limit          最大结果数
     * @return Knowledge Base 列表
     */
    @GetMapping("/knowledge-bases")
    public CursorPage<KnowledgeBaseView> listKnowledgeBases(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<KnowledgeBase> page = service.listKnowledgeBases(
            principal(authentication), ProjectId.parse(projectId), cursor, limit);
        return mapPage(page, KnowledgeBaseView::from);
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param request         创建请求
     * @return 新数据源
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/data-sources")
    public ResponseEntity<DataSourceView> createDataSource(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody CreateDataSourceRequest request) {
        DataSource created = service.createDataSource(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeBaseId.parse(knowledgeBaseId), DataSourceType.valueOf(request.type()),
            request.name(), jsonMapper.writeValueAsString(request.descriptor()));
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/knowledge-bases/" + knowledgeBaseId
                + "/data-sources/" + created.id().value())).body(DataSourceView.from(created, jsonMapper));
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return 数据源列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/data-sources")
    public CursorPage<DataSourceView> listDataSources(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<DataSource> page = service.listDataSources(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeBaseId.parse(knowledgeBaseId), cursor, limit);
        return mapPage(page, value -> DataSourceView.from(value, jsonMapper));
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param dataSourceId    UPLOAD 数据源 UUIDv7
     * @param title           文档标题
     * @param metadataJson    可选字符串值 JSON 对象
     * @param checksum        可选规范 SHA-256
     * @param file            原始文件
     * @return 首个 Document Revision
     */
    @PostMapping(value = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = "multipart/form-data")
    public ResponseEntity<DocumentRevisionView> uploadDocument(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @RequestParam String dataSourceId,
        @RequestParam String title,
        @RequestParam(defaultValue = "{}") String metadataJson,
        @RequestParam(required = false) String checksum,
        @RequestPart("file") MultipartFile file) {
        try {
            DocumentRevision created = service.uploadDocument(
                principal(authentication), ProjectId.parse(projectId),
                KnowledgeBaseId.parse(knowledgeBaseId), DataSourceId.parse(dataSourceId), title,
                Optional.ofNullable(file.getOriginalFilename()).filter(value -> !value.isBlank())
                    .orElse("document.bin"),
                stringMap(metadataJson), file.getInputStream(), file.getSize(),
                Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"),
                checksum == null ? Optional.empty() : Optional.of(new Checksum(checksum)));
            return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/knowledge-bases/" + knowledgeBaseId
                    + "/documents/" + created.documentId().value() + "/revisions/"
                    + created.id().value())).body(DocumentRevisionView.from(created));
        } catch (IOException exception) {
            throw new KnowledgeConflictException("document upload cannot be stored");
        }
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return 文档稳定身份和 ACL 列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public CursorPage<DocumentView> listDocuments(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<Document> page = service.listDocuments(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeBaseId.parse(knowledgeBaseId), cursor, limit);
        return mapPage(page, DocumentView::from);
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param profileKind    Profile 类型 URL Segment
     * @param request        创建请求
     * @return 新不可变 Profile
     */
    @PostMapping("/knowledge-profiles/{profileKind}")
    public ResponseEntity<KnowledgeProfileView> createProfile(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String profileKind,
        @Valid @RequestBody CreateProfileRequest request) {
        ProjectId parsedProjectId = ProjectId.parse(projectId);
        AgentArkPrincipal parsedPrincipal = principal(authentication);
        KnowledgeProfileStatus status = KnowledgeProfileStatus.valueOf(request.status());
        String config = jsonMapper.writeValueAsString(request.config());
        KnowledgeProfileView created = switch (ProfileKind.parse(profileKind)) {
            case PARSER -> KnowledgeProfileView.from(service.createParserProfile(
                parsedPrincipal, parsedProjectId, request.key(), config, status), jsonMapper);
            case CHUNK -> KnowledgeProfileView.from(service.createChunkProfile(
                parsedPrincipal, parsedProjectId, request.key(), config, status), jsonMapper);
            case EMBEDDING -> KnowledgeProfileView.from(service.createEmbeddingProfile(
                parsedPrincipal, parsedProjectId, request.key(), config,
                Optional.ofNullable(request.credentialSecretRef()).filter(value -> !value.isBlank())
                    .map(SecretRef::parse), status), jsonMapper);
            case RETRIEVAL -> KnowledgeProfileView.from(service.createRetrievalProfile(
                parsedPrincipal, parsedProjectId, request.key(), config, status), jsonMapper);
        };
        return ResponseEntity.status(201).body(created);
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param request         创建请求
     * @return CREATED Knowledge Revision
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/revisions")
    public ResponseEntity<KnowledgeRevisionView> createKnowledgeRevision(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @Valid @RequestBody CreateKnowledgeRevisionRequest request) {
        KnowledgeRevision created = service.createKnowledgeRevision(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeBaseId.parse(knowledgeBaseId),
            request.documentRevisionIds().stream().map(DocumentRevisionId::parse).toList(),
            ParserProfileId.parse(request.parserProfileId()),
            ChunkProfileId.parse(request.chunkProfileId()),
            EmbeddingProfileId.parse(request.embeddingProfileId()),
            RetrievalProfileId.parse(request.retrievalProfileId()));
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/knowledge-bases/" + knowledgeBaseId
                + "/revisions/" + created.id().value())).body(KnowledgeRevisionView.from(created));
    }

    /**
     * @param authentication  已认证上下文
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base UUIDv7
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return Knowledge Revision 列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/revisions")
    public CursorPage<KnowledgeRevisionView> listKnowledgeRevisions(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<KnowledgeRevision> page = service.listKnowledgeRevisions(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeBaseId.parse(knowledgeBaseId), cursor, limit);
        return mapPage(page, KnowledgeRevisionView::from);
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param revisionId     Knowledge Revision UUIDv7
     * @param request        幂等创建请求
     * @return 摄取请求描述
     */
    @PostMapping("/knowledge-revisions/{revisionId}/ingestion-requests")
    public ResponseEntity<IngestionRequestView> requestIngestion(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String revisionId,
        @Valid @RequestBody CreateIngestionRequest request) {
        IngestionJobDescriptor descriptor = service.requestIngestion(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeRevisionId.parse(revisionId), request.idempotencyKey());
        return ResponseEntity.accepted().body(IngestionRequestView.from(descriptor));
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param revisionId     Knowledge Revision UUIDv7
     * @return DEPRECATED Revision
     */
    @PostMapping("/knowledge-revisions/{revisionId}/deprecate")
    public KnowledgeRevisionView deprecateRevision(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String revisionId) {
        return KnowledgeRevisionView.from(service.transitionRevision(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeRevisionId.parse(revisionId), KnowledgeRevisionStatus.DEPRECATED, ""));
    }

    /**
     * @param authentication 已认证上下文
     * @param projectId      项目 UUIDv7
     * @param revisionId     Knowledge Revision UUIDv7
     * @return DELETING Revision
     */
    @PostMapping("/knowledge-revisions/{revisionId}/deletion")
    public KnowledgeRevisionView requestDeletion(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String revisionId) {
        return KnowledgeRevisionView.from(service.transitionRevision(
            principal(authentication), ProjectId.parse(projectId),
            KnowledgeRevisionId.parse(revisionId), KnowledgeRevisionStatus.DELETING, ""));
    }

    /**
     * 在保留同一不透明游标的前提下转换领域结果页。
     *
     * @param page   领域结果页
     * @param mapper 单项视图转换器
     * @param <S>    领域值类型
     * @param <T>    Public API 视图类型
     * @return Public API 结果页
     */
    private static <S, T> CursorPage<T> mapPage(
        CursorPage<S> page, java.util.function.Function<S, T> mapper) {
        return new CursorPage<>(
            page.items().stream().map(mapper).toList(), page.nextCursor(), page.hasMore());
    }

    /**
     * 从 Spring Security 上下文读取 AgentArk 协议主体。
     *
     * @param authentication 已认证上下文
     * @return AgentArk 协议主体
     */
    private static AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new AccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }

    /**
     * 解析只允许字符串值的非敏感元数据 JSON。
     *
     * @param metadataJson 元数据 JSON
     * @return 字符串键值映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(String metadataJson) {
        Map<String, Object> raw = jsonMapper.readValue(metadataJson, Map.class);
        Map<String, String> converted = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("document metadata values must be strings");
            }
            converted.put(key, text);
        });
        return Map.copyOf(converted);
    }
}
