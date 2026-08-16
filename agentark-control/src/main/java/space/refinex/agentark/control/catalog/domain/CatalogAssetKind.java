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

package space.refinex.agentark.control.catalog.domain;

import space.refinex.agentark.kernel.id.*;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 定义公开资产分类、受信表映射和对应强类型标识工厂，禁止调用方传入任意表名。
 *
 * @author refinex
 */
public enum CatalogAssetKind {

    /**
     * Agent 稳定身份；Draft、Revision 和 Snapshot 由 Phase 10 Release 边界拥有。
     */
    AGENT("agent", null, AgentId::generate, AgentId::parse, null, null),

    /**
     * Prompt 稳定身份与 PromptVersion。
     */
    PROMPT("prompt", "prompt_version", PromptId::generate, PromptId::parse,
        PromptVersionId::generate, PromptVersionId::parse),

    /**
     * Model Provider 稳定描述符与 ModelProfile。
     */
    MODEL_PROVIDER("model_provider", "model_profile", ModelProviderId::generate,
        ModelProviderId::parse, ModelProfileId::generate, ModelProfileId::parse),

    /**
     * MCP Server 稳定身份与连接版本。
     */
    MCP_SERVER("mcp_server", "mcp_server_version", McpServerId::generate, McpServerId::parse,
        McpServerVersionId::generate, McpServerVersionId::parse),

    /**
     * Skill 稳定身份与 Artifact 版本。
     */
    SKILL("skill", "skill_version", SkillId::generate, SkillId::parse,
        SkillVersionId::generate, SkillVersionId::parse),

    /**
     * Memory Profile 稳定身份与策略版本。
     */
    MEMORY_PROFILE("memory_profile", "memory_profile_version", MemoryProfileId::generate,
        MemoryProfileId::parse, MemoryProfileVersionId::generate, MemoryProfileVersionId::parse),

    /**
     * Workspace Profile 稳定身份与隔离版本。
     */
    WORKSPACE_PROFILE("workspace_profile", "workspace_profile_version",
        WorkspaceProfileId::generate, WorkspaceProfileId::parse,
        WorkspaceProfileVersionId::generate, WorkspaceProfileVersionId::parse),

    /**
     * Sandbox Profile 稳定身份与运行策略版本。
     */
    SANDBOX_PROFILE("sandbox_profile", "sandbox_profile_version", SandboxProfileId::generate,
        SandboxProfileId::parse, SandboxProfileVersionId::generate, SandboxProfileVersionId::parse),

    /**
     * Permission Policy 稳定身份与组合规则版本。
     */
    PERMISSION_POLICY("permission_policy", "permission_policy_version",
        PermissionPolicyId::generate, PermissionPolicyId::parse,
        PermissionPolicyVersionId::generate, PermissionPolicyVersionId::parse);

    /**
     * 受信稳定身份表名，仅持久化适配器使用。
     */
    private final String tableName;

    /**
     * 受信版本表名；Agent 没有版本表。
     */
    private final String versionTableName;

    /**
     * 稳定身份生成器。
     */
    private final Supplier<? extends StrongId> idGenerator;

    /**
     * 稳定身份解析器。
     */
    private final Function<String, ? extends StrongId> idParser;

    /**
     * 版本标识生成器；Agent 为空。
     */
    private final Supplier<? extends StrongId> versionIdGenerator;

    /**
     * 版本标识解析器；Agent 为空。
     */
    private final Function<String, ? extends StrongId> versionIdParser;

    /**
     * 创建受控资产分类。
     *
     * @param tableName          稳定身份表名
     * @param versionTableName   可选版本表名
     * @param idGenerator        稳定身份生成器
     * @param idParser           稳定身份解析器
     * @param versionIdGenerator 可选版本标识生成器
     * @param versionIdParser    可选版本标识解析器
     */
    CatalogAssetKind(
        String tableName,
        String versionTableName,
        Supplier<? extends StrongId> idGenerator,
        Function<String, ? extends StrongId> idParser,
        Supplier<? extends StrongId> versionIdGenerator,
        Function<String, ? extends StrongId> versionIdParser) {
        this.tableName = tableName;
        this.versionTableName = versionTableName;
        this.idGenerator = idGenerator;
        this.idParser = idParser;
        this.versionIdGenerator = versionIdGenerator;
        this.versionIdParser = versionIdParser;
    }

    /**
     * @return 受信稳定身份表名
     */
    public String tableName() {
        return tableName;
    }

    /**
     * @return 受信版本表名
     * @throws IllegalArgumentException 当前分类不支持版本时抛出
     */
    public String versionTableName() {
        if (versionTableName == null) {
            throw new IllegalArgumentException("asset kind does not support versions");
        }
        return versionTableName;
    }

    /**
     * @return 新稳定身份强类型 UUIDv7
     */
    public StrongId generateId() {
        return idGenerator.get();
    }

    /**
     * @param value 规范 UUIDv7 字符串
     * @return 对应分类的稳定强类型标识
     */
    public StrongId parseId(String value) {
        return idParser.apply(value);
    }

    /**
     * @return 新版本强类型 UUIDv7
     * @throws IllegalArgumentException 当前分类不支持版本时抛出
     */
    public StrongId generateVersionId() {
        if (versionIdGenerator == null) {
            throw new IllegalArgumentException("asset kind does not support versions");
        }
        return versionIdGenerator.get();
    }

    /**
     * @param value 规范 UUIDv7 字符串
     * @return 对应分类的版本强类型标识
     * @throws IllegalArgumentException 当前分类不支持版本时抛出
     */
    public StrongId parseVersionId(String value) {
        if (versionIdParser == null) {
            throw new IllegalArgumentException("asset kind does not support versions");
        }
        return versionIdParser.apply(value);
    }

    /**
     * 从 Public API 小写连字符值解析分类。
     *
     * @param value 分类路径值
     * @return 对应资产分类
     * @throws IllegalArgumentException 值不属于固定分类时抛出
     */
    public static CatalogAssetKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("asset kind must not be blank");
        }
        return valueOf(value.replace('-', '_').toUpperCase(Locale.ROOT));
    }

    /**
     * @return Public API 使用的小写连字符分类值
     */
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
