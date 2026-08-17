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

package space.refinex.agentark.runtime.application;

import space.refinex.agentark.kernel.id.DeploymentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.CreateSessionCommand;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.DeploymentResolver;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;
import space.refinex.agentark.runtime.port.SnapshotLoader;
import space.refinex.agentark.runtime.port.RuntimeQuotaPort;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import space.refinex.agentark.foundation.observability.SpanConvention;

import java.util.Map;
import java.util.Objects;
import java.time.Duration;

/**
 * 在 Session 创建事务前解析 Deployment、Snapshot 与 Provider Capability，并固定运行身份。
 *
 * @author refinex
 */
public final class RuntimeAdmissionService {

    /**
     * Control Deployment 解析端口。
     */
    private final DeploymentResolver deploymentResolver;

    /**
     * Control Snapshot 加载端口。
     */
    private final SnapshotLoader snapshotLoader;

    /**
     * 当前进程 Provider 能力目录。
     */
    private final RuntimeProviderCatalog providerCatalog;

    /**
     * Runtime 权威应用服务。
     */
    private final RuntimeApplicationService runtimeService;

    /** Runtime 接单前并发 Quota 预留端口。 */
    private final RuntimeQuotaPort quotaPort;

    /** Runtime 接单关键路径 Telemetry。 */
    private final AgentArkTelemetry telemetry;

    /**
     * 创建 Runtime 接单服务。
     *
     * @param deploymentResolver Deployment Resolver
     * @param snapshotLoader     Snapshot Loader
     * @param providerCatalog    Provider 能力目录
     * @param runtimeService     Runtime 权威应用服务
     */
    public RuntimeAdmissionService(
        DeploymentResolver deploymentResolver,
        SnapshotLoader snapshotLoader,
        RuntimeProviderCatalog providerCatalog,
        RuntimeApplicationService runtimeService) {
        this(
            deploymentResolver, snapshotLoader, providerCatalog, runtimeService,
            RuntimeQuotaPort.noop(), AgentArkTelemetry.noop());
    }

    /**
     * 创建带真实 Quota Reservation 的 Runtime 接单服务。
     *
     * @param deploymentResolver Deployment Resolver
     * @param snapshotLoader     Snapshot Loader
     * @param providerCatalog    Provider 能力目录
     * @param runtimeService     Runtime 权威应用服务
     * @param quotaPort          Quota Reservation 端口
     */
    public RuntimeAdmissionService(
        DeploymentResolver deploymentResolver,
        SnapshotLoader snapshotLoader,
        RuntimeProviderCatalog providerCatalog,
        RuntimeApplicationService runtimeService,
        RuntimeQuotaPort quotaPort) {
        this(
            deploymentResolver, snapshotLoader, providerCatalog, runtimeService, quotaPort,
            AgentArkTelemetry.noop());
    }

