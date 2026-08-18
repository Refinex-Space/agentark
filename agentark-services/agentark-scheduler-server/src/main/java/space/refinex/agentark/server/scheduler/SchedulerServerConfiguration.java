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

package space.refinex.agentark.server.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionWorker;
import space.refinex.agentark.scheduling.adapter.in.web.SchedulerController;
import space.refinex.agentark.scheduling.adapter.in.web.SchedulerInternalController;
import space.refinex.agentark.scheduling.adapter.in.web.SchedulerProblemDetailAdvice;
import space.refinex.agentark.scheduling.adapter.out.channel.agentscope.AgentScopeChannelAdapter;
import space.refinex.agentark.scheduling.adapter.out.control.HttpControlInternalClient;
import space.refinex.agentark.scheduling.adapter.out.persistence.MybatisSchedulerAuditAdapter;
import space.refinex.agentark.scheduling.adapter.out.persistence.MybatisSchedulerStore;
import space.refinex.agentark.scheduling.adapter.out.persistence.SchedulerMapper;
import space.refinex.agentark.scheduling.adapter.out.runtime.HttpRuntimeInternalClient;
import space.refinex.agentark.scheduling.adapter.out.webhook.JdkOutboundWebhookClient;
import space.refinex.agentark.scheduling.application.*;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;
import space.refinex.agentark.scheduling.port.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * 在非测试 Profile 装配 Scheduler MySQL、版本化 Client、Worker、Cron、Webhook 与管理 API。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableConfigurationProperties(SchedulerServerProperties.class)
@MapperScan(basePackageClasses = SchedulerMapper.class)
public class SchedulerServerConfiguration {

    /**
     * 创建 Scheduler Server 配置。
     */
    public SchedulerServerConfiguration() {
        // Spring 使用无参构造器创建配置实例。
    }

    /**
     * 创建 UTC 系统时钟。
     *
     * @return UTC 时钟
     */
    @Bean
    public Clock schedulerClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建 MyBatis Scheduler Store。
     *
     * @param mapper     Scheduler Mapper
     * @param jsonMapper JSON Mapper
     * @return Scheduler Store
     */
    @Bean
    public MybatisSchedulerStore schedulerStore(
        SchedulerMapper mapper, JsonMapper jsonMapper) {
        return new MybatisSchedulerStore(mapper, jsonMapper);
    }

    /**
     * 创建不可静默丢弃的 Outbox 审计适配器。
     *
     * @param mapper     Scheduler Mapper
     * @param jsonMapper JSON Mapper
     * @param controlClient Control Governance 汇聚客户端
     * @return 审计端口
     */
    @Bean
    public SchedulerAuditPort schedulerAuditPort(
        SchedulerMapper mapper,
        JsonMapper jsonMapper,
        ControlInternalClient controlClient) {
        return new MybatisSchedulerAuditAdapter(mapper, jsonMapper, controlClient);
    }

    /**
     * 创建 Scheduler 应用服务。
     *
     * @param store     Scheduler Store
     * @param auditPort 审计端口
     * @param clock     UTC 时钟
     * @return 应用服务
     */
    @Bean
    public SchedulerApplicationService schedulerApplicationService(
        MybatisSchedulerStore store, SchedulerAuditPort auditPort, Clock clock) {
        return new SchedulerApplicationService(store, auditPort, clock);
    }

    /**
     * 创建管理 API 授权服务。
     *
     * @return 授权服务
     */
    @Bean
    public SchedulerAuthorizationService schedulerAuthorizationService() {
        return new SchedulerAuthorizationService();
    }

    /**
     * 创建 Control v1 Internal Client。
     *
     * @param properties Server 配置
     * @param builders   Spring Boot 可选观测客户端构建器
     * @param observationRegistry 当前 Micrometer Observation Registry
     * @return Control Client
     */
    @Bean
    public ControlInternalClient schedulerControlClient(
        SchedulerServerProperties properties,
        ObjectProvider<RestClient.Builder> builders,
        ObservationRegistry observationRegistry) {
        RestClient.Builder builder = builders.getIfAvailable(
            () -> RestClient.builder().observationRegistry(observationRegistry));
        return new HttpControlInternalClient(
            builder.baseUrl(properties.controlBaseUrl().toString()).build(),
            properties::internalServiceToken);
    }

    /**
     * 创建 Runtime v1 Internal Client。
     *
     * @param properties Server 配置
     * @param builders   Spring Boot 可选观测客户端构建器
     * @param observationRegistry 当前 Micrometer Observation Registry
     * @return Runtime Client
     */
    @Bean
    public RuntimeInternalClient schedulerRuntimeClient(
        SchedulerServerProperties properties,
        ObjectProvider<RestClient.Builder> builders,
        ObservationRegistry observationRegistry) {
        RestClient.Builder builder = builders.getIfAvailable(
            () -> RestClient.builder().observationRegistry(observationRegistry));
        return new HttpRuntimeInternalClient(
            builder.baseUrl(properties.runtimeBaseUrl().toString()).build(),
            properties::internalServiceToken);
    }

