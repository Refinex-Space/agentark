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

package space.refinex.agentark.control.catalog.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import space.refinex.agentark.control.catalog.adapter.in.web.CatalogApiModels.*;
import space.refinex.agentark.control.catalog.application.CatalogApplicationService;
import space.refinex.agentark.control.catalog.application.CatalogVersionDiff;
import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.control.catalog.domain.CatalogVersion;
import space.refinex.agentark.control.catalog.domain.CatalogVersionStatus;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * 暴露项目 Scope 的资产目录、不可变版本、Diff 和 Skill Artifact Public API。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@PreAuthorize("isAuthenticated()")
public class CatalogController {

    /**
     * Catalog 应用服务。
     */
    private final CatalogApplicationService service;

    /**
     * 应用统一 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param service    Catalog 应用服务
     * @param jsonMapper JSON 映射器
     */
    public CatalogController(CatalogApplicationService service, JsonMapper jsonMapper) {
        this.service = java.util.Objects.requireNonNull(service, "service must not be null");
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param assetKind      资产分类
     * @param request        创建请求
     * @return 带地址的新稳定身份
     */
    @PostMapping("/catalog/{assetKind}")
    public ResponseEntity<CatalogAssetView> createAsset(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @Valid @RequestBody CreateAssetRequest request) {
        CatalogAssetKind kind = CatalogAssetKind.parse(assetKind);
        CatalogAsset created = service.createAsset(
            principal(authentication), ProjectId.parse(projectId), kind, request.key(),
            request.name(), request.description(), request.metadata());
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/catalog/" + kind.apiValue()
                + "/" + created.id().asString())).body(CatalogAssetView.from(created, jsonMapper));
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param assetKind      资产分类
     * @param cursor         可选不透明游标
     * @param limit          页大小
     * @return 资产游标页
     */
    @GetMapping("/catalog/{assetKind}")
    public CursorPage<CatalogAssetView> listAssets(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<CatalogAsset> page = service.listAssets(
            principal(authentication), ProjectId.parse(projectId),
            CatalogAssetKind.parse(assetKind), cursor, limit);
        return new CursorPage<>(
            page.items().stream().map(asset -> CatalogAssetView.from(asset, jsonMapper)).toList(),
            page.nextCursor(), page.hasMore());
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param assetKind      资产分类
     * @param assetId        稳定身份 UUIDv7
     * @param request        只追加版本请求
     * @return 带地址的新不可变版本
     */
    @PostMapping("/catalog/{assetKind}/{assetId}/versions")
    public ResponseEntity<CatalogVersionView> createVersion(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @PathVariable String assetId,
        @Valid @RequestBody CreateVersionRequest request) {
        CatalogAssetKind kind = CatalogAssetKind.parse(assetKind);
        CatalogVersion created = service.createVersion(
            principal(authentication), ProjectId.parse(projectId), kind, assetId,
            request.payload(), CatalogVersionStatus.valueOf(request.status()));
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/catalog/" + kind.apiValue() + "/" + assetId
                    + "/versions/" + created.id().asString()))
            .body(CatalogVersionView.from(created, jsonMapper));
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param assetKind      资产分类
     * @param assetId        稳定身份 UUIDv7
     * @param cursor         可选版本游标
     * @param limit          页大小
     * @return 不可变版本游标页
     */
    @GetMapping("/catalog/{assetKind}/{assetId}/versions")
    public CursorPage<CatalogVersionView> listVersions(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @PathVariable String assetId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        CursorPage<CatalogVersion> page = service.listVersions(
            principal(authentication), ProjectId.parse(projectId),
            CatalogAssetKind.parse(assetKind), assetId, cursor, limit);
        return new CursorPage<>(
            page.items().stream().map(version -> CatalogVersionView.from(version, jsonMapper)).toList(),
            page.nextCursor(), page.hasMore());
    }

    /**
     * @param authentication  已认证安全上下文
     * @param projectId       项目 UUIDv7
     * @param assetKind       资产分类
     * @param assetId         稳定身份 UUIDv7
     * @param baseVersionId   基准版本 UUIDv7
     * @param targetVersionId 目标版本 UUIDv7
     * @return 只含变化路径的版本 Diff
     */
    @GetMapping("/catalog/{assetKind}/{assetId}/versions:diff")
    public CatalogVersionDiff diff(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @PathVariable String assetId,
        @RequestParam String baseVersionId,
        @RequestParam String targetVersionId) {
        return service.diff(
            principal(authentication), ProjectId.parse(projectId),
            CatalogAssetKind.parse(assetKind), assetId, baseVersionId, targetVersionId);
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param assetKind      资产分类
     * @param assetId        稳定身份 UUIDv7
     * @param request        乐观锁归档请求
     * @return 无响应体
     */
    @PostMapping("/catalog/{assetKind}/{assetId}/archive")
    public ResponseEntity<Void> archiveAsset(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String assetKind,
        @PathVariable String assetId,
        @Valid @RequestBody ArchiveAssetRequest request) {
        service.archiveAsset(
            principal(authentication), ProjectId.parse(projectId),
            CatalogAssetKind.parse(assetKind), assetId, request.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param file           Skill Artifact
     * @param checksum       可选预期 SHA-256
     * @return 已完整性校验的 ObjectRef
     */
    @PostMapping(value = "/skill-artifacts", consumes = "multipart/form-data")
    public ResponseEntity<ObjectRefView> uploadSkillArtifact(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String checksum) {
        try {
            ObjectRef ref = service.uploadSkillArtifact(
                principal(authentication), ProjectId.parse(projectId), file.getInputStream(),
                file.getSize(), Optional.ofNullable(file.getContentType())
                    .orElse("application/octet-stream"),
                checksum == null ? Optional.empty() : Optional.of(new Checksum(checksum)));
            return ResponseEntity.created(ref.uri()).body(ObjectRefView.from(ref));
        } catch (IOException exception) {
            throw new IamConflictException("artifact request cannot be read");
        }
    }

    /**
     * @param authentication 已认证安全上下文
     * @return AgentArk 协议主体
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new IamAccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }
}
