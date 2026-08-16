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

package space.refinex.agentark.knowledge.application.port;

import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.application.KnowledgeProjectContext;

/**
 * 定义由 Control 组合根实现的项目存在性、租户链和权限校验 Port。
 *
 * @author refinex
 */
public interface KnowledgeAccessPort {

    /**
     * 校验主体在项目范围拥有指定权限，并返回可信租户上下文。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param permission 稳定权限代码
     * @return 已完成 IAM 校验的项目上下文
     */
    KnowledgeProjectContext requireProjectPermission(
        AgentArkPrincipal principal, ProjectId projectId, String permission);
}