    /**
     * 创建带真实 Quota 与 Telemetry 的 Runtime 接单服务。
     *
     * @param deploymentResolver Deployment Resolver
     * @param snapshotLoader     Snapshot Loader
     * @param providerCatalog    Provider 能力目录
     * @param runtimeService     Runtime 权威应用服务
     * @param quotaPort          Quota Reservation 端口
     * @param telemetry          Runtime Telemetry
     */
    public RuntimeAdmissionService(
        DeploymentResolver deploymentResolver,
        SnapshotLoader snapshotLoader,
        RuntimeProviderCatalog providerCatalog,
        RuntimeApplicationService runtimeService,
        RuntimeQuotaPort quotaPort,
        AgentArkTelemetry telemetry) {
        this.deploymentResolver = Objects.requireNonNull(
            deploymentResolver, "deploymentResolver must not be null");
        this.snapshotLoader = Objects.requireNonNull(
            snapshotLoader, "snapshotLoader must not be null");
        this.providerCatalog = Objects.requireNonNull(
            providerCatalog, "providerCatalog must not be null");
        this.runtimeService = Objects.requireNonNull(
            runtimeService, "runtimeService must not be null");
        this.quotaPort = Objects.requireNonNull(quotaPort, "quotaPort must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /**
     * 解析并固定 Deployment 当前 Revision/Snapshot 后创建 Session。
     *
     * @param organizationId      调用方已授权组织
     * @param projectId           调用方已授权项目
     * @param deploymentId        Deployment 标识
     * @param participantMetadata 非敏感参与者元数据
     * @param channelMetadata     非敏感渠道元数据
     * @param idempotencyKey      幂等键
     * @param requestHash         请求 Hash
     * @return 固定 Snapshot 的 Session
     */
    public Session createSession(
        OrganizationId organizationId,
        ProjectId projectId,
        DeploymentId deploymentId,
        Map<String, String> participantMetadata,
        Map<String, String> channelMetadata,
        String idempotencyKey,
        Checksum requestHash) {
        DeploymentDescriptor deployment = deploymentResolver.resolve(deploymentId);
        requireDeploymentScope(deployment, organizationId, projectId);
        RuntimeProviderMetadata provider = providerCatalog.current();
        requireProviderCompatibility(deployment, provider);
        SnapshotDescriptor snapshot = snapshotLoader.load(deployment.desiredRevisionId());
        if (!snapshot.revisionId().equals(deployment.desiredRevisionId())
            || snapshot.schemaVersion() != deployment.schemaVersion()
            || !snapshot.runtimeProvider().equals(deployment.runtimeProvider())) {
            throw new RuntimeConflictException(
                "deployment and snapshot runtime metadata are inconsistent");
        }
        return runtimeService.createSession(new CreateSessionCommand(
            organizationId, projectId, deploymentId, snapshot.revisionId(),
            snapshot.snapshotId(), snapshot.contentHash(), participantMetadata,
            channelMetadata, idempotencyKey, requestHash), snapshot);
    }

    /**
     * 在不加载 Snapshot 的接单事务中创建 Turn、Run、Work Item、Event 与 Outbox。
     *
     * @param command Turn 接收命令
     * @return 已排队 Turn
     */
    public Turn acceptTurn(AcceptTurnCommand command) {
        return telemetry.inSpan(
            SpanConvention.RUNTIME, "turn.execute",
            Map.of("operation", "turn.execute", "runtime.provider", command.runtimeProvider()),
            () -> acceptTurnTracked(command));
    }

    /**
     * 在 {@code runtime.turn.execute} Span 内校验 Provider、预留 Quota 并持久接单。
     *
     * @param command Turn 命令
     * @return 已接收 Turn
     */
    private Turn acceptTurnTracked(AcceptTurnCommand command) {
        RuntimeProviderMetadata provider = providerCatalog.current();
        if (!provider.providerId().equals(command.runtimeProvider())
            || !provider.compilerVersion().equals(command.compilerVersion())) {
            throw new RuntimeConflictException("turn runtime provider is not available");
        }
        RuntimeQuotaPort.Reservation reservation = quotaPort.reserveConcurrentRun(
            command.organizationId(), command.projectId(), command.idempotencyKey(),
            command.sessionId().asString(), Duration.ofMinutes(30));
        if (!reservation.allowed()) {
            throw new RuntimeConflictException("hard concurrent run quota exceeded");
        }
        try {
            return runtimeService.acceptTurn(command, reservation.reservationId());
        } catch (RuntimeException exception) {
            reservation.reservationId().ifPresent(quotaPort::release);
            throw exception;
        }
    }

    /**
     * 校验 Deployment 已启用且属于调用方已授权租户。
     *
     * @param deployment     Deployment 描述
     * @param organizationId 组织标识
     * @param projectId      项目标识
     */
    private void requireDeploymentScope(
        DeploymentDescriptor deployment,
        OrganizationId organizationId,
        ProjectId projectId) {
        if (!deployment.enabled()
            || !deployment.organizationId().equals(organizationId)
            || !deployment.projectId().equals(projectId)) {
            throw new RuntimeNotFoundException("deployment is not available");
        }
    }

    /**
     * 校验 Provider 标识、Schema 与所需能力均受当前 Runtime 支持。
     *
     * @param deployment Deployment 描述
     * @param provider   当前 Provider 元数据
     */
    private void requireProviderCompatibility(
        DeploymentDescriptor deployment, RuntimeProviderMetadata provider) {
        if (!deployment.runtimeProvider().equals(provider.providerId())
            || !provider.supportedSchemas().contains(deployment.schemaVersion())
            || !provider.capabilities().containsAll(deployment.requiredCapabilities())) {
            throw new RuntimeConflictException("runtime provider capability is incompatible");
        }
    }
}
