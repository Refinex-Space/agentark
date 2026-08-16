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

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import space.refinex.agentark.control.iam.adapter.in.bootstrap.IamDevBootstrapRunner;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;

/**
 * 隔离仅限 local Profile 的无凭据 IAM 开发资源引导装配。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(
    prefix = "agentark.control.iam.dev-bootstrap",
    name = "enabled",
    havingValue = "true")
public class IamDevBootstrapConfiguration {

    /**
     * 创建本地开发引导装配。
     */
    public IamDevBootstrapConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * 创建不生成口令、Token 或 API Key 的本地资源引导器。
     *
     * @param properties       开发引导配置
     * @param iamService       IAM 应用服务
     * @param tenantRepository 租户目录端口
     * @return 本地引导执行器
     */
    @Bean
    public ApplicationRunner iamDevBootstrapRunner(
        IamDevBootstrapProperties properties,
        IamApplicationService iamService,
        TenantCatalogRepository tenantRepository) {
        return new IamDevBootstrapRunner(properties, iamService, tenantRepository);
    }
}
