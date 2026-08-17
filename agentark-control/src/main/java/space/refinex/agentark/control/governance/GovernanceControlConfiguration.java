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

package space.refinex.agentark.control.governance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import space.refinex.agentark.control.governance.adapter.in.web.GovernanceController;
import space.refinex.agentark.control.governance.adapter.out.audit.PersistentIamAuditAdapter;
import space.refinex.agentark.control.governance.adapter.out.persistence.*;
import space.refinex.agentark.control.governance.application.*;
import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.port.IamAuditPort;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.release.application.port.ReleaseGatePolicy;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * 装配 Control/Governance 持久化、应用、公共 API、内部命令和 Release Gate。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.governance",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean({TenantCatalogRepository.class, ReleaseRepository.class})
@MapperScan(basePackageClasses = GovernanceMapper.class)
public class GovernanceControlConfiguration {

    /** 创建无状态 Governance 配置。 */
    public GovernanceControlConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * 创建 MyBatis Governance Repository。
     *
     * @param mapper     Governance Mapper
     * @param jsonMapper JSON 映射器
     * @return Governance Repository
     */
    @Bean
    public GovernanceRepository governanceRepository(
        GovernanceMapper mapper, JsonMapper jsonMapper) {
        return new MybatisGovernanceRepository(mapper, jsonMapper);
    }

    /**
     * 创建 Governance 应用服务。
     *
     * @param repository           Governance Repository
     * @param tenantRepository     租户目录
     * @param authorizationService IAM 授权服务
     * @param releaseRepository    Release Repository
     * @param clock                UTC 时钟
     * @return Governance 应用服务
     */
    @Bean
    public GovernanceApplicationService governanceApplicationService(
        GovernanceRepository repository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        ReleaseRepository releaseRepository,
        Clock clock) {
        return new GovernanceApplicationService(
            repository, tenantRepository, authorizationService, releaseRepository, clock);
    }

    /**
     * 创建 Release Gate Adapter。
     *
     * @param repository Governance Repository
     * @return Release Gate Policy
     */
    @Bean
    public ReleaseGatePolicy releaseGatePolicy(GovernanceRepository repository) {
        return new GovernanceReleaseGatePolicy(repository);
    }

    /**
     * 使用持久 Audit 取代 Phase 07 临时结构化日志 Sink。
     *
     * @param repository             Governance Repository
     * @param requestContextAccessor 请求上下文
     * @param clock                  UTC 时钟
     * @return 持久 IAM Audit Port
     */
    @Bean
    @Primary
    public IamAuditPort governanceIamAuditPort(
        GovernanceRepository repository,
        RequestContextAccessor requestContextAccessor,
        Clock clock) {
        return new PersistentIamAuditAdapter(repository, requestContextAccessor, clock);
    }

    /**
     * 创建 Governance Public/Internal Controller。
     *
     * @param service Governance 应用服务
     * @return Governance Controller
     */
    @Bean
    public GovernanceController governanceController(GovernanceApplicationService service) {
        return new GovernanceController(service);
    }
}
