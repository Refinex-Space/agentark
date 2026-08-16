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

package space.refinex.agentark.control.release.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * 定义 Release MyBatis Mapper 专用数据库行，禁止向 Domain 和 Public API 暴露。
 *
 * @author refinex
 */
final class ReleasePersistenceRows {

    /**
     * 禁止实例化行模型容器。
     */
    private ReleasePersistenceRows() {
    }

    /**
     * @param agentId        Agent 的 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param specJson       Draft 的 JSON 内容
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    record DraftRow(
        UUID agentId, UUID organizationId, UUID projectId, String specJson,
        long version, Instant createdAt, Instant updatedAt) {
    }

    /**
     * @param agentId        Agent 的 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param componentType  组件类型
     * @param componentOrder 组件顺序
     * @param ownerId        资产稳定 UUID
     * @param versionId      资产版本 UUID
     * @param bindingJson    绑定 JSON
     * @param createdAt      创建时刻
     * @author refinex
     */
    record ComponentRow(
        UUID agentId, UUID organizationId, UUID projectId, String componentType,
        int componentOrder, UUID ownerId, UUID versionId, String bindingJson, Instant createdAt) {
    }

    /**
     * @param id                       Revision 的 UUID
     * @param organizationId           组织 UUID
     * @param projectId                项目 UUID
     * @param agentId                  Agent 的 UUID
     * @param snapshotId               Snapshot 的 UUID
     * @param revisionNumber           Revision 序号
     * @param schemaVersion            Schema 版本
     * @param runtimeProvider          Runtime Provider 标识
     * @param contentHash              SHA-256 字节
     * @param requiredCapabilitiesJson 能力 JSON
     * @param createdAt                创建时刻
     * @param snapshotJson             Snapshot 的 JSON 内容
     * @author refinex
     */
    record SnapshotRow(
        UUID id, UUID organizationId, UUID projectId, UUID agentId, UUID snapshotId,
        long revisionNumber, int schemaVersion, String runtimeProvider, byte[] contentHash,
        String requiredCapabilitiesJson, Instant createdAt, String snapshotJson) {
        /**
         * 防御性复制内容 Hash。
         */
        SnapshotRow {
            contentHash = contentHash.clone();
        }

        /**
         * @return SHA-256 防御性副本
         */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    /**
     * @param id             操作 UUID
     * @param projectId      项目 UUID
     * @param agentId        Agent 的 UUID
     * @param idempotencyKey 幂等键
     * @param draftVersion   Draft 版本
     * @param status         终态
     * @param revisionId     Revision 的 UUID
     * @param createdAt      创建时刻
     * @author refinex
     */
    record OperationRow(
        UUID id, UUID projectId, UUID agentId, String idempotencyKey, long draftVersion,
        String status, UUID revisionId, Instant createdAt) {
    }

    /**
     * @param id                Deployment 的 UUID
     * @param organizationId    组织 UUID
     * @param projectId         项目 UUID
     * @param environmentId     环境 UUID
     * @param agentId           Agent 的 UUID
     * @param desiredRevisionId 期望 Revision UUID
     * @param desiredStatus     期望状态
     * @param trafficPolicyType 流量策略
     * @param canaryPercent     Canary 百分比
     * @param version           乐观锁版本
     * @param createdAt         创建时刻
     * @param updatedAt         更新时间
     * @author refinex
     */
    record DeploymentRow(
        UUID id, UUID organizationId, UUID projectId, UUID environmentId, UUID agentId,
        UUID desiredRevisionId, String desiredStatus, String trafficPolicyType,
        int canaryPercent, long version, Instant createdAt, Instant updatedAt) {
    }
}
