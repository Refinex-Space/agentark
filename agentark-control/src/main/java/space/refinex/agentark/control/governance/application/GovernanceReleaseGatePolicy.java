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

package space.refinex.agentark.control.governance.application;

import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.release.application.port.ReleaseGatePolicy;
import space.refinex.agentark.kernel.id.*;

import java.util.Objects;

/**
 * 将 Governance Release Gate 决策适配为 Release 应用端口。
 *
 * @author refinex
 */
public final class GovernanceReleaseGatePolicy implements ReleaseGatePolicy {

    /**
     * 治理持久化仓储。
     */
    private final GovernanceRepository repository;

    /**
     * 创建 Gate Policy。
     *
     * @param repository Governance Repository
     */
    public GovernanceReleaseGatePolicy(GovernanceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 硬 Gate 未满足时拒绝 Deployment 指针前移。
     */
    @Override
    public void requireEligible(OrganizationId organizationId, ProjectId projectId, AgentId agentId, EnvironmentId environmentId, RevisionId revisionId) {
        var decision = repository.evaluateReleaseGate(organizationId, projectId, agentId, environmentId, revisionId);
        if (!decision.allowed()) {
            throw new IamConflictException("release gate requires a passing evaluation");
        }
    }
}
