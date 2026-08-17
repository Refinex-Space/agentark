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

package space.refinex.agentark.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.access.AccessDeniedException;
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
import space.refinex.agentark.control.iam.IamControlConfiguration;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuditRecord;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.PermissionRegistry;
import space.refinex.agentark.control.iam.application.ResolvedPrincipal;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.ServiceIdentity;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.knowledge.application.KnowledgeApplicationService;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionControlService;
import space.refinex.agentark.knowledge.application.KnowledgePermissions;
import space.refinex.agentark.knowledge.application.KnowledgeProjectContext;
import space.refinex.agentark.knowledge.application.port.KnowledgeAccessPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeAuditPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.DataSourceType;
import space.refinex.agentark.knowledge.domain.KnowledgeProfileStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactKind;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactReference;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 使用真实 MySQL、MyBatis、Object Store、IAM 与 MVC 验证 Knowledge 持久化和跨租户拒绝。
 *
 * @author refinex
 */
@Testcontainers
@SpringBootTest(
    classes = KnowledgeTenancyIT.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "agentark.control.iam.enabled=true",
        "agentark.control.knowledge.enabled=true",
        "agentark.foundation.security.enabled=false",
        "agentark.foundation.persistence.tenant-defense-enabled=true",
        "agentark.foundation.persistence.sql-telemetry-enabled=false",
        "agentark.foundation.storage.enabled=true",
        "agentark.foundation.storage.authority=knowledge-it",
        "spring.flyway.enabled=true",
        "spring.flyway.create-schemas=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.default-schema=agentark_control",
        "spring.flyway.schemas=agentark_control",
        "spring.flyway.locations=classpath:db/migration/control",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
    })
class KnowledgeTenancyIT {

    /** 临时 MySQL 口令，仅存在于测试进程。 */
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString().replace("-", "");

    /** 固定使用与本地 Core 相同的 MySQL 8.4 补丁镜像。 */
    @Container
    private static final MySQLContainer MYSQL =
        new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("agentark_control")
            .withUsername("agentark_control")
            .withPassword(DATABASE_PASSWORD)
            .withCommand("--log-bin-trust-function-creators=ON");

    /** 测试对象存储使用系统临时目录，不写入仓库。 */
    private static final Path OBJECT_ROOT = Path.of(
        System.getProperty("java.io.tmpdir"), "agentark-knowledge-" + UUID.randomUUID());

    /** IAM 聚合应用服务。 */
    @Autowired
    private IamApplicationService iamService;

    /** Knowledge 应用服务。 */
    @Autowired
    private KnowledgeApplicationService knowledgeService;

    /** Internal 摄取结果 Control 服务。 */
    @Autowired
    private KnowledgeIngestionControlService ingestionControlService;

    /** Knowledge 持久化端口。 */
    @Autowired
    private KnowledgeRepository knowledgeRepository;

    /** 数据库验证入口。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Servlet 应用上下文。 */
    @Autowired
    private WebApplicationContext webApplicationContext;

    /** 完整 MVC 安全链测试入口。 */
    private MockMvc mockMvc;

    /** 创建 Knowledge 集成测试实例。 */
    KnowledgeTenancyIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 将动态 MySQL 和临时对象目录接入测试应用。
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

