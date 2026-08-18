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

package space.refinex.agentark.control.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import space.refinex.agentark.control.catalog.adapter.in.web.CatalogController;
import space.refinex.agentark.control.catalog.adapter.in.web.CatalogProblemDetailAdvice;
import space.refinex.agentark.control.catalog.adapter.out.persistence.CatalogMapper;
import space.refinex.agentark.control.catalog.adapter.out.persistence.MybatisCatalogRepository;
import space.refinex.agentark.control.catalog.application.CatalogApplicationService;
import space.refinex.agentark.control.catalog.application.CatalogPayloadValidator;
import space.refinex.agentark.control.catalog.application.SkillSupplyChainVerifier;
import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.secret.SecretProperties;
import space.refinex.agentark.control.secret.adapter.in.web.SecretController;
import space.refinex.agentark.control.secret.adapter.in.web.SecretProblemDetailAdvice;
import space.refinex.agentark.control.secret.adapter.out.local.LocalFileSecretResolver;
import space.refinex.agentark.control.secret.adapter.out.persistence.MybatisSecretRepository;
import space.refinex.agentark.control.secret.adapter.out.persistence.SecretMapper;
import space.refinex.agentark.control.secret.adapter.out.vault.*;
import space.refinex.agentark.control.secret.application.SecretApplicationService;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.control.secret.application.port.SecretResolver;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Optional;

