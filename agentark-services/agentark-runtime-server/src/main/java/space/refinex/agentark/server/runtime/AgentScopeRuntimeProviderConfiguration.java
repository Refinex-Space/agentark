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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import space.refinex.agentark.runtime.adapter.out.engine.UnavailableAgentExecutionEngine;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;
import space.refinex.agentark.runtime.port.*;
import space.refinex.agentark.runtime.provider.agentscope.AgentScopeExecutionEngine;
import space.refinex.agentark.runtime.provider.agentscope.RuntimeProviderDescriptor;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeRuntimeComponentFactory;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeRuntimeMaterializer;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeSnapshotCompiler;
import space.refinex.agentark.runtime.provider.agentscope.compiler.SnapshotCompilationCache;
import space.refinex.agentark.runtime.provider.agentscope.event.AgentScopeEventMapper;
import space.refinex.agentark.runtime.provider.agentscope.model.AgentScopeModelFactory;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeInputMapper;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpEndpointGuard;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptMapper;
import space.refinex.agentark.runtime.provider.agentscope.secret.SecretResolver;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;

import java.time.Clock;

/**
 * 装配 AgentScope Provider Descriptor、Compiler，并在生产 SPI 齐备时启用真实执行引擎。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpEndpointSecurityProperties.class)
public class AgentScopeRuntimeProviderConfiguration {

    /**
     * 创建当前固定 AgentScope Provider Descriptor。
     *
     * @return Provider Descriptor
     */
    @Bean
    public RuntimeProviderDescriptor runtimeProviderDescriptor() {
        return RuntimeProviderDescriptor.current();
    }

    /**
     * 将 Provider Descriptor 投影为 Runtime 中立能力目录。
     *
     * @param descriptor AgentScope Provider Descriptor
     * @return Runtime Provider Catalog
     */
    @Bean
    public RuntimeProviderCatalog runtimeProviderCatalog(RuntimeProviderDescriptor descriptor) {
        RuntimeProviderMetadata metadata = new RuntimeProviderMetadata(
            descriptor.providerId(), descriptor.compilerVersion(),
            descriptor.supportedSchemas(), descriptor.capabilities());
        return () -> metadata;
    }

    /**
     * 创建仅用于 AgentScope 2.0.2 Provider 的 Jackson 2 Mapper。
     *
     * @return Jackson 2 Mapper
     */
    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper agentScopeObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * 创建无 Secret、可丢失重建的 Single Flight 编译缓存。
     *
     * @return Snapshot 编译缓存
     */
    @Bean
    public SnapshotCompilationCache snapshotCompilationCache() {
        return new SnapshotCompilationCache();
    }

    /**
     * 创建 AgentScope Snapshot Compiler。
     *
     * @param descriptor   Provider Descriptor
     * @param objectMapper Jackson 2 Mapper
     * @param cache        Single Flight 缓存
     * @param telemetry    Provider Telemetry
     * @return Snapshot Compiler
     */
    @Bean
    public AgentScopeSnapshotCompiler agentScopeSnapshotCompiler(
        RuntimeProviderDescriptor descriptor,
        com.fasterxml.jackson.databind.ObjectMapper objectMapper,
        SnapshotCompilationCache cache,
        AgentArkTelemetry telemetry) {
        return new AgentScopeSnapshotCompiler(descriptor, objectMapper, cache, telemetry);
    }

    /**
     * 创建 Prompt Mapper。
     *
     * @return Prompt Mapper
     */
    @Bean
    public PromptMapper promptMapper() {
        return new PromptMapper();
    }

    /**
     * 创建 MCP SSRF、DNS Rebinding 与 STDIO 命令白名单守卫。
     *
     * @param properties 部署级 MCP 安全配置
     * @return MCP Endpoint 守卫
     */
    @Bean
    public McpEndpointGuard mcpEndpointGuard(McpEndpointSecurityProperties properties) {
        return new McpEndpointGuard(
            properties.getAllowedRemoteHosts(), properties.getAllowedStdioCommands(),
            java.net.InetAddress::getAllByName, Clock.systemUTC(),
            properties.getConnectTimeout(), properties.getRequestTimeout(),
            properties.getMaxResponseBytes());
    }

    /**
     * 创建 Runtime 输入 Mapper。
     *
     * @param objectMapper Jackson 2 Mapper
     * @return Runtime 输入 Mapper
     */
    @Bean
    public RuntimeInputMapper runtimeInputMapper(
        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new RuntimeInputMapper(objectMapper);
    }

    /**
     * 创建 AgentScope Event Mapper。
     *
     * @param objectMapper Jackson 2 Mapper
     * @return Event Mapper
     */
    @Bean
    public AgentScopeEventMapper agentScopeEventMapper(
        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new AgentScopeEventMapper(objectMapper);
    }

    /**
     * 当 Model、组件与 Secret SPI 均存在时创建单 Run Runtime Materializer。
     *
     * @param compiler         Snapshot Compiler
     * @param modelFactory     Model Factory
     * @param componentFactory Runtime Component Factory
     * @param secretResolver   Secret Resolver
     * @param stateStore       Agent State Store
     * @param checkpointStore  Checkpoint Store
     * @param promptMapper     Prompt Mapper
     * @param mcpEndpointGuard MCP Endpoint 守卫
     * @param clock            UTC 时钟
     * @return Runtime Materializer
     */
    @Bean
    @ConditionalOnBean({AgentScopeModelFactory.class, AgentScopeRuntimeComponentFactory.class,
        SecretResolver.class})
    public AgentScopeRuntimeMaterializer agentScopeRuntimeMaterializer(
        AgentScopeSnapshotCompiler compiler,
        AgentScopeModelFactory modelFactory,
        AgentScopeRuntimeComponentFactory componentFactory,
        SecretResolver secretResolver,
        AgentStateStore stateStore,
        CheckpointStore checkpointStore,
        PromptMapper promptMapper,
        McpEndpointGuard mcpEndpointGuard,
        Clock clock) {
        return new AgentScopeRuntimeMaterializer(
            compiler, modelFactory, componentFactory, secretResolver,
            stateStore, checkpointStore, promptMapper, mcpEndpointGuard, clock);
    }

    /**
     * 当 Materializer 已就绪时创建真实 AgentScope 执行引擎。
     *
     * @param materializer Runtime Materializer
     * @param eventMapper  Event Mapper
     * @param inputMapper  输入 Mapper
     * @param signalSink   持久信号接收器
     * @param telemetry    Agent Run Telemetry
     * @return AgentScope 执行引擎
     */
    @Bean
    @ConditionalOnBean(AgentScopeRuntimeMaterializer.class)
    public AgentExecutionEngine agentScopeExecutionEngine(
        AgentScopeRuntimeMaterializer materializer,
        AgentScopeEventMapper eventMapper,
        RuntimeInputMapper inputMapper,
        ExecutionSignalSink signalSink,
        AgentArkTelemetry telemetry) {
        return new AgentScopeExecutionEngine(
            materializer, eventMapper, inputMapper, signalSink, telemetry);
    }

    /**
     * 生产 SPI 未装配时提供明确失败引擎，防止接单后 Run 静默消失。
     *
     * @return Provider 未就绪执行引擎
     */
    @Bean
    @ConditionalOnMissingBean(AgentExecutionEngine.class)
    public AgentExecutionEngine unavailableAgentExecutionEngine() {
        return new UnavailableAgentExecutionEngine();
    }
}
