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

package space.refinex.agentark.runtime.provider.agentscope.compiler;

import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.runtime.provider.agentscope.knowledge.KnowledgeBinding;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpBinding;
import space.refinex.agentark.runtime.provider.agentscope.memory.MemoryBinding;
import space.refinex.agentark.runtime.provider.agentscope.model.ModelBinding;
import space.refinex.agentark.runtime.provider.agentscope.permission.PermissionBinding;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptBinding;
import space.refinex.agentark.runtime.provider.agentscope.sandbox.SandboxBinding;
import space.refinex.agentark.runtime.provider.agentscope.skill.SkillBinding;
import space.refinex.agentark.runtime.provider.agentscope.workspace.WorkspaceBinding;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 表示可丢失重建、不含 Secret 明文与 Session 可变状态的 Snapshot 编译计划。
 *
 * @param cacheKey             Provider、Schema、Snapshot Hash 与 Compiler 组成的缓存键
 * @param organizationId       Snapshot 所属组织
 * @param projectId            Snapshot 所属项目
 * @param agentId              Agent 稳定标识
 * @param agentName            Agent 展示名称
 * @param requiredCapabilities Snapshot 要求的能力集合
 * @param model                模型构建输入
 * @param prompts              有序 Prompt 列表
 * @param mcpServers           MCP Server 列表
 * @param skills               Skill 制品列表
 * @param knowledge            Knowledge 检索列表
 * @param memory               内存配置档案
 * @param workspace            工作区配置档案
 * @param sandbox              沙箱配置档案
 * @param permission           权限策略
 * @param turnTimeout          单次执行超时
 * @param maxToolCalls         最大 Tool 调用数
 * @param maxSubAgents         最大子 Agent 数
 * @author refinex
 */
public record AgentScopeCompilationPlan(
    SnapshotCompilationCache.CacheKey cacheKey,
    OrganizationId organizationId,
    ProjectId projectId,
    AgentId agentId,
    String agentName,
    Set<String> requiredCapabilities,
    ModelBinding model,
    List<PromptBinding> prompts,
    List<McpBinding> mcpServers,
    List<SkillBinding> skills,
    List<KnowledgeBinding> knowledge,
    MemoryBinding memory,
    WorkspaceBinding workspace,
    SandboxBinding sandbox,
    PermissionBinding permission,
    Duration turnTimeout,
    int maxToolCalls,
    int maxSubAgents) {

    /**
     * 校验编译计划完整并防御性复制集合。
     */
    public AgentScopeCompilationPlan {
        Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank");
        }
        requiredCapabilities = Set.copyOf(Objects.requireNonNull(
            requiredCapabilities, "requiredCapabilities must not be null"));
        Objects.requireNonNull(model, "model must not be null");
        prompts = List.copyOf(Objects.requireNonNull(prompts, "prompts must not be null"));
        mcpServers = List.copyOf(Objects.requireNonNull(
            mcpServers, "mcpServers must not be null"));
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
        knowledge = List.copyOf(Objects.requireNonNull(knowledge, "knowledge must not be null"));
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(workspace, "workspace must not be null");
        Objects.requireNonNull(sandbox, "sandbox must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(turnTimeout, "turnTimeout must not be null");
        if (turnTimeout.isNegative() || turnTimeout.isZero() || maxToolCalls < 0
            || maxSubAgents < 0) {
            throw new IllegalArgumentException("runtime limits are invalid");
        }
    }
}
