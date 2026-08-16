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

import space.refinex.agentark.kernel.id.McpServerVersionId;
import space.refinex.agentark.kernel.id.McpToolDescriptorId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示从 MCP Server Version 派生的工具描述、安全风险、读写与幂等语义快照。
 *
 * @param id                     Descriptor 标识
 * @param organizationId         所属组织
 * @param projectId              所属项目
 * @param serverVersionId        所属 MCP Server Version
 * @param toolName               工具名称
 * @param description            用途说明
 * @param argumentSchemaJson     参数 JSON Schema
 * @param accessMode             读写模式
 * @param riskLevel              风险等级
 * @param idempotency            幂等语义
 * @param permissionMetadataJson Allowlist、权限和审批元数据
 * @param contentHash            Descriptor 内容 Hash
 * @param createdAt              创建时刻
 * @author refinex
 */
public record McpToolDescriptorSnapshot(
    McpToolDescriptorId id,
    OrganizationId organizationId,
    ProjectId projectId,
    McpServerVersionId serverVersionId,
    String toolName,
    String description,
    String argumentSchemaJson,
    String accessMode,
    String riskLevel,
    String idempotency,
    String permissionMetadataJson,
    Checksum contentHash,
    Instant createdAt) {

    /**
     * 校验工具元数据完整性和可穷举安全字段。
     *
     * @param id                     Descriptor 标识
     * @param organizationId         所属组织
     * @param projectId              所属项目
     * @param serverVersionId        所属版本
     * @param toolName               工具名称
     * @param description            用途说明
     * @param argumentSchemaJson     参数 Schema
     * @param accessMode             访问模式
     * @param riskLevel              风险等级
     * @param idempotency            幂等语义
     * @param permissionMetadataJson 权限元数据
     * @param contentHash            内容 Hash
     * @param createdAt              创建时刻
     */
    public McpToolDescriptorSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(serverVersionId, "serverVersionId must not be null");
        toolName = CatalogFieldPolicy.text(toolName, "toolName", 255);
        description = CatalogFieldPolicy.optionalText(description, "description", 1024);
        argumentSchemaJson = CatalogFieldPolicy.json(argumentSchemaJson, "argumentSchemaJson");
        if (!java.util.Set.of("READ", "WRITE", "READ_WRITE").contains(accessMode)) {
            throw new IllegalArgumentException("accessMode is not supported");
        }
        if (!java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(riskLevel)) {
            throw new IllegalArgumentException("riskLevel is not supported");
        }
        if (!java.util.Set.of("IDEMPOTENT", "NON_IDEMPOTENT", "UNKNOWN").contains(idempotency)) {
            throw new IllegalArgumentException("idempotency is not supported");
        }
        permissionMetadataJson = CatalogFieldPolicy.json(
            permissionMetadataJson, "permissionMetadataJson");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        createdAt = CatalogFieldPolicy.instant(createdAt, "createdAt");
    }
}

