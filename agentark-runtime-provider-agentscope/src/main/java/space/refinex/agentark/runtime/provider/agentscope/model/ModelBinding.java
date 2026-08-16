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

import space.refinex.agentark.kernel.ref.SecretRef;

import java.util.Map;
import java.util.Objects;

/**
 * 表示编译后仍不含明文凭据的 AgentScope Model 构建输入。
 *
 * @param provider         模型 Provider 稳定名称
 * @param modelName        Provider 内模型名称
 * @param parameters       冻结的通用模型参数
 * @param secretRef        凭据逻辑引用
 * @param resolutionPolicy Secret 解析策略
 * @author refinex
 */
public record ModelBinding(
    String provider,
    String modelName,
    Map<String, Object> parameters,
    SecretRef secretRef,
    String resolutionPolicy) {

    /**
     * 校验 Model 绑定完整且不包含空白值。
     */
    public ModelBinding {
        if (provider == null || provider.isBlank() || modelName == null || modelName.isBlank()
            || resolutionPolicy == null || resolutionPolicy.isBlank()) {
            throw new IllegalArgumentException("model binding text is invalid");
        }
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
        Objects.requireNonNull(secretRef, "secretRef must not be null");
    }
}
