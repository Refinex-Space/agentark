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

package space.refinex.agentark.control.catalog.application;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.catalog.CatalogProperties;
import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.*;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.storage.ObjectMetadata;
import space.refinex.agentark.foundation.storage.ObjectNamespace;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.storage.PutObjectCommand;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.McpServerVersionId;
import space.refinex.agentark.kernel.id.McpToolDescriptorId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.StrongId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 编排资产授权、只追加版本、版本 Diff、审计和 Skill Object Store 提交。
 *
 * @author refinex
 */
public class CatalogApplicationService {

    /**
     * 资产持久化端口。
     */
    private final CatalogRepository repository;

    /**
     * IAM 租户目录端口。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * IAM 应用授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 事务感知审计发布器。
     */
    private final IamAuditPublisher auditPublisher;

    /**
     * 分类载荷校验器。
     */
    private final CatalogPayloadValidator payloadValidator;

    /**
     * SecretRef 同项目存在性检查端口。
     */
    private final SecretRepository secretRepository;

    /**
     * 可选对象存储；生产未提供时 Skill 上传显式失败。
     */
    private final Optional<ObjectStore> objectStore;

    /**
     * Catalog 配置。
     */
    private final CatalogProperties properties;

    /**
     * Skill 签名、SBOM、扫描和许可证供应链验证器。
     */
    private final SkillSupplyChainVerifier skillSupplyChainVerifier;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * JSON 映射器，仅用于无值差异路径计算。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param repository               资产持久化端口
     * @param tenantRepository         IAM 租户端口
     * @param authorizationService     IAM 授权服务
     * @param auditPublisher           审计发布器
     * @param payloadValidator         载荷校验器
     * @param secretRepository         SecretRef 检查端口
     * @param objectStore              可选对象存储
     * @param properties               Catalog 配置
     * @param skillSupplyChainVerifier Skill 供应链验证器
     * @param clock                    UTC 时钟
     * @param jsonMapper               JSON 映射器
     */
    public CatalogApplicationService(
        CatalogRepository repository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        IamAuditPublisher auditPublisher,
        CatalogPayloadValidator payloadValidator,
        SecretRepository secretRepository,
        Optional<ObjectStore> objectStore,
        CatalogProperties properties,
        SkillSupplyChainVerifier skillSupplyChainVerifier,
        Clock clock,
        JsonMapper jsonMapper) {

        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.payloadValidator = Objects.requireNonNull(payloadValidator, "payloadValidator must not be null");
        this.secretRepository = Objects.requireNonNull(secretRepository, "secretRepository must not be null");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillSupplyChainVerifier = Objects.requireNonNull(skillSupplyChainVerifier, "skillSupplyChainVerifier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * @param principal   已认证主体
     * @param projectId   项目标识
     * @param kind        资产分类
     * @param key         稳定 Key
     * @param name        显示名称
     * @param description 可选说明
     * @param metadata    分类元数据
     * @return 新稳定身份
     */
    @Transactional
    public CatalogAsset createAsset(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind, String key,
                                    String name, String description, Map<String, Object> metadata) {

        Project project = authorize(principal, projectId, PermissionRegistry.CATALOG_MANAGE);
        Instant now = Instant.now(clock);
        CatalogAsset asset = new CatalogAsset(
            kind.generateId(),
            kind,
            project.organizationId(),
            project.id(),
            key,
            name,
            description,
            payloadValidator.validateMetadata(kind, metadata),
            CatalogAssetStatus.ACTIVE,
            0,
            now,
            now);
        repository.insertAsset(asset, actor(principal));
        audit(principal, "catalog.create", kind.apiValue(), asset.id(), project, now);
        return asset;
    }

    /**
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param kind      资产分类
     * @param cursor    可选不透明游标
     * @param limit     正数页大小
     * @return 资产游标页
     */
    @Transactional(readOnly = true)
    public CursorPage<CatalogAsset> listAssets(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind,
                                               String cursor, int limit) {

        authorize(principal, projectId, PermissionRegistry.CATALOG_READ);
        int pageSize = requireLimit(limit);
        List<CatalogAsset> loaded = repository.listAssets(kind, projectId, CatalogCursorCodec.decode(cursor, ""), pageSize + 1);
        boolean hasMore = loaded.size() > pageSize;
        List<CatalogAsset> items = loaded.stream().limit(pageSize).toList();
        Optional<String> next = hasMore
            ? Optional.of(CatalogCursorCodec.encode(items.getLast().key()))
            : Optional.empty();
        return new CursorPage<>(items, next, hasMore);
    }

    /**
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param kind            资产分类
     * @param assetId         稳定身份
     * @param expectedVersion 乐观锁版本
     */
    @Transactional
    public void archiveAsset(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind, String assetId, long expectedVersion) {
        Project project = authorize(principal, projectId, PermissionRegistry.CATALOG_MANAGE);
        StrongId id = kind.parseId(assetId);
        Instant now = Instant.now(clock);
        if (repository.archiveAsset(
            kind, projectId, id, expectedVersion, actor(principal), now) != 1) {
            throw new IamConflictException("asset archive precondition failed");
        }
        audit(principal, "catalog.archive", kind.apiValue(), id, project, now);
    }

    /**
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param kind      资产分类
     * @param assetId   稳定身份
     * @param payload   版本载荷
     * @param status    创建时版本状态
     * @return 新不可变版本
     */
    @Transactional
    public CatalogVersion createVersion(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind,
                                        String assetId, Map<String, Object> payload, CatalogVersionStatus status) {

        Project project = authorize(principal, projectId, PermissionRegistry.CATALOG_MANAGE);
        StrongId ownerId = kind.parseId(assetId);
        CatalogValidatedPayload validated = payloadValidator.validateVersion(kind, payload);
        if (payloadValidator.secretRefs(payload).stream().anyMatch(ref -> !secretRepository.existsReference(projectId, ref))) {
            throw new IamNotFoundException("secret reference is not visible");
        }
        if (kind == CatalogAssetKind.SKILL) {
            verifySkillArtifact(payload);
        }

        long number = repository.nextVersionNumber(kind, projectId, ownerId)
            .orElseThrow(() -> new IamNotFoundException("asset is missing or archived"));
        Instant now = Instant.now(clock);
        StrongId versionId = kind.generateVersionId();
        CatalogVersion version = new CatalogVersion(versionId, kind, project.organizationId(), project.id(), ownerId, number,
            validated.canonicalJson(), Checksum.sha256(validated.canonicalJson()), status, now);
        List<McpToolDescriptorSnapshot> tools = descriptors(kind, project, versionId, validated.toolPayloads(), now);
        repository.insertVersion(version, tools, actor(principal));
        audit(principal, "catalog.version.create", kind.apiValue(), version.id(), project, now);
        return version;
    }

    /**
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param kind      资产分类
     * @param assetId   稳定身份
     * @param cursor    可选版本号游标
     * @param limit     页大小
     * @return 不可变版本游标页
     */
    @Transactional(readOnly = true)
    public CursorPage<CatalogVersion> listVersions(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind,
                                                   String assetId, String cursor, int limit) {

        authorize(principal, projectId, PermissionRegistry.CATALOG_READ);
        StrongId ownerId = kind.parseId(assetId);
        int pageSize = requireLimit(limit);
        long after = parseVersionCursor(cursor);
        List<CatalogVersion> loaded = repository.listVersions(kind, projectId, ownerId, after, pageSize + 1);
        boolean hasMore = loaded.size() > pageSize;
        List<CatalogVersion> items = loaded.stream().limit(pageSize).toList();
        Optional<String> next = hasMore
            ? Optional.of(CatalogCursorCodec.encode(Long.toString(items.getLast().versionNumber())))
            : Optional.empty();
        return new CursorPage<>(items, next, hasMore);
    }

    /**
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param kind            资产分类
     * @param assetId         稳定身份
     * @param baseVersionId   基准版本
     * @param targetVersionId 目标版本
     * @return 不回显值的变更路径
     */
    @Transactional(readOnly = true)
    public CatalogVersionDiff diff(AgentArkPrincipal principal, ProjectId projectId, CatalogAssetKind kind, String assetId,
                                   String baseVersionId, String targetVersionId) {

        authorize(principal, projectId, PermissionRegistry.CATALOG_READ);
        StrongId ownerId = kind.parseId(assetId);
        CatalogVersion base = requiredVersion(kind, projectId, ownerId, kind.parseVersionId(baseVersionId));
        CatalogVersion target = requiredVersion(kind, projectId, ownerId, kind.parseVersionId(targetVersionId));
        return new CatalogVersionDiff(base.id(), target.id(), changedPaths(base.payloadJson(), target.payloadJson()));
    }

    /**
     * @param principal        已认证主体
     * @param projectId        项目标识
     * @param content          Artifact 输入流
     * @param size             声明字节数
     * @param contentType      媒体类型
     * @param expectedChecksum 可选预期校验和
     * @return 已完整性校验的持久 ObjectRef
     */
    public ObjectRef uploadSkillArtifact(AgentArkPrincipal principal, ProjectId projectId, InputStream content, long size,
                                         String contentType, Optional<Checksum> expectedChecksum) {

        authorize(principal, projectId, PermissionRegistry.CATALOG_MANAGE);
        if (size < 0 || size > properties.getMaxArtifactSize().toBytes()) {
            throw new IllegalArgumentException("artifact size exceeds configured limit");
        }

        ObjectStore store = objectStore.orElseThrow(() -> new IamConflictException("object store is not configured"));
        try {
            return store.put(new PutObjectCommand(new ObjectNamespace("skill-artifacts"), content, size, contentType, expectedChecksum));
        } catch (IOException exception) {
            throw new IamConflictException("artifact upload failed");
        }
    }

    /**
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param permission 必需权限
     * @return 已授权项目
     */
    private Project authorize(AgentArkPrincipal principal, ProjectId projectId, String permission) {
        Project project = tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project is not visible"));
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(projectId), Optional.empty(), permission);
        return project;
    }

    /**
     * @param payload Skill 版本载荷
     */
    @SuppressWarnings("unchecked")
    private void verifySkillArtifact(Map<String, Object> payload) {
        Map<String, Object> artifact = (Map<String, Object>) payload.get("artifact");
        ObjectRef ref = ObjectRef.of(
            String.valueOf(artifact.get("uri")),
            new Checksum(String.valueOf(artifact.get("checksum"))),
            ((Number) artifact.get("size")).longValue(),
            String.valueOf(artifact.get("mediaType")));
        ObjectStore store = objectStore.orElseThrow(() -> new IamConflictException("object store is not configured"));

        try {
            ObjectMetadata metadata = store.head(ref);
            if (!metadata.checksum().equals(ref.checksum()) || metadata.size() != ref.size() || !metadata.contentType().equals(ref.mediaType())) {
                throw new IamConflictException("artifact metadata does not match object store");
            }
            skillSupplyChainVerifier.verify(payload, store);
        } catch (IOException exception) {
            throw new IamConflictException("artifact is missing or inaccessible");
        }
    }

    /**
     * @param kind      资产分类
     * @param project   项目
     * @param versionId 版本标识
     * @param payloads  Tool 载荷
     * @param now       创建时刻
     * @return MCP Tool Descriptor 快照
     */
    private List<McpToolDescriptorSnapshot> descriptors(CatalogAssetKind kind, Project project, StrongId versionId,
                                                        List<Map<String, Object>> payloads, Instant now) {

        if (kind != CatalogAssetKind.MCP_SERVER) {
            return List.of();
        }

        McpServerVersionId serverVersionId = (McpServerVersionId) versionId;
        return payloads.stream().map(payload -> {
            String json = write(payload);
            return new McpToolDescriptorSnapshot(
                McpToolDescriptorId.generate(),
                project.organizationId(),
                project.id(),
                serverVersionId,
                String.valueOf(payload.get("name")),
                Objects.toString(payload.get("description"), ""),
                write(payload.get("argumentSchema")),
                String.valueOf(payload.get("accessMode")),
                String.valueOf(payload.get("riskLevel")),
                String.valueOf(payload.get("idempotency")),
                write(payload.get("permissionMetadata")),
                Checksum.sha256(json),
                now);
        }).toList();
    }

    /**
     * @param kind      资产分类
     * @param projectId 项目标识
     * @param ownerId   Owner 标识
     * @param versionId 版本标识
     * @return 必须存在的版本
     */
    private CatalogVersion requiredVersion(CatalogAssetKind kind, ProjectId projectId, StrongId ownerId, StrongId versionId) {
        return repository.findVersion(kind, projectId, ownerId, versionId)
            .orElseThrow(() -> new IamNotFoundException("asset version is not visible"));
    }

    /**
     * @param baseJson   基准 JSON
     * @param targetJson 目标 JSON
     * @return 发生变化的顶层 JSON Pointer
     */
    @SuppressWarnings("unchecked")
    private List<String> changedPaths(String baseJson, String targetJson) {
        try {
            Map<String, Object> base = jsonMapper.readValue(baseJson, Map.class);
            Map<String, Object> target = jsonMapper.readValue(targetJson, Map.class);
            Set<String> keys = new TreeSet<>();
            keys.addAll(base.keySet());
            keys.addAll(target.keySet());

            return keys.stream()
                .filter(key -> !Objects.equals(base.get(key), target.get(key)))
                .map(key -> "/" + key.replace("~", "~0").replace("/", "~1"))
                .toList();
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("stored catalog JSON is invalid", exception);
        }
    }

    /**
     * @param value 待序列化值
     * @return JSON 字符串
     */
    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("value cannot be serialized as JSON", exception);
        }
    }

    /**
     * @param cursor 可选版本游标
     * @return 非负版本号
     */
    private long parseVersionCursor(String cursor) {
        String decoded = CatalogCursorCodec.decode(cursor, "0");
        try {
            long value = Long.parseLong(decoded);
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("version cursor is invalid", exception);
        }
    }

    /**
     * @param limit 请求页大小
     * @return 1 到 100 的页大小
     */
    private int requireLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return limit;
    }

    /**
     * @param principal 已认证主体
     * @return 审计使用的稳定非秘密主体引用
     */
    private String actor(AgentArkPrincipal principal) {
        return principal.subject();
    }

    /**
     * @param principal    已认证主体
     * @param action       操作代码
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param project      项目
     * @param now          操作时刻
     */
    private void audit(AgentArkPrincipal principal, String action, String resourceType, StrongId resourceId, Project project, Instant now) {
        auditPublisher.append(new IamAuditRecord(
            action,
            actor(principal),
            resourceType,
            resourceId.asString(),
            Optional.of(project.organizationId()),
            Optional.of(project.id()),
            "SUCCEEDED",
            now));
    }
}
