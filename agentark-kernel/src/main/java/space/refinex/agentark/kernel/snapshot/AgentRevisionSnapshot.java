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

package space.refinex.agentark.kernel.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.kernel.id.SnapshotId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.SchemaVersion;

/**
 * 表示 AgentArk Runtime Provider 消费的完整不可变 Agent 修订版本依赖闭包。
 *
 * <p>Snapshot 在发布时解析全部资产版本，Runtime 执行期间不得回读可编辑的 Control Catalog。
 *
 * @param schemaVersion   Snapshot JSON Schema 版本
 * @param organizationId  Snapshot 所属组织标识
 * @param projectId       Snapshot 所属项目标识
 * @param snapshotId      Snapshot 唯一标识
 * @param agentId         Agent 资源标识
 * @param revisionId      Agent 修订版本标识
 * @param revisionNumber  Agent 内从 1 开始递增的修订序号
 * @param createdAt       Snapshot 创建时刻，使用 UTC 时间线上的 {@link Instant}
 * @param contentHash     除顶层 contentHash 外完整规范 JSON 的 SHA-256
 * @param runtimeProvider 发布时固定的 Runtime Provider 标识
 * @param agent           Agent 执行入口与能力要求
 * @param model           模型及引用式凭据规范
 * @param prompts         已解析并校验正文 Hash 的 Prompt 版本列表
 * @param mcpServers      MCP 服务版本与 Tool 白名单列表
 * @param skills          Skill 版本与制品引用列表
 * @param knowledge       READY 知识修订版本与检索参数列表
 * @param memory          记忆配置版本
 * @param workspace       工作区配置版本
 * @param sandbox         沙箱配置版本
 * @param permissions     默认决策与权限覆盖规则
 * @param limits          Runtime 硬限制
 * @author refinex
 */
public record AgentRevisionSnapshot(
    SchemaVersion schemaVersion,
    OrganizationId organizationId,
    ProjectId projectId,
    SnapshotId snapshotId,
    AgentId agentId,
    RevisionId revisionId,
    long revisionNumber,
    Instant createdAt,
    Checksum contentHash,
    RuntimeProviderId runtimeProvider,
    AgentSpec agent,
    ModelSpec model,
    List<PromptSpec> prompts,
    List<McpSpec> mcpServers,
    List<SkillSpec> skills,
    List<KnowledgeSpec> knowledge,
    MemorySpec memory,
    WorkspaceSpec workspace,
    SandboxSpec sandbox,
    PermissionSpec permissions,
    RuntimeLimits limits) {

    /**
     * 校验并创建不可变 Snapshot，同时防御性复制所有资产列表。
     *
     * @param schemaVersion   Snapshot Schema 版本
     * @param organizationId  组织标识
     * @param projectId       项目标识
     * @param snapshotId      Snapshot 标识
     * @param agentId         Agent 标识
     * @param revisionId      修订版本标识
     * @param revisionNumber  修订序号
     * @param createdAt       创建时刻
     * @param contentHash     Snapshot 内容校验和
     * @param runtimeProvider Runtime Provider 标识
     * @param agent           Agent 规范
     * @param model           模型规范
     * @param prompts         Prompt 规范列表
     * @param mcpServers      MCP 规范列表
     * @param skills          Skill 规范列表
     * @param knowledge       知识规范列表
     * @param memory          记忆规范
     * @param workspace       工作区规范
     * @param sandbox         沙箱规范
     * @param permissions     权限规范
     * @param limits          Runtime 限制
     * @throws NullPointerException     当任一必填对象或列表为 {@code null} 时抛出
     * @throws IllegalArgumentException 当修订序号小于 1 或列表包含空元素时抛出
     */
    public AgentRevisionSnapshot {
        Objects.requireNonNull(schemaVersion, "Snapshot schemaVersion must not be null");
        Objects.requireNonNull(organizationId, "Snapshot organizationId must not be null");
        Objects.requireNonNull(projectId, "Snapshot projectId must not be null");
        Objects.requireNonNull(snapshotId, "Snapshot snapshotId must not be null");
        Objects.requireNonNull(agentId, "Snapshot agentId must not be null");
        Objects.requireNonNull(revisionId, "Snapshot revisionId must not be null");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("Snapshot revisionNumber must be positive");
        }
        Objects.requireNonNull(createdAt, "Snapshot createdAt must not be null");
        Objects.requireNonNull(contentHash, "Snapshot contentHash must not be null");
        Objects.requireNonNull(runtimeProvider, "Snapshot runtimeProvider must not be null");
        Objects.requireNonNull(agent, "Snapshot agent must not be null");
        Objects.requireNonNull(model, "Snapshot model must not be null");
        prompts = SnapshotRequirements.immutableList(prompts, "Snapshot prompts");
        mcpServers = SnapshotRequirements.immutableList(mcpServers, "Snapshot mcpServers");
        skills = SnapshotRequirements.immutableList(skills, "Snapshot skills");
        knowledge = SnapshotRequirements.immutableList(knowledge, "Snapshot knowledge");
        Objects.requireNonNull(memory, "Snapshot memory must not be null");
        Objects.requireNonNull(workspace, "Snapshot workspace must not be null");
        Objects.requireNonNull(sandbox, "Snapshot sandbox must not be null");
        Objects.requireNonNull(permissions, "Snapshot permissions must not be null");
        Objects.requireNonNull(limits, "Snapshot limits must not be null");
    }
}
