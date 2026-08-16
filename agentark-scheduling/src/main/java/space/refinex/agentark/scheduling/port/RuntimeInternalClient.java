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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.kernel.ref.Checksum;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 定义 Scheduler 创建 Agent Turn 时唯一允许使用的版本化 Runtime Internal API Port。
 *
 * @author refinex
 */
@FunctionalInterface
public interface RuntimeInternalClient {

    /**
     * 幂等创建 Turn；Runtime 事务提交后返回稳定 Run 标识。
     *
     * @param command Runtime Turn 命令
     * @return Run UUIDv7 字符串
     */
    CompletionStage<String> createTurn(RuntimeTurnCommand command);

    /**
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param sessionId      已固定 Snapshot 的 Session
     * @param inputJson      不含 Secret 的中立输入 JSON
     * @param inputHash      输入 SHA-256
     * @param priority       Runtime 优先级
     * @param idempotencyKey 稳定幂等键
     * @author refinex
     */
    record RuntimeTurnCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        String inputJson,
        Checksum inputHash,
        int priority,
        String idempotencyKey) {

        /**
         * 校验 Runtime Internal Command 租户、输入和幂等键。
         */
        public RuntimeTurnCommand {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(inputHash, "inputHash must not be null");
            if (inputJson == null || inputJson.isBlank()
                || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}")) {
                throw new IllegalArgumentException("runtime turn command is invalid");
            }
        }
    }
}
