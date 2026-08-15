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

package space.refinex.agentark.kernel.id;

import java.time.Instant;
import java.util.UUID;

/**
 * 所有不可变 UUIDv7 领域标识的统一只读契约，用于提供规范字符串和生成时间能力。
 *
 * @author refinex
 */
public sealed interface StrongId
    permits OrganizationId,
    ProjectId,
    EnvironmentId,
    AgentId,
    RevisionId,
    SnapshotId,
    DeploymentId,
    KnowledgeRevisionId,
    SessionId,
    TurnId,
    RunId,
    ApprovalId,
    JobId,
    EventId,
    PromptVersionId,
    McpServerVersionId,
    SkillVersionId,
    MemoryProfileVersionId,
    WorkspaceProfileVersionId,
    SandboxProfileVersionId {

    /**
     * 返回领域标识持有的 UUIDv7 原始值。
     *
     * @return 已通过版本和 Variant 校验的 UUIDv7
     */
    UUID value();

    /**
     * 返回小写连字符形式的规范 UUIDv7 字符串。
     *
     * @return 规范 UUIDv7 字符串
     */
    default String asString() {
        return value().toString();
    }

    /**
     * 从 UUIDv7 的 48 位 Unix 毫秒字段还原生成时间。
     *
     * @return UTC 时间线上的生成时刻
     */
    default Instant generatedAt() {
        return UuidV7.timestamp(value());
    }
}
