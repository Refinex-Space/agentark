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

package space.refinex.agentark.control.release.domain;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.snapshot.PromptRole;

import java.util.List;
import java.util.Objects;

/**
 * 表示 Agent Draft 对全部版本化资产的强类型引用与 Provider 中立运行约束。
 *
 * <p>Draft 可编辑，但不能被 Runtime 消费；发布器必须把所有引用解析为完整 Snapshot。
 *
 * @param runtimeProvider      目标 Runtime Provider 稳定名称
 * @param requiredCapabilities Runtime 必需能力
 * @param model                唯一模型绑定
 * @param prompts              Prompt 版本绑定
 * @param mcpServers           MCP Server 版本与 Tool 白名单
 * @param skills               Skill 版本绑定
 * @param knowledge            Knowledge READY Revision 绑定
 * @param profiles             Memory、Workspace 与 Sandbox 版本绑定
 * @param permissionPolicy     Permission Policy 版本绑定
 * @param limits               Runtime 硬限制
 * @author refinex
 */
public record AgentDraftSpec(
    String runtimeProvider,
    List<String> requiredCapabilities,
    ModelBinding model,
    List<PromptBinding> prompts,
    List<McpBinding> mcpServers,
    List<SkillBinding> skills,
    List<KnowledgeBinding> knowledge,
    ProfileBindings profiles,
    PermissionBinding permissionPolicy,
    LimitSpec limits) {

    /**
     * 校验 Draft 必填依赖并防御性复制全部列表。
     */
    public AgentDraftSpec {
        if (runtimeProvider == null
            || !runtimeProvider.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")) {
            throw new IllegalArgumentException("runtimeProvider must be a stable lowercase name");
        }
        requiredCapabilities = immutable(requiredCapabilities, "requiredCapabilities");
        if (requiredCapabilities.stream().distinct().count() != requiredCapabilities.size()) {
            throw new IllegalArgumentException("requiredCapabilities must not contain duplicates");
        }
        Objects.requireNonNull(model, "model must not be null");
        prompts = immutable(prompts, "prompts");
        mcpServers = immutable(mcpServers, "mcpServers");
        skills = immutable(skills, "skills");
        knowledge = immutable(knowledge, "knowledge");
        Objects.requireNonNull(profiles, "profiles must not be null");
        Objects.requireNonNull(permissionPolicy, "permissionPolicy must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
    }

    /**
     * 防御性复制列表并拒绝空元素。
     *
     * @param values 输入列表
     * @param name   字段名
     * @param <T>    元素类型
     * @return 不可变列表
     */
    private static <T> List<T> immutable(List<T> values, String name) {
        List<T> copied = List.copyOf(Objects.requireNonNull(values, name + " must not be null"));
        if (copied.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " must not contain null values");
        }
        return copied;
    }

    /**
     * @param providerId 模型 Provider 稳定身份
     * @param profileId  模型 Profile 稳定身份
     * @author refinex
     */
    public record ModelBinding(ModelProviderId providerId, ModelProfileId profileId) {
        /**
         * 校验模型引用完整。
         */
        public ModelBinding {
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(profileId, "profileId must not be null");
        }
    }

    /**
     * @param promptId  Prompt 稳定身份
     * @param versionId Prompt 不可变版本
     * @param role      Snapshot 消息角色
     * @author refinex
     */
    public record PromptBinding(PromptId promptId, PromptVersionId versionId, PromptRole role) {
        /**
         * 校验 Prompt 引用完整。
         */
        public PromptBinding {
            Objects.requireNonNull(promptId, "promptId must not be null");
            Objects.requireNonNull(versionId, "versionId must not be null");
            Objects.requireNonNull(role, "role must not be null");
        }
    }

    /**
     * @param serverId     MCP Server 稳定身份
     * @param versionId    MCP Server 不可变版本
     * @param allowedTools 显式 Tool 白名单
     * @author refinex
     */
    public record McpBinding(
        McpServerId serverId, McpServerVersionId versionId, List<String> allowedTools) {
        /**
         * 校验 MCP 引用并冻结 Tool 白名单。
         */
        public McpBinding {
            Objects.requireNonNull(serverId, "serverId must not be null");
            Objects.requireNonNull(versionId, "versionId must not be null");
            allowedTools = immutable(allowedTools, "allowedTools");
            if (allowedTools.isEmpty()
                || allowedTools.stream().anyMatch(value -> value == null || value.isBlank())
                || allowedTools.stream().distinct().count() != allowedTools.size()) {
                throw new IllegalArgumentException("allowedTools must be non-empty and unique");
            }
        }
    }

    /**
     * @param skillId   Skill 稳定身份
     * @param versionId Skill 不可变版本
     * @author refinex
     */
    public record SkillBinding(SkillId skillId, SkillVersionId versionId) {
        /**
         * 校验 Skill 引用完整。
         */
        public SkillBinding {
            Objects.requireNonNull(skillId, "skillId must not be null");
            Objects.requireNonNull(versionId, "versionId must not be null");
        }
    }

    /**
     * @param knowledgeBaseId Knowledge Base 稳定身份
     * @param revisionId      必须处于 READY 的 Knowledge Revision
     * @author refinex
     */
    public record KnowledgeBinding(
        KnowledgeBaseId knowledgeBaseId, KnowledgeRevisionId revisionId) {
        /**
         * 校验 Knowledge 引用完整。
         */
        public KnowledgeBinding {
            Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
            Objects.requireNonNull(revisionId, "revisionId must not be null");
        }
    }

    /**
     * @param memoryId           Memory Profile 稳定身份
     * @param memoryVersionId    Memory Profile 版本
     * @param workspaceId        Workspace Profile 稳定身份
     * @param workspaceVersionId Workspace Profile 版本
     * @param sandboxId          Sandbox Profile 稳定身份
     * @param sandboxVersionId   Sandbox Profile 版本
     * @author refinex
     */
    public record ProfileBindings(
        MemoryProfileId memoryId,
        MemoryProfileVersionId memoryVersionId,
        WorkspaceProfileId workspaceId,
        WorkspaceProfileVersionId workspaceVersionId,
        SandboxProfileId sandboxId,
        SandboxProfileVersionId sandboxVersionId) {
        /**
         * 校验三类 Profile 的稳定身份与版本完整。
         */
        public ProfileBindings {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            Objects.requireNonNull(memoryVersionId, "memoryVersionId must not be null");
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(workspaceVersionId, "workspaceVersionId must not be null");
            Objects.requireNonNull(sandboxId, "sandboxId must not be null");
            Objects.requireNonNull(sandboxVersionId, "sandboxVersionId must not be null");
        }
    }

    /**
     * @param policyId  Permission Policy 稳定身份
     * @param versionId Permission Policy 不可变版本
     * @author refinex
     */
    public record PermissionBinding(
        PermissionPolicyId policyId, PermissionPolicyVersionId versionId) {
        /**
         * 校验 Permission Policy 引用完整。
         */
        public PermissionBinding {
            Objects.requireNonNull(policyId, "policyId must not be null");
            Objects.requireNonNull(versionId, "versionId must not be null");
        }
    }

    /**
     * @param turnTimeoutSeconds 单 Turn 超时秒数
     * @param maxToolCalls       单 Turn 最大 Tool 调用数
     * @param maxSubAgents       单 Turn 最大 Sub-Agent 数
     * @author refinex
     */
    public record LimitSpec(long turnTimeoutSeconds, int maxToolCalls, int maxSubAgents) {
        /**
         * 校验运行限制位于 Kernel 契约允许范围。
         */
        public LimitSpec {
            if (turnTimeoutSeconds < 1 || turnTimeoutSeconds > 86_400
                || maxToolCalls < 0 || maxToolCalls > 100_000
                || maxSubAgents < 0 || maxSubAgents > 1_000) {
                throw new IllegalArgumentException("runtime limits are outside supported bounds");
            }
        }
    }
}
