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

package space.refinex.agentark.scheduling.application;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.domain.SchedulerException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Scheduler 管理权限和精确租户选择，防止跨 Project 对象访问。
 *
 * @author refinex
 */
class SchedulerAuthorizationServiceTest {

    /** 创建授权测试实例。 */
    SchedulerAuthorizationServiceTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明没有 Redrive 权限的主体得到稳定拒绝错误。 */
    @Test
    void rejectsPrincipalWithoutPermission() {
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        AgentArkPrincipal principal = principal(
            organizationId, projectId, Set.of(SchedulerAuthorizationService.READ));

        assertThatThrownBy(() -> new SchedulerAuthorizationService().requireProject(
            principal, SchedulerAuthorizationService.REDRIVE, organizationId, projectId))
            .isInstanceOfSatisfying(
                SchedulerException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo("SCHEDULER_ACCESS_DENIED"));
    }

    /** 证明错误 Project 选择使用不存在语义，避免暴露目标资源。 */
    @Test
    void hidesResourceFromDifferentProject() {
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        AgentArkPrincipal principal = principal(
            organizationId, ProjectId.generate(), Set.of(SchedulerAuthorizationService.REDRIVE));

        assertThatThrownBy(() -> new SchedulerAuthorizationService().requireProject(
            principal, SchedulerAuthorizationService.REDRIVE, organizationId, projectId))
            .isInstanceOfSatisfying(
                SchedulerException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo("JOB_NOT_FOUND"));
    }

    /**
     * 创建具有指定租户选择和权限的交互式主体。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param authorities    权限集合
     * @return 测试主体
     */
    private static AgentArkPrincipal principal(
        OrganizationId organizationId, ProjectId projectId, Set<String> authorities) {
        return new AgentArkPrincipal(
            "https://issuer.example", "scheduler-user", PrincipalType.USER,
            authorities,
            Optional.of(new TenantSelection(
                organizationId, Optional.of(projectId), Optional.empty())),
            Optional.empty());
    }
}
