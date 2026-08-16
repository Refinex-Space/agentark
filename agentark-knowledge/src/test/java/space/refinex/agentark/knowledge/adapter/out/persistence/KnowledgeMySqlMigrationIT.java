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

package space.refinex.agentark.knowledge.adapter.out.persistence;

import java.util.Set;
import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;

/**
 * 验证组合类路径中的 Control V1 到 V5 可从空库和 V3 升级，并持久化全部中文表字段注释。
 *
 * @author refinex
 */
class KnowledgeMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 创建 Knowledge MySQL 迁移测试实例。 */
    KnowledgeMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 返回组合类路径中的 Control 最新 Flyway 版本。 */
    @Override
    protected String schemaName() {
        return "agentark_control";
    }

    /** 返回 Knowledge 迁移测试使用的固定配置。 */
    @Override
    protected String forbiddenSchemaName() {
        return "agentark_runtime";
    }

    /** 返回 Knowledge 迁移测试使用的固定配置。 */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/control";
    }

    /** 返回 Knowledge 迁移测试使用的固定配置。 */
    @Override
    protected String expectedVersion() {
        return "5";
    }

    /** 返回 Knowledge 迁移测试使用的固定配置。 */
    @Override
    protected String previousVersion() {
        return "3";
    }

    /**
     * 声明 Phase 07 到 Phase 10 组合类路径允许存在的全部 Control 业务表。
     *
     * @return IAM、Catalog、Secret、Knowledge 与 Release 表集合
     */
    @Override
    protected Set<String> expectedBusinessTables() {
        return Set.of(
            "organization", "project", "environment", "user_identity", "service_account",
            "membership", "permission", "role", "role_permission", "role_binding", "api_key",
            "api_key_scope", "agent", "prompt", "prompt_version", "model_provider",
            "model_profile", "mcp_server", "mcp_server_version", "mcp_tool_descriptor", "skill",
            "skill_version", "memory_profile", "memory_profile_version", "workspace_profile",
            "workspace_profile_version", "sandbox_profile", "sandbox_profile_version",
            "permission_policy", "permission_policy_version", "secret_metadata", "secret_binding",
            "knowledge_base", "data_source", "document", "document_acl", "document_revision",
            "parser_profile", "chunk_profile", "embedding_profile", "retrieval_profile",
            "knowledge_revision", "knowledge_revision_document", "knowledge_ingestion_request",
            "agent_draft", "agent_draft_component", "validation_report", "agent_revision",
            "agent_revision_snapshot", "publish_operation", "deployment", "deployment_revision",
            "control_outbox");
    }
}
