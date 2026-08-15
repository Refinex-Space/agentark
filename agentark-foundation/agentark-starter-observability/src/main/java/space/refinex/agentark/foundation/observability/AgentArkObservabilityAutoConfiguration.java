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

package space.refinex.agentark.foundation.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * 装配安全采集策略、Tag 白名单、JSON Structured Logging 和可选 OTel/Micrometer 记录器。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "agentark.foundation.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentArkObservabilityAutoConfiguration {

    /**
     * 创建可观测性自动配置。
     */
    public AgentArkObservabilityAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建正文默认不采集的数据策略。
     *
     * @param properties 可观测配置属性
     * @return 不包含 Secret 开关的数据策略
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservabilityDataPolicy observabilityDataPolicy(
        AgentArkObservabilityProperties properties) {
        return new ObservabilityDataPolicy(
            properties.isCollectPromptText(),
            properties.isCollectToolArguments(),
            properties.isCollectDocumentText());
    }

    /**
     * 创建 Metric Tag 与 Span Attribute 白名单策略。
     *
     * @param properties 可观测配置属性
     * @return 低基数标签策略
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricTagPolicy metricTagPolicy(AgentArkObservabilityProperties properties) {
        return new MetricTagPolicy(properties.getAllowedTags());
    }

    /**
     * 创建敏感数据和正文清理器。
     *
     * @param dataPolicy 数据采集策略
     * @return 日志字段清理器
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataSanitizer sensitiveDataSanitizer(ObservabilityDataPolicy dataPolicy) {
        return new SensitiveDataSanitizer(dataPolicy);
    }

    /**
     * 创建单行 JSON Structured Log Writer。
     *
     * @param jsonMapper Jackson 3 映射器
     * @param sanitizer  敏感字段清理器
     * @return 结构化日志写入器
     */
    @Bean
    @ConditionalOnMissingBean
    public StructuredLogWriter structuredLogWriter(
        JsonMapper jsonMapper, SensitiveDataSanitizer sanitizer) {
        return new StructuredLogWriter(jsonMapper, sanitizer);
    }

    /**
     * 创建使用已有 Micrometer 与 OpenTelemetry Bean 的记录器，不隐式创建孤立 Registry。
     *
     * @param meterRegistries 可选 Micrometer Registry Provider
     * @param openTelemetries 可选 OpenTelemetry Provider
     * @param tagPolicy       标签白名单
     * @return AgentArk 可观测记录器
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentArkTelemetry agentArkTelemetry(
        ObjectProvider<MeterRegistry> meterRegistries,
        ObjectProvider<OpenTelemetry> openTelemetries,
        MetricTagPolicy tagPolicy) {
        return new AgentArkTelemetry(
            Optional.ofNullable(meterRegistries.getIfUnique()),
            Optional.ofNullable(openTelemetries.getIfUnique()),
            tagPolicy);
    }
}
