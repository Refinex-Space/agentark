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

package space.refinex.agentark.control.release;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.release.adapter.in.web.ReleaseController;
import space.refinex.agentark.control.release.adapter.in.web.ReleaseProblemDetailAdvice;
import space.refinex.agentark.control.release.adapter.out.persistence.MybatisReleaseRepository;
import space.refinex.agentark.control.release.adapter.out.persistence.ReleaseMapper;
import space.refinex.agentark.control.release.application.*;
import space.refinex.agentark.control.release.application.port.KnowledgeSnapshotLookup;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * 在 Knowledge READY 查询端口存在时装配 Agent Release、Deployment 与 Public/Internal API。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.release",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean(KnowledgeSnapshotLookup.class)
@MapperScan(basePackageClasses = ReleaseMapper.class)
public class ReleaseControlConfiguration {

    /**
     * 创建 Release Control 装配。
     */
    public ReleaseControlConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * 创建 MyBatis Release Repository。
     *
     * @param mapper     Release Mapper
     * @param jsonMapper JSON 映射器
     * @return Release Repository
     */
    @Bean
    public ReleaseRepository releaseRepository(ReleaseMapper mapper, JsonMapper jsonMapper) {
        return new MybatisReleaseRepository(mapper, jsonMapper);
    }

    /**
     * 创建 Canonical Snapshot 序列化器。
     *
     * @param jsonMapper JSON 映射器
     * @return Canonical Snapshot 序列化器
     */
    @Bean
    public CanonicalSnapshotSerializer canonicalSnapshotSerializer(JsonMapper jsonMapper) {
        return new CanonicalSnapshotSerializer(jsonMapper);
    }

    /**
     * 创建发布边界的 Snapshot 资产解析器。
     *
     * @param catalogRepository AI 资产目录端口
     * @param secretRepository  Secret 引用检查端口
     * @param knowledgeLookup   READY Knowledge 查询端口
     * @param jsonMapper        JSON 映射器
     * @return Snapshot 资产解析器
     */
    @Bean
    public SnapshotAssetResolver snapshotAssetResolver(
        CatalogRepository catalogRepository,
        SecretRepository secretRepository,
        KnowledgeSnapshotLookup knowledgeLookup,
        JsonMapper jsonMapper) {
        return new SnapshotAssetResolver(
            catalogRepository, secretRepository, knowledgeLookup, jsonMapper);
    }

    /**
     * 创建事务型 Agent Publisher。
     *
     * @param repository           Release 持久化端口
     * @param resolver             Snapshot 资产解析器
     * @param serializer           Canonical Snapshot 序列化器
     * @param catalogRepository    AI 资产目录端口
     * @param tenantRepository     租户目录端口
     * @param authorizationService IAM 授权服务
     * @param auditPublisher       事务提交后审计发布器
     * @param clock                UTC 时钟
     * @param jsonMapper           JSON 映射器
     * @return Agent Publisher
     */
    @Bean
    public AgentPublisher agentPublisher(
        ReleaseRepository repository,
        SnapshotAssetResolver resolver,
        CanonicalSnapshotSerializer serializer,
        CatalogRepository catalogRepository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        IamAuditPublisher auditPublisher,
        Clock clock,
        JsonMapper jsonMapper) {
        return new AgentPublisher(
            repository, resolver, serializer, catalogRepository, tenantRepository,
            authorizationService, auditPublisher, clock, jsonMapper);
    }

    /**
     * 创建 Agent Draft 与 Deployment 应用服务。
     *
     * @param repository           Release 持久化端口
     * @param catalogRepository    AI 资产目录端口
     * @param tenantRepository     租户目录端口
     * @param authorizationService IAM 授权服务
     * @param secretRepository     Secret 引用检查端口
     * @param auditPublisher       事务提交后审计发布器
     * @param clock                UTC 时钟
     * @param jsonMapper           JSON 映射器
     * @return Release 应用服务
     */
    @Bean
    public ReleaseApplicationService releaseApplicationService(
        ReleaseRepository repository,
        CatalogRepository catalogRepository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        SecretRepository secretRepository,
        IamAuditPublisher auditPublisher,
        Clock clock,
        JsonMapper jsonMapper) {
        return new ReleaseApplicationService(
            repository, catalogRepository, tenantRepository, authorizationService,
            secretRepository, auditPublisher, clock, jsonMapper);
    }

    /**
     * 创建 Runtime Internal Contract 服务。
     *
     * @param repository Release 持久化端口
     * @return Internal Runtime Contract 服务
     */
    @Bean
    public RuntimeInternalContractService runtimeInternalContractService(
        ReleaseRepository repository) {
        return new RuntimeInternalContractService(repository);
    }

    /**
     * 创建 Public 与 Internal Release Controller。
     *
     * @param service         Release 应用服务
     * @param publisher       Agent Publisher
     * @param internalService Internal Runtime Contract 服务
     * @param jsonMapper      Snapshot JSON 解析器
     * @return Release Controller
     */
    @Bean
    public ReleaseController releaseController(
        ReleaseApplicationService service,
        AgentPublisher publisher,
        RuntimeInternalContractService internalService,
        JsonMapper jsonMapper) {
        return new ReleaseController(service, publisher, internalService, jsonMapper);
    }

    /**
     * 创建 Release Problem Detail 异常映射器。
     *
     * @param accessor 请求上下文访问器
     * @return Release Problem Detail 映射器
     */
    @Bean
    public ReleaseProblemDetailAdvice releaseProblemDetailAdvice(RequestContextAccessor accessor) {
        return new ReleaseProblemDetailAdvice(accessor);
    }
}
