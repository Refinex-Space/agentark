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

import io.agentscope.harness.agent.HarnessAgent;
import space.refinex.agentark.runtime.provider.agentscope.knowledge.KnowledgeBinding;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpBinding;
import space.refinex.agentark.runtime.provider.agentscope.memory.MemoryBinding;
import space.refinex.agentark.runtime.provider.agentscope.permission.PermissionBinding;
import space.refinex.agentark.runtime.provider.agentscope.sandbox.SandboxBinding;
import space.refinex.agentark.runtime.provider.agentscope.secret.SecretResolver;
import space.refinex.agentark.runtime.provider.agentscope.skill.SkillBinding;
import space.refinex.agentark.runtime.provider.agentscope.workspace.WorkspaceBinding;

import java.util.List;

/**
 * 将各运行能力的供应商中立绑定贡献给 AgentScope Harness Builder。
 *
 * <p>生产实现必须在此边界内使用 AgentScope Extension，不得让其类型进入 Runtime Domain。
 *
 * @author refinex
 */
public interface AgentScopeRuntimeComponentFactory {

    /**
     * 配置 MCP Client、Transport 和 Tool 白名单。
     *
     * @param builder        Harness Builder
     * @param bindings       MCP 绑定
     * @param secretResolver 按需 Secret 解析端口
     * @param resources      RuntimeHandle 关闭时需释放的资源
     */
    void configureMcp(
        HarnessAgent.Builder builder,
        List<McpBinding> bindings,
        SecretResolver secretResolver,
        List<AutoCloseable> resources);

    /**
     * 校验 Skill 制品完整性并配置 Skill Repository。
     *
     * @param builder   Harness Builder
     * @param bindings  Skill 绑定
     * @param resources RuntimeHandle 资源
     */
    void configureSkills(
        HarnessAgent.Builder builder, List<SkillBinding> bindings, List<AutoCloseable> resources);

    /**
     * 配置 Knowledge Retriever，不得回读 Control Catalog。
     *
     * @param builder   Harness Builder
     * @param bindings  Knowledge 绑定
     * @param resources RuntimeHandle 资源
     */
    void configureKnowledge(
        HarnessAgent.Builder builder,
        List<KnowledgeBinding> bindings,
        List<AutoCloseable> resources);

    /**
     * 配置 Memory Profile。
     *
     * @param builder Harness Builder
     * @param binding Memory 绑定
     */
    void configureMemory(HarnessAgent.Builder builder, MemoryBinding binding);

    /**
     * 配置 Workspace Profile。
     *
     * @param builder   Harness Builder
     * @param binding   Workspace 绑定
     * @param resources RuntimeHandle 资源
     */
    void configureWorkspace(
        HarnessAgent.Builder builder, WorkspaceBinding binding, List<AutoCloseable> resources);

    /**
     * 配置 Sandbox Profile。
     *
     * @param builder   Harness Builder
     * @param binding   Sandbox 绑定
     * @param resources RuntimeHandle 资源
     */
    void configureSandbox(
        HarnessAgent.Builder builder, SandboxBinding binding, List<AutoCloseable> resources);

    /**
     * 将 Permission Policy 映射为 AgentScope Permission Context。
     *
     * @param builder Harness Builder
     * @param binding Permission 绑定
     */
    void configurePermission(HarnessAgent.Builder builder, PermissionBinding binding);
}