    /** 每个测试前构造包含 Spring Security 的 MockMvc。 */
    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    /**
     * 证明原文件 Hash、不可变元数据、Project Scope SQL 和跨租户 HTTP 均按契约生效。
     *
     * @throws Exception 文件上传或 MockMvc 请求失败时抛出
     */
    @Test
    void persistsMetadataAndRejectsCrossTenantAccess() throws Exception {
        AgentArkPrincipal ownerA = platformOwner("knowledge-owner-a");
        AgentArkPrincipal ownerB = platformOwner("knowledge-owner-b");
        var organizationA = iamService.createOrganization(ownerA, "knowledge-a", "知识组织甲");
        var organizationB = iamService.createOrganization(ownerB, "knowledge-b", "知识组织乙");
        var projectA = iamService.createProject(ownerA, organizationA.id(), "project-a", "项目甲");
        var projectB = iamService.createProject(ownerB, organizationB.id(), "project-b", "项目乙");
        var knowledgeBase = knowledgeService.createKnowledgeBase(
            ownerA, projectA.id(), "manuals", "产品手册", "原文件可追踪");
        knowledgeService.createKnowledgeBase(
            ownerA, projectA.id(), "manuals-two", "产品手册二", "验证数据库游标分页");
        knowledgeService.createKnowledgeBase(
            ownerA, projectA.id(), "manuals-three", "产品手册三", "验证数据库游标分页");
        var source = knowledgeService.createDataSource(
            ownerA, projectA.id(), knowledgeBase.id(), DataSourceType.UPLOAD, "上传",
            "{\"mode\":\"manual\"}");
        byte[] content = "AgentArk Knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var documentRevision = knowledgeService.uploadDocument(
            ownerA, projectA.id(), knowledgeBase.id(), source.id(), "架构手册", "architecture.txt",
            Map.of("language", "zh-CN"), new ByteArrayInputStream(content), content.length,
            "text/plain", Optional.of(Checksum.sha256(content)));
        var parser = knowledgeService.createParserProfile(
            ownerA, projectA.id(), "text", "{\"format\":\"text\"}",
            KnowledgeProfileStatus.PUBLISHED);
        var chunk = knowledgeService.createChunkProfile(
            ownerA, projectA.id(), "paragraph", "{\"size\":512}",
            KnowledgeProfileStatus.PUBLISHED);
        var embedding = knowledgeService.createEmbeddingProfile(
            ownerA, projectA.id(), "fake", "{\"dimension\":3}", Optional.empty(),
            KnowledgeProfileStatus.PUBLISHED);
        var retrieval = knowledgeService.createRetrievalProfile(
            ownerA, projectA.id(), "hybrid", "{\"topK\":10}",
            KnowledgeProfileStatus.PUBLISHED);
        var revision = knowledgeService.createKnowledgeRevision(
            ownerA, projectA.id(), knowledgeBase.id(), List.of(documentRevision.id()), parser.id(),
            chunk.id(), embedding.id(), retrieval.id());
        var ingestionRequest = knowledgeService.requestIngestion(
            ownerA, projectA.id(), revision.id(), "knowledge-it:request:0001");
        JobId schedulerJobId = JobId.generate();
        UUID attemptId = JobId.generate().value();
        IngestionResult ingestionResult = new IngestionResult(
            JobId.generate().value(), ingestionRequest.id(), organizationA.id(), projectA.id(),
            revision.id(), schedulerJobId, attemptId, "knowledge-it:result:0001", 1, 1,
            Checksum.sha256("knowledge-it-manifest"), List.of(new ArtifactReference(
                ArtifactKind.CHUNKS, documentRevision.objectRef())), ResultStatus.SUCCEEDED, "",
            Instant.now());
        ingestionControlService.acceptResult(schedulerService(), ingestionResult);
        ingestionControlService.acceptResult(schedulerService(), ingestionResult);

