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

package space.refinex.agentark.server.runtime;

import org.mybatis.spring.annotation.MapperScan;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import io.micrometer.core.instrument.MeterRegistry;
import space.refinex.agentark.foundation.redis.DistributedLeaseManager;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeController;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeInternalController;
import space.refinex.agentark.runtime.adapter.in.web.RuntimeProblemDetailAdvice;
import space.refinex.agentark.runtime.adapter.out.control.ControlPlaneRuntimeClient;
import space.refinex.agentark.runtime.adapter.out.coordination.RedisRuntimeLeaseCoordinator;
import space.refinex.agentark.runtime.adapter.out.engine.UnavailableAgentExecutionEngine;
import space.refinex.agentark.runtime.adapter.out.event.InMemoryRuntimeEventNotifier;
import space.refinex.agentark.runtime.adapter.out.persistence.MybatisRuntimeStore;
import space.refinex.agentark.runtime.adapter.out.persistence.RuntimeMapper;
import space.refinex.agentark.runtime.adapter.out.storage.ObjectStoreRuntimePayloadExternalizer;
import space.refinex.agentark.runtime.application.*;
import space.refinex.agentark.runtime.port.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;

/**
 * 在非测试 Profile 装配 Runtime MySQL 权威状态、Control Client、Lease、API 与 Worker。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
@EnableConfigurationProperties(RuntimeServerProperties.class)
@Import(AgentScopeRuntimeProviderConfiguration.class)
@MapperScan(basePackageClasses = RuntimeMapper.class)
public class RuntimeServerConfiguration {

    /**
     * 创建 UTC 系统时钟。
     *
     * @return UTC 时钟
     */
    @Bean
    public Clock runtimeClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建 Runtime MyBatis 聚合适配器。
     *
     * @param mapper     Runtime Mapper
     * @param jsonMapper Jackson 3 JsonMapper
     * @return Runtime Store
     */
    @Bean
    public MybatisRuntimeStore mybatisRuntimeStore(
        RuntimeMapper mapper, JsonMapper jsonMapper) {
        return new MybatisRuntimeStore(mapper, jsonMapper);
    }

    /**
     * 创建进程内有界 Event 通知器；持久轮询负责跨实例修复。
     *
     * @return Event 通知器
     */
    @Bean
    public RuntimeEventNotifier runtimeEventNotifier() {
        return new InMemoryRuntimeEventNotifier();
    }

    /**
     * 创建大型 Event Object Store 外置器。
     *
     * @param objectStore 受控 Object Store
     * @return 载荷外置器
     */
    @Bean
    public RuntimePayloadExternalizer runtimePayloadExternalizer(ObjectStore objectStore) {
        return new ObjectStoreRuntimePayloadExternalizer(objectStore);
    }

    /**
     * 创建 Runtime 权威应用服务；Engine 使用 Lazy 打破信号接收器装配环。
     *
     * @param store          Runtime Store
     * @param snapshotLoader Snapshot Loader
     * @param engine         Provider 执行引擎
     * @param clock          UTC 时钟
     * @return Runtime 应用服务
     */
    @Bean
    public RuntimeApplicationService runtimeApplicationService(
        MybatisRuntimeStore store,
        SnapshotLoader snapshotLoader,
        @Lazy AgentExecutionEngine engine,
        Clock clock) {
        return new RuntimeApplicationService(
            store, store, store, store, snapshotLoader, engine, clock);
    }

    /**
     * 创建 Control Internal API 客户端和 ETag Snapshot Cache。
     *
     * @param properties      Runtime Server 配置
     * @param objectMapper    Jackson 3 Mapper
     * @param providerCatalog Provider 能力目录
     * @param builders        Spring Boot 可选观测客户端构建器
     * @param observationRegistry 当前 Micrometer Observation Registry
     * @return Control Client
     */
    @Bean
    public ControlPlaneRuntimeClient controlPlaneRuntimeClient(
        RuntimeServerProperties properties,
        ObjectMapper objectMapper,
        RuntimeProviderCatalog providerCatalog,
        ObjectProvider<WebClient.Builder> builders,
        ObservationRegistry observationRegistry) {
        if (properties.getControlBaseUrl() == null) {
            throw new IllegalStateException("agentark.runtime.control-base-url is required");
        }
        WebClient.Builder builder = builders.getIfAvailable(
            () -> WebClient.builder().observationRegistry(observationRegistry));
        return new ControlPlaneRuntimeClient(
            builder
                .baseUrl(properties.getControlBaseUrl().toString())
                .build(),
            objectMapper,
            properties::getInternalServiceToken,
            providerCatalog);
    }

    /**
     * 创建短事务 Worker/HITL 协调器。
     *
     * @param store    Runtime Store
     * @param notifier Event 通知器
     * @param quotaPort Control 并发配额 Reservation 端口
     * @param governanceAuditClient Control Audit 汇聚端口
     * @param telemetry Runtime Telemetry
     * @param clock    UTC 时钟
     * @return 执行协调器
     */
    @Bean
    public RuntimeExecutionCoordinator runtimeExecutionCoordinator(
        MybatisRuntimeStore store,
        RuntimeEventNotifier notifier,
        RuntimeQuotaPort quotaPort,
        GovernanceAuditClient governanceAuditClient,
        AgentArkTelemetry telemetry,
        Clock clock) {
        return new RuntimeExecutionCoordinator(
            store, store, store, store, store, store, notifier, quotaPort,
            governanceAuditClient, telemetry, clock);
    }

    /**
     * 创建 Provider 信号持久化接收器。
     *
     * @param store               Runtime Store
     * @param runtimeService      Runtime 应用服务
     * @param payloadExternalizer 大载荷外置器
     * @param notifier            Event 通知器
     * @param objectMapper        Jackson 3 Mapper
     * @param clock               UTC 时钟
     * @param telemetry           Provider Event Telemetry
     * @return 执行信号接收器
     */
    @Bean
    public ExecutionSignalSink executionSignalSink(
        MybatisRuntimeStore store,
        RuntimeApplicationService runtimeService,
        RuntimePayloadExternalizer payloadExternalizer,
        RuntimeEventNotifier notifier,
        ObjectMapper objectMapper,
        Clock clock,
        AgentArkTelemetry telemetry) {
        return new PersistentExecutionSignalSink(
            store, store, store, runtimeService, payloadExternalizer,
            notifier, objectMapper, clock, telemetry);
    }

    /**
     * 创建 Runtime Admission Service。
     *
     * @param deploymentResolver Deployment Resolver
     * @param snapshotLoader     Snapshot Loader
     * @param providerCatalog    Provider 目录
     * @param runtimeService     Runtime 应用服务
     * @param quotaPort          Control Quota 端口
     * @param telemetry          Runtime Telemetry
     * @return Admission Service
     */
    @Bean
    public RuntimeAdmissionService runtimeAdmissionService(
        DeploymentResolver deploymentResolver,
        SnapshotLoader snapshotLoader,
        RuntimeProviderCatalog providerCatalog,
        RuntimeApplicationService runtimeService,
        RuntimeQuotaPort quotaPort,
        AgentArkTelemetry telemetry) {
        return new RuntimeAdmissionService(
            deploymentResolver, snapshotLoader, providerCatalog, runtimeService, quotaPort,
            telemetry);
    }

    /**
     * 创建 Runtime Usage 治理汇聚 Worker。
     *
     * @param store  Runtime Usage Store
     * @param client Control Governance Client
     * @param clock  UTC 时钟
     * @return Usage Governance Worker
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.runtime",
        name = "usage-governance-enabled",
        havingValue = "true")
    public RuntimeUsageGovernanceWorker runtimeUsageGovernanceWorker(
        UsageGovernanceStore store, UsageGovernanceClient client, Clock clock) {
        return new RuntimeUsageGovernanceWorker(store, client, clock);
    }

    /**
     * 注册 Runtime 低基数权威状态指标。
     *
     * @param store    Runtime Store
     * @param registry Meter Registry
     * @param clock    UTC 时钟
     * @return Runtime 指标注册句柄
     */
    @Bean
    public RuntimeMetrics runtimeMetrics(
        MybatisRuntimeStore store, MeterRegistry registry, Clock clock) {
        return new RuntimeMetrics(store, registry, clock);
    }

    /**
     * 创建 Runtime 查询服务。
     *
     * @param store Runtime Store
     * @return 查询服务
     */
    @Bean
    public RuntimeQueryService runtimeQueryService(MybatisRuntimeStore store) {
        return new RuntimeQueryService(store, store, store, store);
    }

    /**
     * 创建 Runtime 运行控制服务。
     *
     * @param coordinator 执行协调器
     * @param engine      Provider 引擎
     * @return 运行控制服务
     */
    @Bean
    public RuntimeControlService runtimeControlService(
        RuntimeExecutionCoordinator coordinator, AgentExecutionEngine engine) {
        return new RuntimeControlService(coordinator, engine);
    }

    /**
     * 创建 Runtime 授权服务。
     *
     * @return 授权服务
     */
    @Bean
    public RuntimeAuthorizationService runtimeAuthorizationService() {
        return new RuntimeAuthorizationService();
    }

    /**
     * 创建 Event 回放与实时追平服务。
     *
     * @param store    Runtime Store
     * @param notifier Event 通知器
     * @return Event Stream 服务
     */
    @Bean
    public RuntimeEventStreamService runtimeEventStreamService(
        MybatisRuntimeStore store, RuntimeEventNotifier notifier) {
        return new RuntimeEventStreamService(store, notifier);
    }

    /**
     * 创建 Runtime Public API Controller。
     *
     * @param admissionService     Admission Service
     * @param queryService         查询服务
     * @param controlService       控制服务
     * @param coordinator          执行协调器
     * @param authorizationService 授权服务
     * @param eventStreamService   Event Stream 服务
     * @param objectMapper         Jackson 3 Mapper
     * @param providerCatalog      Provider 目录
     * @param objectStore          可选对象存储
     * @return Runtime Controller
     */
    @Bean
    public RuntimeController runtimeController(
        RuntimeAdmissionService admissionService,
        RuntimeQueryService queryService,
        RuntimeControlService controlService,
        RuntimeExecutionCoordinator coordinator,
        RuntimeAuthorizationService authorizationService,
        RuntimeEventStreamService eventStreamService,
        ObjectMapper objectMapper,
        RuntimeProviderCatalog providerCatalog,
        Optional<space.refinex.agentark.foundation.storage.ObjectStore> objectStore) {
        return new RuntimeController(
            admissionService, queryService, controlService, coordinator,
            authorizationService, eventStreamService, objectMapper, providerCatalog, objectStore);
    }

    /**
     * 创建只接受 Audience 受限服务身份的 Runtime Internal API Controller。
     *
     * @param admissionService Runtime 接单服务
     * @param queryService     Runtime 查询服务
     * @param providerCatalog  Runtime Provider 目录
     * @return Runtime Internal Controller
     */
    @Bean
    public RuntimeInternalController runtimeInternalController(
        RuntimeAdmissionService admissionService,
        RuntimeQueryService queryService,
        RuntimeProviderCatalog providerCatalog) {
        return new RuntimeInternalController(admissionService, queryService, providerCatalog);
    }

    /**
     * 创建 Runtime ProblemDetail Advice。
     *
     * @return ProblemDetail Advice
     */
    @Bean
    public RuntimeProblemDetailAdvice runtimeProblemDetailAdvice() {
        return new RuntimeProblemDetailAdvice();
    }

    /**
     * 创建 Redis + MySQL 双层 Lease 协调器。
     *
     * @param redisLeases Redis Lease
     * @param store       MySQL Lease
     * @param clock       UTC 时钟
     * @return 执行 Lease 协调器
     */
    @Bean(destroyMethod = "close")
    public RedisRuntimeLeaseCoordinator executionLeaseCoordinator(
        DistributedLeaseManager redisLeases, MybatisRuntimeStore store, Clock clock) {
        return new RedisRuntimeLeaseCoordinator(redisLeases, store, clock);
    }

    /**
     * Worker 显式启用时创建 Runtime Worker。
     *
     * @param properties       Runtime 配置
     * @param coordinator      执行协调器
     * @param leaseCoordinator Lease 协调器
     * @param snapshotLoader   Snapshot Loader
     * @param engine           Provider 引擎
     * @return Runtime Worker
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.runtime", name = "worker-enabled", havingValue = "true")
    public RuntimeWorker runtimeWorker(
        RuntimeServerProperties properties,
        RuntimeExecutionCoordinator coordinator,
        ExecutionLeaseCoordinator leaseCoordinator,
        SnapshotLoader snapshotLoader,
        AgentExecutionEngine engine) {
        if (engine instanceof UnavailableAgentExecutionEngine) {
            throw new IllegalStateException(
                "runtime worker requires AgentScope model, component and secret provider beans");
        }
        return new RuntimeWorker(
            requiredInstanceKey(properties), properties.getLeaseTtl(), coordinator,
            leaseCoordinator, snapshotLoader, engine);
    }

    /**
     * Worker 显式启用时创建轮询调度器。
     *
     * @param worker            Runtime Worker
     * @param instanceLifecycle Runtime 实例生命周期
     * @return Worker Scheduler
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.runtime", name = "worker-enabled", havingValue = "true")
    public RuntimeWorkerScheduler runtimeWorkerScheduler(
        RuntimeWorker worker, RuntimeInstanceLifecycle instanceLifecycle) {
        return new RuntimeWorkerScheduler(worker, instanceLifecycle);
    }

    /**
     * 创建 Runtime Instance 心跳与排空生命周期。
     *
     * @param store           Instance 仓储
     * @param providerCatalog Provider 目录
     * @param properties      Runtime 配置
     * @param clock           UTC 时钟
     * @return Instance 生命周期
     */
    @Bean
    public RuntimeInstanceLifecycle runtimeInstanceLifecycle(
        MybatisRuntimeStore store,
        RuntimeProviderCatalog providerCatalog,
        RuntimeServerProperties properties,
        Clock clock) {
        return new RuntimeInstanceLifecycle(
            store, providerCatalog, requiredInstanceKey(properties), clock);
    }

    /**
     * 校验并返回 Runtime Instance Key。
     *
     * @param properties Runtime 配置
     * @return 非空 Instance Key
     */
    private String requiredInstanceKey(RuntimeServerProperties properties) {
        String value = properties.getInstanceKey();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("agentark.runtime.instance-key is required");
        }
        return value;
    }
}
