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

package space.refinex.agentark.control.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import space.refinex.agentark.control.iam.adapter.in.web.IamApiModels.ApiKeyView;
import space.refinex.agentark.control.catalog.CatalogControlConfiguration;
import space.refinex.agentark.control.catalog.application.CatalogApplicationService;
import space.refinex.agentark.control.catalog.domain.*;
import space.refinex.agentark.control.iam.application.AuthorizationCacheKey;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamApiKeyService;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.PermissionRegistry;
import space.refinex.agentark.control.iam.application.port.AuthorizationCache;
import space.refinex.agentark.control.iam.application.port.AuthorizationRepository;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.IamScopeType;
import space.refinex.agentark.control.iam.domain.PrincipalKind;
import space.refinex.agentark.control.release.ReleaseControlConfiguration;
import space.refinex.agentark.control.release.application.AgentPublisher;
import space.refinex.agentark.control.release.application.ReleaseApplicationService;
import space.refinex.agentark.control.release.application.RuntimeInternalContractService;
import space.refinex.agentark.control.release.application.port.KnowledgeSnapshotLookup;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.LimitSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.ModelBinding;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.PermissionBinding;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.ProfileBindings;
import space.refinex.agentark.control.release.domain.ReleaseModels.TrafficPolicy;
import space.refinex.agentark.control.secret.application.SecretApplicationService;
import space.refinex.agentark.control.secret.domain.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.ServiceIdentity;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import tools.jackson.databind.json.JsonMapper;

/**
 * 使用真实 MySQL、MVC 安全链和 MyBatis 适配器验证租户、资产、API Key 与发布全链路。
 *
 * @author refinex
 */
@Testcontainers
@SpringBootTest(
    classes = IamTenancySecurityIT.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "agentark.control.iam.enabled=true",
        "agentark.foundation.security.enabled=false",
        "agentark.foundation.persistence.tenant-defense-enabled=true",
        "agentark.foundation.persistence.sql-telemetry-enabled=false",
        "agentark.foundation.storage.enabled=true",
        "agentark.foundation.storage.authority=catalog-it",
        "agentark.control.catalog.enabled=true",
        "agentark.control.catalog.skill-supply-chain-required=false",
        "spring.flyway.enabled=true",
        "spring.flyway.create-schemas=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.default-schema=agentark_control",
        "spring.flyway.schemas=agentark_control",
        "spring.flyway.locations=classpath:db/migration/control",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
    })
class IamTenancySecurityIT {

    /** 临时 MySQL 引导口令，仅存在于当前测试进程。 */
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString().replace("-", "");

    /** 固定使用与本地 Core 一致的 MySQL 8.4 补丁镜像。 */
    @Container
    private static final MySQLContainer MYSQL =
        new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("agentark_control")
            .withUsername("agentark_control")
            .withPassword(DATABASE_PASSWORD)
            .withCommand("--log-bin-trust-function-creators=ON");

    /** 测试对象存储使用系统临时目录中的独立随机根，不写入仓库。 */
    private static final Path OBJECT_ROOT = Path.of(
        System.getProperty("java.io.tmpdir"), "agentark-catalog-" + UUID.randomUUID());

    /** IAM 聚合应用服务。 */
    @Autowired
    private IamApplicationService iamService;

    /** IAM 应用授权服务。 */
    @Autowired
    private IamAuthorizationService authorizationService;

    /** API Key 生命周期与认证服务。 */
    @Autowired
    private IamApiKeyService apiKeyService;

    /** AI 资产目录应用服务。 */
    @Autowired
    private CatalogApplicationService catalogService;

    /** Secret Metadata 与 Binding 应用服务。 */
    @Autowired
    private SecretApplicationService secretService;

    /** Agent Draft 与 Deployment 应用服务。 */
    @Autowired
    private ReleaseApplicationService releaseService;

    /** 事务型 Agent 发布器。 */
    @Autowired
    private AgentPublisher agentPublisher;