    /**
     * 创建 Runtime Turn Handler。
     *
     * @param client       Runtime Client
     * @param objectMapper JSON Mapper
     * @return Runtime Turn Handler
     */
    @Bean
    public RuntimeTurnJobHandler runtimeTurnJobHandler(
        RuntimeInternalClient client, ObjectMapper objectMapper) {
        return new RuntimeTurnJobHandler(client, objectMapper);
    }

    /**
     * 创建禁止自动重定向的 HTTPS Webhook Client。
     *
     * @return JDK Webhook Client
     */
    @Bean
    public OutboundWebhookClient outboundWebhookClient() {
        return new JdkOutboundWebhookClient(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build());
    }

    /**
     * 当 Control 端点解析器已装配时创建 Outbound Webhook Handler。
     *
     * @param client       Webhook Client
     * @param resolver     受 SSRF 策略约束的固定 Endpoint Resolver
     * @param store        Delivery Store
     * @param objectMapper JSON Mapper
     * @param clock        UTC 时钟
     * @return Outbound Webhook Handler
     */
    @Bean
    @ConditionalOnBean(OutboundEndpointResolver.class)
    public OutboundWebhookJobHandler outboundWebhookJobHandler(
        OutboundWebhookClient client,
        OutboundEndpointResolver resolver,
        MybatisSchedulerStore store,
        ObjectMapper objectMapper,
        Clock clock) {
        return new OutboundWebhookJobHandler(client, resolver, store, objectMapper, clock);
    }

    /**
     * 当独立 Provider 组合层提供版本绑定 Bridge 时创建 AgentScope Channel 防腐层。
     *
     * @param bridge AgentScope Channel Bridge
     * @return 中立 Channel Gateway
     */
    @Bean
    @ConditionalOnBean(AgentScopeChannelAdapter.AgentScopeChannelBridge.class)
    @ConditionalOnMissingBean(ChannelGateway.class)
    public ChannelGateway agentScopeChannelGateway(
        AgentScopeChannelAdapter.AgentScopeChannelBridge bridge) {
        return new AgentScopeChannelAdapter(bridge);
    }

    /**
     * 当 Channel Gateway 已装配时创建类型隔离的 Channel Message Handler。
     *
     * @param gateway      中立 Channel Gateway
     * @param store        Delivery Store
     * @param objectMapper JSON Mapper
     * @param clock        UTC 时钟
     * @return Channel Message Handler
     */
    @Bean
    @ConditionalOnBean(ChannelGateway.class)
    public ChannelMessageJobHandler channelMessageJobHandler(
        ChannelGateway gateway,
        MybatisSchedulerStore store,
        ObjectMapper objectMapper,
        Clock clock) {
        return new ChannelMessageJobHandler(gateway, store, objectMapper, clock);
    }

    /**
     * 当生产 Provider 已装配 Phase 14 Worker 时创建 Knowledge 摄取 Handler。
     *
     * @param workerProvider 可选 Knowledge Worker
     * @param objectMapper   JSON Mapper
     * @return 可选 Handler；缺少 Provider 时不 Claim 该类型 Job
     */
    @Bean
    @ConditionalOnBean(KnowledgeIngestionWorker.class)
    public KnowledgeIngestionJobHandler knowledgeIngestionJobHandler(
        ObjectProvider<KnowledgeIngestionWorker> workerProvider,
        ObjectMapper objectMapper) {
        return new KnowledgeIngestionJobHandler(
            workerProvider.getObject(), objectMapper);
    }

    /**
     * 创建失败默认的 Webhook Secret Resolver；生产必须用 Secret Provider Bean 替换。
     *
     * @return 失败默认 Resolver
     */
    @Bean
    @ConditionalOnMissingBean(WebhookSecretResolver.class)
    public WebhookSecretResolver webhookSecretResolver() {
        return secretRef -> {
            throw new IllegalStateException("webhook secret provider is not configured");
        };
    }

    /**
     * 创建 Webhook HMAC 验证器。
     *
     * @param resolver Secret Resolver
     * @param clock    UTC 时钟
     * @return 验签器
     */
    @Bean
    public WebhookSignatureVerifier webhookSignatureVerifier(
        WebhookSecretResolver resolver, Clock clock) {
        return new WebhookSignatureVerifier(resolver, clock, Duration.ofMinutes(5));
    }

