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

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import space.refinex.agentark.control.iam.adapter.in.security.*;
import space.refinex.agentark.control.iam.adapter.in.web.IamController;
import space.refinex.agentark.control.iam.adapter.in.web.IamInternalApiKeyController;
import space.refinex.agentark.control.iam.adapter.in.web.IamInternalIdentityController;
import space.refinex.agentark.control.iam.adapter.in.web.IamProblemDetailAdvice;
import space.refinex.agentark.control.iam.adapter.out.audit.StructuredLogIamAuditAdapter;
import space.refinex.agentark.control.iam.adapter.out.cache.ShortTtlAuthorizationCache;
import space.refinex.agentark.control.iam.adapter.out.persistence.*;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.*;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import space.refinex.agentark.foundation.web.TenantContextResolver;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * 装配 Control Plane IAM 的领域端口、MyBatis 适配器、认证过滤链与租户纵深防御。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.iam",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({IamProperties.class, IamDevBootstrapProperties.class})
@MapperScan(basePackageClasses = TenantCatalogMapper.class)
@Import({IamSecurityConfiguration.class, IamDevBootstrapConfiguration.class})
public class IamControlConfiguration {

    /**
     * 创建 IAM Control 装配。
     */
    public IamControlConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * 提供全局 UTC 时钟，保证领域时间和持久化时间来源一致。
     *
     * @return 系统 UTC 时钟
     */
    @Bean
    public Clock iamClock() {
        return Clock.systemUTC();
    }

    /**
     * 提供 API Key 专用密码学安全随机源。
     *
     * @return 安全随机源
     */
    @Bean
    public SecureRandom iamSecureRandom() {
        return new SecureRandom();
    }

    /**
     * 创建租户目录持久化端口。
     *
     * @param mapper 租户目录 Mapper
     * @return MyBatis 租户目录适配器
     */
    @Bean
    public TenantCatalogRepository tenantCatalogRepository(TenantCatalogMapper mapper) {
        return new MybatisTenantCatalogRepository(mapper);
    }

    /**
     * 创建身份与成员关系持久化端口。
     *
     * @param mapper 身份 Mapper
     * @return MyBatis 身份适配器
     */
    @Bean
    public IdentityRepository identityRepository(IdentityMapper mapper) {
        return new MybatisIdentityRepository(mapper);
    }

    /**
     * 创建角色、绑定和有效权限持久化端口。
     *
     * @param mapper 授权 Mapper
     * @return MyBatis 授权适配器
     */
    @Bean
    public AuthorizationRepository authorizationRepository(AuthorizationMapper mapper) {
        return new MybatisAuthorizationRepository(mapper);
    }

    /**
     * 创建仅保存 API Key 摘要的持久化端口。
     *
     * @param mapper API Key Mapper
     * @return MyBatis API Key 适配器
     */
    @Bean
    public ApiKeyRepository apiKeyRepository(ApiKeyMapper mapper) {
        return new MybatisApiKeyRepository(mapper);
    }

    /**
     * 创建有界短 TTL 授权缓存。
     *
     * @param clock      UTC 时钟
     * @param properties IAM 配置
     * @return 授权缓存端口
     */
    @Bean
    public AuthorizationCache authorizationCache(Clock clock, IamProperties properties) {
        return new ShortTtlAuthorizationCache(clock, properties.getAuthorizationCacheTtl());
    }

    /**
     * 创建不记录凭据或敏感载荷的结构化审计输出端口。
     *
     * @return 真实审计端口实现
     */
    @Bean
    @ConditionalOnMissingBean(IamAuditPort.class)
    public IamAuditPort iamAuditPort() {
        return new StructuredLogIamAuditAdapter();
    }

    /**
     * 创建事务感知审计发布器。
     *
     * @param auditPort 审计输出端口
     * @return 审计发布器
     */
    @Bean
    public IamAuditPublisher iamAuditPublisher(IamAuditPort auditPort) {
        return new IamAuditPublisher(auditPort);
    }

    /**
     * 创建独立写事务的外部身份首次映射服务。
     *
     * @param identityRepository 身份端口
     * @param clock              UTC 时钟
     * @return 外部身份映射服务
     */
    @Bean
    public IamIdentityMappingService iamIdentityMappingService(
        IdentityRepository identityRepository, Clock clock) {
        return new IamIdentityMappingService(identityRepository, clock);
    }

    /**
     * 创建数据库角色与凭据 Scope 双重校验的应用授权服务。
     *
     * @param identityRepository      身份端口
     * @param identityMappingService  外部身份映射事务服务
     * @param authorizationRepository 授权端口
     * @param authorizationCache      授权缓存
     * @param clock                   UTC 时钟
     * @return 应用授权服务
     */
    @Bean
    public IamAuthorizationService iamAuthorizationService(
        IdentityRepository identityRepository,
        IamIdentityMappingService identityMappingService,
        AuthorizationRepository authorizationRepository,
        AuthorizationCache authorizationCache,
        Clock clock) {

        return new IamAuthorizationService(
            identityRepository,
            identityMappingService,
            authorizationRepository,
            authorizationCache,
            clock);
    }

