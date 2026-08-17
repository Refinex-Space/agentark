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

package space.refinex.agentark.control.release.application.port;

import space.refinex.agentark.kernel.id.*;

/**
 * 定义 Deployment 指针前移前的版本化 Release Gate，不让 Evaluation 领域反向进入 Release。
 *
 * @author refinex
 */
@FunctionalInterface
public interface ReleaseGatePolicy {

    /**
     * 验证目标 Revision 是否满足 Agent/Environment 的活动硬 Gate；软失败不得抛出。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param agentId        Agent
     * @param environmentId  Environment
     * @param revisionId     目标不可变 Revision
     * @throws RuntimeException 当活动 HARD Gate 未通过时抛出稳定冲突异常
     */
    void requireEligible(
        OrganizationId organizationId,
        ProjectId projectId,
        AgentId agentId,
        EnvironmentId environmentId,
        RevisionId revisionId);
}