    /**
     * 创建 Webhook 接入服务。
     *
     * @param store    Trigger Store
     * @param verifier 验签器
     * @param clock    UTC 时钟
     * @return Webhook 服务
     */
    @Bean
    public WebhookIngressService webhookIngressService(
        MybatisSchedulerStore store, WebhookSignatureVerifier verifier, Clock clock) {
        return new WebhookIngressService(store, verifier, clock, Duration.ofHours(24));
    }

    /**
     * 创建 Cron 时间计算器。
     *
     * @return Cron Calculator
     */
    @Bean
    public CronCalculator cronCalculator() {
        return new CronCalculator();
    }

    /**
     * 创建 Cron Trigger 服务。
     *
     * @param store      Trigger Store
     * @param calculator Cron Calculator
     * @param clock      UTC 时钟
     * @param telemetry  Scheduler Telemetry
     * @param jsonMapper 目标 Job Payload JSON 映射器
     * @return Cron 服务
     */
    @Bean
    public CronTriggerService cronTriggerService(
        MybatisSchedulerStore store,
        CronCalculator calculator,
        Clock clock,
        JsonMapper jsonMapper) {
        return new CronTriggerService(store, calculator, clock, jsonMapper);
    }

    /**
     * 创建持久 Trigger 登记服务。
     *
     * @param store      Trigger Store
     * @param calculator Cron Calculator
     * @param clock      UTC 时钟
     * @return Trigger 定义服务
     */
    @Bean
    public TriggerDefinitionService triggerDefinitionService(
        MybatisSchedulerStore store, CronCalculator calculator, Clock clock) {
        return new TriggerDefinitionService(store, calculator, clock);
    }

    /**
     * 创建按 Job Type 隔离的 Worker Pool。
     *
     * @param store      Scheduler Store
     * @param handlers   当前已装配 Handler
     * @param properties Server 配置
     * @param clock      UTC 时钟
     * @return Scheduler Worker
     */
    @Bean(destroyMethod = "close")
    public SchedulerWorker schedulerWorker(
        MybatisSchedulerStore store,
        List<JobHandler> handlers,
        SchedulerServerProperties properties,
        Clock clock,
        AgentArkTelemetry telemetry) {
        EnumMap<JobType, Integer> pools = new EnumMap<>(JobType.class);
        for (JobType type : JobType.values()) {
            pools.put(type, properties.workerPoolSize());
        }
        return new SchedulerWorker(
            store, handlers, pools, clock, new Random(),
            properties.instanceKey(), properties.leaseTtl(), telemetry);
    }

    /**
     * 注册 Scheduler 队列指标。
     *
     * @param store    Scheduler Store
     * @param registry Meter Registry
     * @param clock    UTC 时钟
     * @return 指标注册句柄
     */
    @Bean
    public SchedulerMetrics schedulerMetrics(
        MybatisSchedulerStore store, MeterRegistry registry, Clock clock) {
        return new SchedulerMetrics(store, registry, clock);
    }

    /**
     * 创建 Scheduler API Controller。
     *
     * @param service              应用服务
     * @param authorizationService 授权服务
     * @param webhookService       Webhook 服务
     * @param triggerService       Trigger 定义服务
     * @return Controller
     */
    @Bean
    public SchedulerController schedulerController(
        SchedulerApplicationService service,
        SchedulerAuthorizationService authorizationService,
        WebhookIngressService webhookService,
        TriggerDefinitionService triggerService) {
        return new SchedulerController(
            service, authorizationService, webhookService, triggerService);
    }

    /**
     * 创建仅接受 Audience 受限服务身份的 Scheduler Internal API Controller。
     *
     * @param service        Scheduler 应用服务
     * @param triggerService Trigger 定义服务
     * @return Scheduler Internal Controller
     */
    @Bean
    public SchedulerInternalController schedulerInternalController(
        SchedulerApplicationService service,
        TriggerDefinitionService triggerService) {
        return new SchedulerInternalController(service, triggerService);
    }

    /**
     * 创建 Scheduler ProblemDetail Advice。
     *
     * @return Advice
     */
    @Bean
    public SchedulerProblemDetailAdvice schedulerProblemDetailAdvice() {
        return new SchedulerProblemDetailAdvice();
    }

    /**
     * 创建 Worker 与 Cron 循环。
     *
     * @param worker     Scheduler Worker
     * @param cron       Cron 服务
     * @param properties Server 配置
     * @return Worker Loop
     */
    @Bean
    public SchedulerWorkerLoop schedulerWorkerLoop(
        SchedulerWorker worker,
        CronTriggerService cron,
        SchedulerServerProperties properties) {
        return new SchedulerWorkerLoop(worker, cron, properties);
    }

}