/**
 * 装配 AI 资产目录、Secret Metadata、MyBatis 适配器、Public API 和开发 Local Provider。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.catalog",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({CatalogProperties.class, SecretProperties.class, VaultSecretProperties.class})
@MapperScan(basePackageClasses = {CatalogMapper.class, SecretMapper.class})
public class CatalogControlConfiguration {

    /**
     * 创建 Catalog Control 装配。
     */
    public CatalogControlConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * @param mapper 资产目录 Mapper
     * @return MyBatis 资产目录仓储
     */
    @Bean
    public CatalogRepository catalogRepository(CatalogMapper mapper) {
        return new MybatisCatalogRepository(mapper);
    }

    /**
     * @param mapper Secret 元数据 Mapper
     * @return MyBatis Secret 元数据仓储
     */
    @Bean
    public SecretRepository secretRepository(SecretMapper mapper) {
        return new MybatisSecretRepository(mapper);
    }

    /**
     * @param jsonMapper 应用统一 JSON 映射器
     * @return 分类载荷校验器
     */
    @Bean
    public CatalogPayloadValidator catalogPayloadValidator(JsonMapper jsonMapper) {
        return new CatalogPayloadValidator(jsonMapper);
    }

    /**
     * @param properties Catalog 安全配置
     * @param jsonMapper JSON 映射器
     * @return Skill 供应链验证器
     */
    @Bean
    public SkillSupplyChainVerifier skillSupplyChainVerifier(CatalogProperties properties, JsonMapper jsonMapper) {
        return new SkillSupplyChainVerifier(properties, jsonMapper);
    }

    /**
     * @param repository               Catalog Repository
     * @param tenantRepository         IAM 租户目录
     * @param authorizationService     IAM 授权服务
     * @param auditPublisher           审计发布器
     * @param payloadValidator         载荷校验器
     * @param secretRepository         SecretRef 检查端口
     * @param objectStoreProvider      可选 ObjectStore
     * @param properties               Catalog 配置
     * @param skillSupplyChainVerifier Skill 供应链验证器
     * @param clock                    UTC 时钟
     * @param jsonMapper               JSON 映射器
     * @return Catalog 应用服务
     */
    @Bean
    public CatalogApplicationService catalogApplicationService(
        CatalogRepository repository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        IamAuditPublisher auditPublisher,
        CatalogPayloadValidator payloadValidator,
        SecretRepository secretRepository,
        ObjectProvider<ObjectStore> objectStoreProvider,
        CatalogProperties properties,
        SkillSupplyChainVerifier skillSupplyChainVerifier,
        Clock clock,
        JsonMapper jsonMapper) {

        return new CatalogApplicationService(
            repository,
            tenantRepository,
            authorizationService,
            auditPublisher,
            payloadValidator,
            secretRepository,
            Optional.ofNullable(objectStoreProvider.getIfAvailable()),
            properties,
            skillSupplyChainVerifier,
            clock,
            jsonMapper);
    }

    /**
     * @param repository           Secret Repository
     * @param tenantRepository     IAM 租户目录
     * @param authorizationService IAM 授权服务
     * @param auditPublisher       审计发布器
     * @param clock                UTC 时钟
     * @return Secret 应用服务
     */
    @Bean
    public SecretApplicationService secretApplicationService(SecretRepository repository, TenantCatalogRepository tenantRepository,
                                                             IamAuthorizationService authorizationService, IamAuditPublisher auditPublisher, Clock clock) {
        return new SecretApplicationService(repository, tenantRepository, authorizationService, auditPublisher, clock);
    }

    /**
     * @param service    Catalog 应用服务
     * @param jsonMapper JSON 映射器
     * @return Catalog Public API
     */
    @Bean
    public CatalogController catalogController(CatalogApplicationService service, JsonMapper jsonMapper) {
        return new CatalogController(service, jsonMapper);
    }

    /**
     * @param service Secret 应用服务
     * @return Secret Public API
     */
    @Bean
    public SecretController secretController(SecretApplicationService service) {
        return new SecretController(service);
    }

    /**
     * @param accessor 请求上下文
     * @return Catalog ProblemDetail 映射器
     */
    @Bean
    public CatalogProblemDetailAdvice catalogProblemDetailAdvice(RequestContextAccessor accessor) {
        return new CatalogProblemDetailAdvice(accessor);
    }

    /**
     * @param accessor 请求上下文
     * @return Secret ProblemDetail 映射器
     */
    @Bean
    public SecretProblemDetailAdvice secretProblemDetailAdvice(RequestContextAccessor accessor) {
        return new SecretProblemDetailAdvice(accessor);
    }

    /**
     * 仅 local Profile 且显式启用时提供文件 Resolver；生产只有 SecretResolver SPI。
     *
     * @param properties Secret 配置
     * @return Local File Secret Resolver
     * @throws IOException 根目录初始化失败时抛出
     */
    @Bean
    @Profile("local")
    @ConditionalOnProperty(
        prefix = "agentark.control.secret",
        name = "local-provider-enabled",
        havingValue = "true")
    public SecretResolver localFileSecretResolver(SecretProperties properties) throws IOException {
        return new LocalFileSecretResolver(properties.getLocalRoot());
    }

    /**
     * 生产 Vault 集成按请求读取工作负载短期令牌文件，支持无重启轮换。
     *
     * @param properties Vault 配置
     * @return 令牌来源
     */
    @Bean
    @Profile("!local & !test")
    @ConditionalOnProperty(
        prefix = "agentark.control.secret.vault",
        name = "enabled",
        havingValue = "true")
    public VaultTokenSource vaultTokenSource(VaultSecretProperties properties) {
        return new FileVaultTokenSource(properties.getTokenFile());
    }

    /**
     * 装配 HTTPS Vault KV v2 解析器与 Secret Access Audit 包装器。
     *
     * @param properties     Vault 配置
     * @param tokenSource    短期令牌来源
     * @param jsonMapper     JSON 映射器
     * @param auditPublisher 审计发布器
     * @param clock          UTC 时钟
     * @return 生产 Secret 解析器
     */
    @Bean
    @Profile("!local & !test")
    @ConditionalOnProperty(
        prefix = "agentark.control.secret.vault",
        name = "enabled",
        havingValue = "true")
    public SecretResolver vaultSecretResolver(VaultSecretProperties properties, VaultTokenSource tokenSource,
                                              JsonMapper jsonMapper, IamAuditPublisher auditPublisher, Clock clock) {

        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(properties.getConnectTimeout())
            .build();

        SecretResolver vault = new VaultKvV2SecretResolver(
            httpClient,
            properties.getAddress(),
            properties.getMount(),
            properties.getNamespace(),
            tokenSource,
            jsonMapper,
            properties.getReadTimeout());
        return new AuditedSecretResolver(vault, auditPublisher, clock);
    }
}
