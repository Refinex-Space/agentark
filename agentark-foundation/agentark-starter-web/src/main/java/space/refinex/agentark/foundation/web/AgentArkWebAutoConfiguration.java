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

package space.refinex.agentark.foundation.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;

/**
 * 装配与具体 Servlet 或 Reactive 栈无关的 Web 基础契约。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkWebProperties.class)
@ConditionalOnProperty(
    prefix = "agentark.foundation.web",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentArkWebAutoConfiguration {

    /**
     * 创建无状态 Web 公共自动配置。
     */
    public AgentArkWebAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 提供默认的空租户解析器，安全 Starter 或应用可替换为已认证实现。
     *
     * @return 永不信任客户端租户 Header 的默认解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantContextResolver tenantContextResolver() {
        return java.util.Optional::empty;
    }

    /**
     * 提供同步请求上下文访问器。
     *
     * @return 每个应用上下文独立的访问器
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestContextAccessor requestContextAccessor() {
        return new RequestContextAccessor();
    }

    /**
     * 提供请求关联标识与认证租户上下文工厂。
     *
     * @param tenantContextResolver 租户上下文解析器
     * @return 请求上下文工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestContextFactory requestContextFactory(TenantContextResolver tenantContextResolver) {
        return new RequestContextFactory(tenantContextResolver);
    }

    /**
     * 提供 RFC 9457 ProblemDetail 工厂。
     *
     * @return 安全错误映射工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailFactory problemDetailFactory() {
        return new ProblemDetailFactory();
    }

    /**
     * 配置 UTC/ISO 时间、严格枚举与强类型 ID 字符串映射。
     *
     * @return Jackson 3 JsonMapper 定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "agentArkJsonMapperBuilderCustomizer")
    public JsonMapperBuilderCustomizer agentArkJsonMapperBuilderCustomizer() {
        return builder ->
            builder
                .addModule(new AgentArkJacksonModule())
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    }
}
