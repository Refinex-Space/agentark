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

package space.refinex.agentark.foundation.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

/**
 * 在存在 DataSource 时装配 MyBatis-Plus 分页、乐观锁与受控类型映射，不创建业务 Mapper 或表。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkPersistenceProperties.class)
@ConditionalOnClass({MybatisPlusInterceptor.class, DataSource.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
    prefix = "agentark.foundation.persistence",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentArkPersistenceAutoConfiguration {

    /**
     * 创建持久化基础自动配置。
     */
    public AgentArkPersistenceAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建固定 MySQL 方言的分页和乐观锁拦截器链。
     *
     * @param properties        持久化配置属性
     * @param tenantLineHandler 所属平面可选的租户表达式处理器
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
        AgentArkPersistenceProperties properties,
        ObjectProvider<TenantLineHandler> tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (properties.isTenantDefenseEnabled()) {
            tenantLineHandler.ifAvailable(
                handler -> interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(handler)));
        }
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(properties.getMaxPageSize());
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 创建只记录 Mapper 语句标识、耗时和结果的 SQL 遥测插件，禁止记录 SQL 正文与参数值。
     *
     * @param properties    持久化配置属性
     * @param meterRegistry 可选的 Micrometer 指标注册表
     * @return MyBatis 语句遥测插件
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.persistence",
        name = "sql-telemetry-enabled",
        havingValue = "true",
        matchIfMissing = true)
    public MybatisStatementTelemetryInterceptor mybatisStatementTelemetryInterceptor(
        AgentArkPersistenceProperties properties,
        ObjectProvider<MeterRegistry> meterRegistry) {
        return new MybatisStatementTelemetryInterceptor(
            properties.getSlowQueryThreshold(), Optional.ofNullable(meterRegistry.getIfAvailable()));
    }

    /**
     * 注册 UUIDv7、UTC Instant、JSON 节点和严格枚举 TypeHandler。
     *
     * @param jsonMapper 应用统一 Jackson 3 映射器
     * @return MyBatis-Plus 配置定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "agentArkTypeHandlerCustomizer")
    public ConfigurationCustomizer agentArkTypeHandlerCustomizer(JsonMapper jsonMapper) {
        return configuration -> {
            configuration.setDefaultEnumTypeHandler(EnumTypeHandler.class);
            configuration
                .getTypeHandlerRegistry()
                .register(UUID.class, JdbcType.BINARY, new UuidV7BinaryTypeHandler());
            configuration
                .getTypeHandlerRegistry()
                .register(Instant.class, JdbcType.TIMESTAMP, new UtcInstantTypeHandler());
            configuration
                .getTypeHandlerRegistry()
                .register(JsonNode.class, new JsonNodeTypeHandler(jsonMapper));
        };
    }
}
