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

package space.refinex.agentark.runtime.provider.agentscope.model;

import io.agentscope.core.model.Model;
import space.refinex.agentark.runtime.provider.agentscope.secret.ResolvedSecret;

/**
 * 将 Provider 中立模型绑定创建为当前 Run 专属 AgentScope Model 的扩展端口。
 *
 * @author refinex
 */
@FunctionalInterface
public interface AgentScopeModelFactory {

    /**
     * 创建不与其他 Session 共享可变状态的 Model。
     *
     * @param binding 已校验且不含明文凭据的模型绑定
     * @param secret  当前 RuntimeHandle 专用凭据
     * @return AgentScope Model
     */
    Model create(ModelBinding binding, ResolvedSecret secret);
}
