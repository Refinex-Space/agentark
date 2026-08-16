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

package space.refinex.agentark.control.adapter.out.persistence;

import java.util.Set;
import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;

/**
 * 验证 Control 只迁移 agentark_control，并拒绝读取 Runtime Schema。
 *
 * @author refinex
 */
class ControlMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 创建 Control MySQL 迁移测试实例。 */
    ControlMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 返回 Control 独占 Schema 名称。
     *
     * @return Control Schema 名称
     */
    @Override
    protected String schemaName() {
        return "agentark_control";
    }

    /**
     * 返回用于证明越权失败的 Runtime Schema 名称。
     *
     * @return Runtime Schema 名称
     */
    @Override
    protected String forbiddenSchemaName() {
        return "agentark_runtime";
    }

    /**
     * 返回 Control 独占 Flyway Location。
     *
     * @return Control Migration classpath Location
     */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/control";
    }

    /**
     * 声明 Control 当前最新迁移为 Phase 08 的 V3。
     *
     * @return Flyway 版本 3
     */
    @Override
    protected String expectedVersion() {
        return "3";
    }

    /**
     * 声明 Phase 07 的 V2 是当前升级测试起点。
     *
     * @return Flyway 版本 2
     */
    @Override
    protected String previousVersion() {
        return "2";
    }

    /**
     * 声明 Phase 07 IAM 与 Phase 08 资产目录允许创建的业务表。
     *
     * @return 十二张 IAM 表和二十张资产、Secret 表
     */
    @Override
    protected Set<String> expectedBusinessTables() {
        return Set.of(
            "organization",
            "project",
            "environment",
            "user_identity",
            "service_account",
            "membership",
            "permission",
            "role",
            "role_permission",
            "role_binding",
            "api_key",
            "api_key_scope",
            "agent",
            "prompt",
            "prompt_version",
            "model_provider",
            "model_profile",
            "mcp_server",
            "mcp_server_version",
            "mcp_tool_descriptor",
            "skill",
            "skill_version",
            "memory_profile",
            "memory_profile_version",
            "workspace_profile",
            "workspace_profile_version",
            "sandbox_profile",
            "sandbox_profile_version",
            "permission_policy",
            "permission_policy_version",
            "secret_metadata",
            "secret_binding");
    }
}
