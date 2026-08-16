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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.kernel.id.DeploymentId;
import space.refinex.agentark.runtime.domain.RuntimeModels.DeploymentDescriptor;

/**
 * 通过 Control Internal Contract 解析新 Session 应固定的 Deployment 与 Revision。
 *
 * @author refinex
 */
@FunctionalInterface
public interface DeploymentResolver {

    /**
     * 解析只读 Deployment 描述；实现不得读取 Control Schema。
     *
     * @param deploymentId Deployment 标识
     * @return 语言中立 Deployment 描述
     */
    DeploymentDescriptor resolve(DeploymentId deploymentId);
}