    /** Runtime Internal Contract 服务。 */
    @Autowired
    private RuntimeInternalContractService internalContractService;

    /** 角色持久化端口。 */
    @Autowired
    private AuthorizationRepository authorizationRepository;

    /** 租户目录持久化端口。 */
    @Autowired
    private TenantCatalogRepository tenantRepository;

    /** 短 TTL 授权缓存。 */
    @Autowired
    private AuthorizationCache authorizationCache;

    /** 用于验证摘要列的 JDBC 查询入口。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 完整 MVC 安全链测试入口。 */
    private MockMvc mockMvc;

    /** Servlet 应用上下文，用于装配真实安全过滤链。 */
    @Autowired
    private WebApplicationContext webApplicationContext;

    /** 应用统一 JSON 映射器。 */
    @Autowired
    private JsonMapper jsonMapper;

    /** 创建 IAM 集成测试实例。 */
    IamTenancySecurityIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 将动态 MySQL 端口和随机测试凭据接入 Spring DataSource。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("agentark.foundation.storage.root", OBJECT_ROOT::toString);
    }

    /** 每个测试前从真实 Servlet 上下文构造包含 Spring Security 的 MockMvc。 */
    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    /**
     * 验证客户端租户 Header、猜测 ID、列表查询和直接对象访问均不能跨租户。
     *
     * @throws Exception MockMvc 请求执行失败时抛出
     */
    @Test
    void rejectsCrossTenantAccessAndTenantHeaderOverride() throws Exception {
        AgentArkPrincipal ownerA = platformOwner("owner-a");
        AgentArkPrincipal ownerB = platformOwner("owner-b");
        var organizationA = iamService.createOrganization(ownerA, "tenant-a", "租户甲");
        var organizationB = iamService.createOrganization(ownerB, "tenant-b", "租户乙");
        var projectA = iamService.createProject(ownerA, organizationA.id(), "project-a", "项目甲");
        var projectB = iamService.createProject(ownerB, organizationB.id(), "project-b", "项目乙");
        iamService.createEnvironment(ownerB, projectB.id(), "prod", "生产环境");

        assertThat(tenantRepository.listProjects(organizationA.id(), 100))
            .extracting(value -> value.id())
            .containsExactly(projectA.id());
        assertThatThrownBy(() -> iamService.listProjects(ownerA, organizationB.id()))
            .isInstanceOf(IamAccessDeniedException.class);
        assertThatThrownBy(() -> iamService.listEnvironments(ownerA, projectB.id()))
            .isInstanceOf(IamAccessDeniedException.class);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/projects",
                organizationB.id().asString())
                .with(authentication(springAuthentication(ownerA)))
                .header("X-AgentArk-Organization-Id", organizationB.id().asString())
                .header("X-Tenant-Id", organizationB.id().asString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ARK-IAM-FORBIDDEN-00001"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/environments",
                projectB.id().asString())
                .with(authentication(springAuthentication(ownerA))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ARK-IAM-FORBIDDEN-00001"));
        mockMvc.perform(get("/api/v1/organizations"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ARK-IAM-UNAUTHORIZED-00001"));
    }

    /**
     * 验证角色变化主动失效缓存、API Key 只保存 SHA-256 摘要且明文仅在创建结果出现。
     *
     * @throws Exception SHA-256 或 JSON 序列化不可用时抛出
     */
    @Test
    void invalidatesAuthorizationCacheAndStoresOnlyApiKeyDigest() throws Exception {
        AgentArkPrincipal owner = platformOwner("key-owner");
        var organization = iamService.createOrganization(owner, "key-org", "密钥组织");
        var project = iamService.createProject(owner, organization.id(), "key-project", "密钥项目");
        var serviceAccount = iamService.createServiceAccount(owner, project.id(), "runtime-worker");
        var adminRole = authorizationRepository
            .findRoleByKey(organization.id(), Optional.of(project.id()), "project-admin")
            .orElseThrow();

        AuthorizationCacheKey cacheKey = new AuthorizationCacheKey(
            PrincipalKind.SERVICE_ACCOUNT,
            serviceAccount.id().value(),
            organization.id(),
            Optional.of(project.id()),
            Optional.empty());
        authorizationCache.put(cacheKey, Set.of(PermissionRegistry.PROJECT_READ));
        assertThat(authorizationCache.get(cacheKey)).isPresent();

        iamService.createRoleBinding(
            owner,
            project.id(),
            adminRole.id(),
            PrincipalKind.SERVICE_ACCOUNT,
            serviceAccount.id().value(),
            IamScopeType.PROJECT,
            project.id().value());
        assertThat(authorizationCache.get(cacheKey)).isEmpty();

        var created = apiKeyService.create(
            owner,
            project.id(),
            serviceAccount.id(),
            "runtime access",
            Set.of(PermissionRegistry.PROJECT_READ, PermissionRegistry.API_KEY_READ),
            Optional.of(Instant.now().plusSeconds(3600)));
        String plaintext = created.plaintext();
        String expectedDigest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(plaintext.getBytes(StandardCharsets.US_ASCII)));
        String storedDigest = jdbcTemplate.queryForObject(
            "SELECT LOWER(HEX(digest)) FROM api_key WHERE prefix = ?",
            String.class,
            created.metadata().prefix());
        Integer forbiddenColumns = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'api_key' "
                + "AND column_name IN ('plaintext', 'secret', 'token')",
            Integer.class);
        assertThat(storedDigest).isEqualTo(expectedDigest);
        assertThat(forbiddenColumns).isZero();

        String prefix = plaintext.substring(4, 16);
        char[] secret = plaintext.substring(17).toCharArray();
        var authenticated = apiKeyService.authenticate(prefix, secret, "agentark-control");
        assertThat(authenticated).isPresent();
        assertThat(authenticated.orElseThrow().authorities())
            .containsExactlyInAnyOrder(
                PermissionRegistry.PROJECT_READ, PermissionRegistry.API_KEY_READ);
        assertThat(secret).containsOnly('\0');
        mockMvc.perform(get("/api/v1/organizations")
                .header(HttpHeaders.AUTHORIZATION, "ApiKey " + plaintext))
            .andExpect(status().isOk());

        String safeListJson = jsonMapper.writeValueAsString(List.of(ApiKeyView.from(created.metadata())));
        assertThat(safeListJson)
            .doesNotContain("digest")
            .doesNotContain("plaintext")
            .doesNotContain(plaintext);

        apiKeyService.revoke(owner, project.id(), created.metadata().id(), created.metadata().version());
        char[] revokedSecret = plaintext.substring(17).toCharArray();
        assertThat(apiKeyService.authenticate(prefix, revokedSecret, "agentark-control"))
            .isEmpty();
        assertThat(revokedSecret).containsOnly('\0');

        var expiring = apiKeyService.create(
            owner,
            project.id(),
            serviceAccount.id(),
            "expiring runtime access",
            Set.of(PermissionRegistry.PROJECT_READ),
            Optional.of(Instant.now().plusSeconds(3600)));
        jdbcTemplate.update(
            "UPDATE api_key "
                + "SET created_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 SECOND), "
                + "expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) "
                + "WHERE prefix = ?",
            expiring.metadata().prefix());
        String expiringPrefix = expiring.plaintext().substring(4, 16);
        char[] expiredSecret = expiring.plaintext().substring(17).toCharArray();
        assertThat(apiKeyService.authenticate(
            expiringPrefix, expiredSecret, "agentark-control")).isEmpty();
        assertThat(expiredSecret).containsOnly('\0');
    }

    /**
     * 验证资产版本只追加、SecretRef 存在性、MCP 安全元数据、Skill ObjectRef 和跨租户拒绝。
     *
     * @throws Exception MockMvc 或 JSON 校验失败时抛出
     */
    @Test
    void persistsImmutableCatalogVersionsAndRejectsCrossTenantAssets() throws Exception {
        AgentArkPrincipal ownerA = platformOwner("catalog-owner-a");
        AgentArkPrincipal ownerB = platformOwner("catalog-owner-b");
        var organizationA = iamService.createOrganization(ownerA, "catalog-org-a", "资产组织甲");
        var organizationB = iamService.createOrganization(ownerB, "catalog-org-b", "资产组织乙");
        var projectA = iamService.createProject(
            ownerA, organizationA.id(), "catalog-project-a", "资产项目甲");
        var projectB = iamService.createProject(
            ownerB, organizationB.id(), "catalog-project-b", "资产项目乙");
        var environmentA = iamService.createEnvironment(ownerA, projectA.id(), "prod", "生产环境");

        SecretMetadata projectSecret = secretService.createMetadata(
            ownerA, projectA.id(), "model-credential", "模型凭据",
            SecretProviderType.CUSTOM, "providers/model/primary", "v1", SecretScope.PROJECT);
        SecretMetadata environmentSecret = secretService.createMetadata(
            ownerA, projectA.id(), "mcp-credential", "MCP 凭据",
            SecretProviderType.CUSTOM, "providers/mcp/primary", "v1", SecretScope.ENVIRONMENT);
        var binding = secretService.createBinding(
            ownerA, projectA.id(), environmentA.id(), environmentSecret.id(), "mcp-auth");

        CatalogAsset provider = catalogService.createAsset(
            ownerA, projectA.id(), CatalogAssetKind.MODEL_PROVIDER, "primary-model",
            "主模型 Provider", "OpenAI 兼容入口",
            java.util.Map.of("providerType", "OPENAI_COMPATIBLE", "descriptor",
                java.util.Map.of("baseUrl", "https://models.example.test/v1")));
        CatalogVersion model = catalogService.createVersion(
            ownerA, projectA.id(), CatalogAssetKind.MODEL_PROVIDER, provider.id().asString(),
            java.util.Map.of(
                "modelName", "example-model",
                "capabilities", List.of("TOOL", "STREAMING"),
                "parameters", java.util.Map.of("temperature", 0.2),
                "credentialSecretRef", projectSecret.projectRef().asString()),
            CatalogVersionStatus.PUBLISHED);
        assertThat(model.versionNumber()).isEqualTo(1);

        CatalogAsset prompt = catalogService.createAsset(
            ownerA, projectA.id(), CatalogAssetKind.PROMPT, "assistant-prompt",
            "助手 Prompt", "系统提示", java.util.Map.of());
        CatalogVersion promptV1 = catalogService.createVersion(
            ownerA, projectA.id(), CatalogAssetKind.PROMPT, prompt.id().asString(),
            java.util.Map.of("template", "你好 {{name}}", "variableSchema",
                java.util.Map.of("type", "object"), "purpose", "问候"),
            CatalogVersionStatus.DRAFT);
        CatalogVersion promptV2 = catalogService.createVersion(
            ownerA, projectA.id(), CatalogAssetKind.PROMPT, prompt.id().asString(),
            java.util.Map.of("template", "您好 {{name}}", "variableSchema",
                java.util.Map.of("type", "object"), "purpose", "正式问候"),
            CatalogVersionStatus.PUBLISHED);
        assertThat(catalogService.diff(
            ownerA, projectA.id(), CatalogAssetKind.PROMPT, prompt.id().asString(),
            promptV1.id().asString(), promptV2.id().asString()).changedPaths())
            .containsExactly("/purpose", "/template");

        CatalogAsset mcp = catalogService.createAsset(
            ownerA, projectA.id(), CatalogAssetKind.MCP_SERVER, "orders-mcp",
            "订单 MCP", "只记录连接元数据", java.util.Map.of());
        catalogService.createVersion(
            ownerA, projectA.id(), CatalogAssetKind.MCP_SERVER, mcp.id().asString(),
            java.util.Map.of(
                "transport", "STREAMABLE_HTTP",
                "endpointUri", "https://mcp.example.test/api",
                "transportConfig", java.util.Map.of("timeoutMs", 5000),
                "authSecretRef", binding.ref().asString(),
                "ssrfPolicy", java.util.Map.of(
                    "denyPrivateNetworks", true, "denyCloudMetadata", true,
                    "resolveAndPinDns", true),
                "tools", List.of(java.util.Map.of(
                    "name", "get-order", "description", "读取订单",
                    "argumentSchema", java.util.Map.of("type", "object"),
                    "accessMode", "READ", "riskLevel", "LOW",
                    "idempotency", "IDEMPOTENT",
                    "permissionMetadata", java.util.Map.of("allowlisted", true)))),
            CatalogVersionStatus.PUBLISHED);
        Integer descriptorCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mcp_tool_descriptor WHERE project_id = UUID_TO_BIN(?)",
            Integer.class, projectA.id().asString());
        assertThat(descriptorCount).isOne();

        byte[] artifact = "# Example Skill\n".getBytes(StandardCharsets.UTF_8);
        var objectRef = catalogService.uploadSkillArtifact(
            ownerA, projectA.id(), new java.io.ByteArrayInputStream(artifact), artifact.length,
            "text/markdown", Optional.of(Checksum.sha256(artifact)));
        CatalogAsset skill = catalogService.createAsset(
            ownerA, projectA.id(), CatalogAssetKind.SKILL, "example-skill", "示例 Skill",
            "只提交 Artifact，不执行", java.util.Map.of());
        catalogService.createVersion(
            ownerA, projectA.id(), CatalogAssetKind.SKILL, skill.id().asString(),
            java.util.Map.of(
                "artifact", java.util.Map.of(
                    "uri", objectRef.uri().toString(), "checksum", objectRef.checksum().toString(),
                    "size", objectRef.size(), "mediaType", objectRef.mediaType()),
                "sourceUri", "https://source.example.test/skills/example",
                "license", "Apache-2.0", "compatibility", java.util.Map.of("agentark", ">=0.1")),
            CatalogVersionStatus.PUBLISHED);

        SecretMetadata lifecycle = secretService.createMetadata(
            ownerA, projectA.id(), "rotation-target", "轮换目标",
            SecretProviderType.VAULT, "projects/rotation-target", "1", SecretScope.PROJECT);
        lifecycle = secretService.rotateMetadata(
            ownerA, projectA.id(), lifecycle.id(), "2", lifecycle.version());
        assertThat(lifecycle.externalVersion()).isEqualTo("2");
        lifecycle = secretService.changeMetadataStatus(
            ownerA, projectA.id(), lifecycle.id(), SecretMetadataStatus.DISABLED,
            lifecycle.version());
        assertThat(lifecycle.status()).isEqualTo(SecretMetadataStatus.DISABLED);
        lifecycle = secretService.changeMetadataStatus(
            ownerA, projectA.id(), lifecycle.id(), SecretMetadataStatus.ENABLED,
            lifecycle.version());
        assertThat(lifecycle.status()).isEqualTo(SecretMetadataStatus.ENABLED);
        lifecycle = secretService.changeMetadataStatus(
            ownerA, projectA.id(), lifecycle.id(), SecretMetadataStatus.REVOKED,
            lifecycle.version());
        SecretMetadata revoked = lifecycle;
        assertThatThrownBy(() -> secretService.changeMetadataStatus(
            ownerA, projectA.id(), revoked.id(), SecretMetadataStatus.ENABLED,
            revoked.version()))
            .isInstanceOf(IamConflictException.class)
            .hasMessageContaining("cannot be re-enabled");

        mockMvc.perform(get("/api/v1/projects/{projectId}/catalog/prompt", projectA.id().asString())
                .with(authentication(springAuthentication(ownerB))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ARK-CATALOG-FORBIDDEN-00001"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/catalog/prompt", projectB.id().asString())
                .with(authentication(springAuthentication(ownerA))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ARK-CATALOG-FORBIDDEN-00001"));

        Integer forbiddenSecretColumns = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name IN ('secret_metadata', 'secret_binding') "
                + "AND column_name IN ('secret', 'secret_value', 'plaintext', 'token', 'api_key')",
            Integer.class);
        assertThat(forbiddenSecretColumns).isZero();
    }

    /**
     * 验证真实 MySQL 上从版本化资产、Draft 发布到 Deployment 回退与 Internal Snapshot 的全链路。
     *
     * @throws Exception 解析数据库中的 Canonical Snapshot 失败时抛出
     */
    @Test
    void publishesImmutableSnapshotAndRollsBackDeploymentEndToEnd() throws Exception {
        AgentArkPrincipal owner = platformOwner("release-owner");
        var organization = iamService.createOrganization(owner, "release-org", "发布组织");
        var project = iamService.createProject(
            owner, organization.id(), "release-project", "发布项目");
        var environment = iamService.createEnvironment(
            owner, project.id(), "release-prod", "发布生产环境");

        SecretMetadata modelSecret = secretService.createMetadata(
            owner, project.id(), "release-model-credential", "发布模型凭据",
            SecretProviderType.CUSTOM, "providers/release/model", "v1", SecretScope.PROJECT);
        CatalogAsset modelProvider = catalogService.createAsset(
            owner, project.id(), CatalogAssetKind.MODEL_PROVIDER, "release-model",
            "发布模型 Provider", "发布链路模型",
            java.util.Map.of("providerType", "OPENAI_COMPATIBLE", "descriptor",
                java.util.Map.of("baseUrl", "https://models.example.test/v1")));
        CatalogVersion modelProfile = catalogService.createVersion(
            owner, project.id(), CatalogAssetKind.MODEL_PROVIDER,
            modelProvider.id().asString(), java.util.Map.of(
                "modelName", "release-model",
                "capabilities", List.of("STREAMING"),
                "parameters", java.util.Map.of("temperature", 0.2, "maxTokens", 4096),
                "credentialSecretRef", modelSecret.projectRef().asString()),
            CatalogVersionStatus.PUBLISHED);

        CatalogAsset memory = catalogService.createAsset(
            owner, project.id(), CatalogAssetKind.MEMORY_PROFILE, "release-memory",
            "发布 Memory", "发布链路 Memory", java.util.Map.of());
        CatalogVersion memoryVersion = catalogService.createVersion(
            owner, project.id(), CatalogAssetKind.MEMORY_PROFILE, memory.id().asString(),
            java.util.Map.of("strategy", "session"), CatalogVersionStatus.PUBLISHED);
        CatalogAsset workspace = catalogService.createAsset(
            owner, project.id(), CatalogAssetKind.WORKSPACE_PROFILE, "release-workspace",
            "发布 Workspace", "发布链路 Workspace", java.util.Map.of());
        CatalogVersion workspaceVersion = catalogService.createVersion(
            owner, project.id(), CatalogAssetKind.WORKSPACE_PROFILE, workspace.id().asString(),
            java.util.Map.of("mode", "isolated"), CatalogVersionStatus.PUBLISHED);
        CatalogAsset sandbox = catalogService.createAsset(
            owner, project.id(), CatalogAssetKind.SANDBOX_PROFILE, "release-sandbox",
            "发布 Sandbox", "发布链路 Sandbox", java.util.Map.of());
        CatalogVersion sandboxVersion = catalogService.createVersion(
            owner, project.id(), CatalogAssetKind.SANDBOX_PROFILE, sandbox.id().asString(),
            java.util.Map.of("network", "deny"), CatalogVersionStatus.PUBLISHED);
        CatalogAsset policy = catalogService.createAsset(
            owner, project.id(), CatalogAssetKind.PERMISSION_POLICY, "release-policy",
            "发布权限策略", "发布链路最小权限", java.util.Map.of());
        CatalogVersion policyVersion = catalogService.createVersion(
            owner, project.id(), CatalogAssetKind.PERMISSION_POLICY, policy.id().asString(),
            java.util.Map.of(
                "defaultDecision", "DENY", "rules", List.of(), "scopes", List.of(),
                "approvalPolicy", java.util.Map.of("mode", "disabled")),
            CatalogVersionStatus.PUBLISHED);

        AgentDraftSpec initialDraft = draft(
            modelProvider, modelProfile, memory, memoryVersion, workspace, workspaceVersion,
            sandbox, sandboxVersion, policy, policyVersion, 10);
        var agent = releaseService.createAgent(
            owner, project.id(), "release-agent", "发布 Agent", "真实发布链路", initialDraft);
        var first = agentPublisher.publish(
            owner, project.id(), agent.id(), "release-e2e-1", 0);
        var replay = agentPublisher.publish(
            owner, project.id(), agent.id(), "release-e2e-1", 0);
        assertThat(replay.id()).isEqualTo(first.id());

        AgentArkPrincipal runtime = new AgentArkPrincipal(
            "https://issuer.example.test", "runtime", PrincipalType.SERVICE, Set.of(),
            Optional.empty(), Optional.of(new ServiceIdentity(
                "agentark-runtime", Set.of(RuntimeInternalContractService.CONTROL_AUDIENCE))));
        var stored = internalContractService.snapshot(
            runtime, first.id(), "agentscope-java-2", Set.of(1), Set.of("streaming"));
        var snapshotJson = jsonMapper.readTree(stored.canonicalJson());
        assertThat(snapshotJson.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(snapshotJson.get("runtimeProvider").asText()).isEqualTo("agentscope-java-2");
        assertThat(snapshotJson.get("model").get("credential").get("secretRef").asText())
            .startsWith("secret://project/");
        assertThat(stored.canonicalJson())
            .doesNotContain("providers/release/model")
            .doesNotContain("secretValue")
            .doesNotContain("plaintext");

        var deployment = releaseService.createDeployment(
            owner, project.id(), environment.id(), agent.id(), first.id(), TrafficPolicy.full());
        AgentDraftSpec changedDraft = draft(
            modelProvider, modelProfile, memory, memoryVersion, workspace, workspaceVersion,
            sandbox, sandboxVersion, policy, policyVersion, 11);
        releaseService.updateDraft(owner, project.id(), agent.id(), changedDraft, 0);
        var second = agentPublisher.publish(
            owner, project.id(), agent.id(), "release-e2e-2", 1);
        var promoted = releaseService.promote(
            owner, project.id(), environment.id(), deployment.id(), second.id(), 0);
        var rolledBack = releaseService.rollback(
            owner, project.id(), environment.id(), deployment.id(), first.id(), 1);

        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(promoted.desiredRevisionId()).isEqualTo(second.id());
        assertThat(rolledBack.desiredRevisionId()).isEqualTo(first.id());
        assertThat(rolledBack.version()).isEqualTo(2);
        assertThat(internalContractService.deployment(runtime, deployment.id()).desiredRevisionId())
            .isEqualTo(first.id().asString());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agent_revision WHERE agent_id = UUID_TO_BIN(?)",
            Integer.class, agent.id().asString())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM control_outbox WHERE aggregate_id IN (?, ?, ?)", Integer.class,
            first.id().asString(), second.id().asString(), deployment.id().asString())).isEqualTo(5);
        assertThatThrownBy(() -> releaseService.getDraft(
            platformOwner("release-intruder"), project.id(), agent.id()))
            .isInstanceOf(IamAccessDeniedException.class);
    }

    /**
     * 创建发布 E2E 使用的最小完整 Draft。
     *
     * @param modelProvider 模型 Provider 稳定资产
     * @param modelProfile 模型不可变版本
     * @param memory Memory 稳定资产
     * @param memoryVersion Memory 不可变版本
     * @param workspace Workspace 稳定资产
     * @param workspaceVersion Workspace 不可变版本
     * @param sandbox Sandbox 稳定资产
     * @param sandboxVersion Sandbox 不可变版本
     * @param policy Permission Policy 稳定资产
     * @param policyVersion Permission Policy 不可变版本
     * @param maxToolCalls 单 Turn Tool 调用上限
     * @return 强类型 Draft
     */
    private AgentDraftSpec draft(
        CatalogAsset modelProvider,
        CatalogVersion modelProfile,
        CatalogAsset memory,
        CatalogVersion memoryVersion,
        CatalogAsset workspace,
        CatalogVersion workspaceVersion,
        CatalogAsset sandbox,
        CatalogVersion sandboxVersion,
        CatalogAsset policy,
        CatalogVersion policyVersion,
        int maxToolCalls) {
        return new AgentDraftSpec(
            "agentscope-java-2",
            List.of("streaming"),
            new ModelBinding(
                (ModelProviderId) modelProvider.id(), (ModelProfileId) modelProfile.id()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new ProfileBindings(
                (MemoryProfileId) memory.id(), (MemoryProfileVersionId) memoryVersion.id(),
                (WorkspaceProfileId) workspace.id(),
                (WorkspaceProfileVersionId) workspaceVersion.id(),
                (SandboxProfileId) sandbox.id(),
                (SandboxProfileVersionId) sandboxVersion.id()),
            new PermissionBinding(
                (PermissionPolicyId) policy.id(),
                (PermissionPolicyVersionId) policyVersion.id()),
            new LimitSpec(300, maxToolCalls, 2));
    }

    /**
     * 创建具备首个组织创建 Claim 的受信测试用户主体。
     *
     * @param subject 稳定测试 Subject
     * @return 平台组织创建者
     */
    private AgentArkPrincipal platformOwner(String subject) {
        return new AgentArkPrincipal(
            "https://issuer.example.test",
            subject,
            PrincipalType.USER,
            Set.of(PermissionRegistry.ORGANIZATION_CREATE),
            Optional.empty(),
            Optional.empty());
    }

    /**
     * 将协议主体包装为 Spring Security 已认证对象。
     *
     * @param principal AgentArk 主体
     * @return 已认证安全上下文
     */
    private Authentication springAuthentication(AgentArkPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", List.of());
    }

    /**
     * 在 Release 配置解析前提供测试专用 Knowledge 中立端口。
     *
     * @author refinex
     */
    @Configuration(proxyBeanMethods = false)
    static class ReleaseKnowledgeTestConfiguration {

        /** 创建 Knowledge 测试配置。 */
        ReleaseKnowledgeTestConfiguration() {
            // Spring 通过无参构造器创建测试配置。
        }

        /**
         * 当前 E2E Draft 不引用 Knowledge，因此端口始终返回空。
         *
         * @return 始终返回空的 Knowledge 查询端口
         */
        @Bean
        KnowledgeSnapshotLookup knowledgeSnapshotLookup() {
            return (projectId, knowledgeBaseId, revisionId) -> Optional.empty();
        }
    }

    /**
     * 提供只用于集成测试的最小 Spring Boot 应用装配。
     *
     * @author refinex
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        ReleaseKnowledgeTestConfiguration.class,
        IamControlConfiguration.class,
        CatalogControlConfiguration.class,
        ReleaseControlConfiguration.class
    })
    static class TestApplication {

        /** 创建测试应用配置。 */
        TestApplication() {
            // Spring Boot 通过无参构造器创建测试配置。
        }
    }
}