        assertThat(documentRevision.objectRef().checksum()).isEqualTo(Checksum.sha256(content));
        assertThat(knowledgeRepository.findKnowledgeBase(projectB.id(), knowledgeBase.id())).isEmpty();
        assertThat(knowledgeRepository.findKnowledgeRevision(projectB.id(), revision.id())).isEmpty();
        var firstPage = knowledgeService.listKnowledgeBases(ownerA, projectA.id(), null, 2);
        var secondPage = knowledgeService.listKnowledgeBases(
            ownerA, projectA.id(), firstPage.nextCursor().orElseThrow(), 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.items())
            .extracting(item -> item.id().asString())
            .doesNotContainAnyElementsOf(
                firstPage.items().stream().map(item -> item.id().asString()).toList());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_revision WHERE project_id = UUID_TO_BIN(?)",
            Integer.class, projectA.id().asString())).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM knowledge_ingestion_result WHERE project_id = UUID_TO_BIN(?)",
            Integer.class, projectA.id().asString())).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM control_outbox "
                + "WHERE aggregate_type = 'knowledge_revision' AND aggregate_id = ?",
            Integer.class, revision.id().asString())).isOne();
        assertThat(knowledgeRepository.findKnowledgeRevision(projectA.id(), revision.id())
            .orElseThrow().status()).isEqualTo(KnowledgeRevisionStatus.READY);
        assertThatThrownBy(() -> knowledgeService.listKnowledgeBases(ownerB, projectA.id(), null, 50))
            .isInstanceOf(AccessDeniedException.class);

        mockMvc.perform(get(
                "/api/v1/projects/{projectId}/knowledge-bases/{knowledgeBaseId}/data-sources",
                projectA.id().asString(), knowledgeBase.id().asString())
                .with(authentication(springAuthentication(ownerA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].descriptor.mode").value("manual"))
            .andExpect(jsonPath("$.items[0].descriptorJson").doesNotExist())
            .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get(
                "/api/v1/projects/{projectId}/knowledge-bases/{knowledgeBaseId}/revisions",
                projectA.id().asString(), knowledgeBase.id().asString())
                .with(authentication(springAuthentication(ownerA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("READY"))
            .andExpect(jsonPath("$.items[0].contentHash").isString());

        mockMvc.perform(get("/api/v1/projects/{projectId}/knowledge-bases", projectA.id().asString())
                .with(authentication(springAuthentication(ownerB))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ARK-KNOWLEDGE-FORBIDDEN-00001"));
    }

    /**
     * 创建具备首个组织创建 Claim 的测试用户。
     *
     * @param subject 稳定测试 Subject
     * @return 平台组织创建者
     */
    private static AgentArkPrincipal platformOwner(String subject) {
        return new AgentArkPrincipal(
            "https://issuer.example.test", subject, PrincipalType.USER,
            Set.of(PermissionRegistry.ORGANIZATION_CREATE), Optional.empty(), Optional.empty());
    }

    /**
     * 创建具备 Control Audience 的 Scheduler 服务身份，不携带数据库凭据。
     *
     * @return 内部 Scheduler 服务主体
     */
    private static AgentArkPrincipal schedulerService() {
        return new AgentArkPrincipal(
            "https://issuer.example.test", "knowledge-scheduler", PrincipalType.SERVICE,
            Set.of(), Optional.empty(), Optional.of(new ServiceIdentity(
                "agentark-scheduler", Set.of(KnowledgeIngestionControlService.CONTROL_AUDIENCE))));
    }

    /**
     * 将 AgentArk 主体包装为 Spring Security 已认证对象。
     *
     * @param principal AgentArk 主体
     * @return 已认证上下文
     */
    private static Authentication springAuthentication(AgentArkPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", List.of());
    }

    /**
     * 提供 Knowledge 测试使用的真实 IAM 授权与审计桥接。
     *
     * @author refinex
     */
    @Configuration(proxyBeanMethods = false)
    static class TestBridgeConfiguration {

        /** 创建测试桥接配置。 */
        TestBridgeConfiguration() {
            // Spring 通过无参构造器创建配置。
        }

        /**
         * @param tenantRepository IAM 租户目录
         * @param authorizationService IAM 授权服务
         * @return Knowledge 授权 Port
         */
        @Bean
        KnowledgeAccessPort knowledgeAccessPort(
            TenantCatalogRepository tenantRepository,
            IamAuthorizationService authorizationService) {
            return (principal, projectId, permission) -> {
                if (!KnowledgePermissions.ALL.contains(permission)) {
                    throw new IllegalArgumentException("knowledge permission is not registered");
                }
                Project project = tenantRepository.findProject(projectId)
                    .orElseThrow(() -> new AccessDeniedException("project permission is required"));
                try {
                    ResolvedPrincipal resolved = authorizationService.requirePermission(
                        principal, project.organizationId(), Optional.of(projectId), Optional.empty(),
                        permission);
                    return new KnowledgeProjectContext(
                        project.organizationId(), projectId, resolved.kind().name(), resolved.id());
                } catch (IamAccessDeniedException exception) {
                    throw new AccessDeniedException("project permission is required", exception);
                }
            };
        }

        /**
         * @param auditPublisher IAM 审计发布器
         * @return Knowledge 审计 Port
         */
        @Bean
        KnowledgeAuditPort knowledgeAuditPort(IamAuditPublisher auditPublisher) {
            return record -> auditPublisher.append(new IamAuditRecord(
                record.action(), record.actor(), record.resourceType(), record.resourceId(),
                Optional.of(record.organizationId()), Optional.of(record.projectId()), "SUCCEEDED",
                record.occurredAt()));
        }
    }

    /**
     * 提供只用于 Knowledge 集成测试的最小 Spring Boot 应用。
     *
     * @author refinex
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({IamControlConfiguration.class, KnowledgeConfiguration.class, TestBridgeConfiguration.class})
    static class TestApplication {

        /** 创建测试应用配置。 */
        TestApplication() {
            // Spring Boot 通过无参构造器创建测试配置。
        }
    }
}