    /**
     * 创建 IAM 聚合事务应用服务。
     *
     * @param tenantRepository        租户目录端口
     * @param identityRepository      身份端口
     * @param authorizationRepository 授权端口
     * @param authorizationService    授权服务
     * @param eventPublisher          Spring 事件发布器
     * @param auditPublisher          审计发布器
     * @param clock                   UTC 时钟
     * @return IAM 应用服务
     */
    @Bean
    public IamApplicationService iamApplicationService(
        TenantCatalogRepository tenantRepository,
        IdentityRepository identityRepository,
        AuthorizationRepository authorizationRepository,
        IamAuthorizationService authorizationService,
        ApplicationEventPublisher eventPublisher,
        IamAuditPublisher auditPublisher,
        Clock clock) {

        return new IamApplicationService(
            tenantRepository,
            identityRepository,
            authorizationRepository,
            authorizationService,
            eventPublisher,
            auditPublisher,
            clock);
    }

    /**
     * 创建 API Key 单次交付和摘要认证服务。
     *
     * @param secureRandom          安全随机源
     * @param apiKeyRepository      API Key 摘要端口
     * @param identityRepository    身份端口
     * @param authorizationService  授权服务
     * @param iamApplicationService IAM 应用服务
     * @param eventPublisher        Spring 事件发布器
     * @param auditPublisher        审计发布器
     * @param clock                 UTC 时钟
     * @return API Key 服务
     */
    @Bean
    public IamApiKeyService iamApiKeyService(
        SecureRandom secureRandom,
        ApiKeyRepository apiKeyRepository,
        IdentityRepository identityRepository,
        IamAuthorizationService authorizationService,
        IamApplicationService iamApplicationService,
        ApplicationEventPublisher eventPublisher,
        IamAuditPublisher auditPublisher,
        Clock clock) {

        return new IamApiKeyService(
            secureRandom,
            apiKeyRepository,
            identityRepository,
            authorizationService,
            iamApplicationService,
            eventPublisher,
            auditPublisher,
            clock);
    }

    /**
     * 创建提交后授权缓存失效监听器。
     *
     * @param cache 授权缓存端口
     * @return 失效监听器
     */
    @Bean
    public IamAuthorizationCacheInvalidator iamAuthorizationCacheInvalidator(AuthorizationCache cache) {
        return new IamAuthorizationCacheInvalidator(cache);
    }

    /**
     * 创建 API Key Servlet 认证过滤器。
     *
     * @param apiKeyService API Key 认证服务
     * @param problemWriter 安全错误写出器
     * @return API Key 过滤器
     */
    @Bean
    public IamApiKeyAuthenticationFilter iamApiKeyAuthenticationFilter(IamApiKeyService apiKeyService, IamSecurityProblemWriter problemWriter) {
        return new IamApiKeyAuthenticationFilter(apiKeyService, problemWriter);
    }

    /**
     * 创建认证与授权失败的稳定 ProblemDetail 写出器。
     *
     * @param jsonMapper 应用统一 JSON 映射器
     * @return 安全错误写出器
     */
    @Bean
    public IamSecurityProblemWriter iamSecurityProblemWriter(JsonMapper jsonMapper) {
        return new IamSecurityProblemWriter(jsonMapper);
    }

    /**
     * 用数据库授权解析器替换 Foundation 的空租户解析器。
     *
     * @param authorizationService IAM 授权服务
     * @return 已授权租户上下文解析器
     */
    @Bean
    public TenantContextResolver tenantContextResolver(IamAuthorizationService authorizationService) {
        return new IamTenantContextResolver(authorizationService);
    }

    /**
     * 创建 MyBatis-Plus organization_id 纵深防御处理器。
     *
     * @param requestContextAccessor 请求上下文访问器
     * @return Control 租户行处理器
     */
    @Bean
    public ControlTenantLineHandler controlTenantLineHandler(RequestContextAccessor requestContextAccessor) {
        return new ControlTenantLineHandler(requestContextAccessor);
    }

    /**
     * 创建只依赖应用服务的 IAM Public API Controller。
     *
     * @param iamApplicationService IAM 聚合应用服务
     * @param iamApiKeyService      API Key 应用服务
     * @return IAM Controller
     */
    @Bean
    public IamController iamController(IamApplicationService iamApplicationService, IamApiKeyService iamApiKeyService) {
        return new IamController(iamApplicationService, iamApiKeyService);
    }

    /**
     * 创建 API Key 内部自省 Controller；认证仍由 Control 本地摘要校验完成。
     *
     * @return API Key 内部自省 Controller
     */
    @Bean
    public IamInternalApiKeyController iamInternalApiKeyController() {
        return new IamInternalApiKeyController();
    }

    /**
     * 创建 Gateway 内置账号到 Control 用户身份的内部投影入口。
     *
     * @param mappingService 身份映射事务服务
     * @return 内部身份投影 Controller
     */
    @Bean
    public IamInternalIdentityController iamInternalIdentityController(
        IamIdentityMappingService mappingService) {
        return new IamInternalIdentityController(mappingService);
    }

    /**
     * 创建 IAM 稳定 ProblemDetail 异常映射器。
     *
     * @param requestContextAccessor 请求上下文访问器
     * @return IAM 异常映射器
     */
    @Bean
    public IamProblemDetailAdvice iamProblemDetailAdvice(RequestContextAccessor requestContextAccessor) {
        return new IamProblemDetailAdvice(requestContextAccessor);
    }

}
