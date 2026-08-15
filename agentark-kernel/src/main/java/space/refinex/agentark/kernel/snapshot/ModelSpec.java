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

package space.refinex.agentark.kernel.snapshot;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 表示 Provider 中立的模型选择、受限参数和引用式凭据绑定。
 *
 * @param provider   稳定模型 Provider 名称
 * @param modelName  Provider 内的具体模型名称
 * @param parameters AgentArk 可理解的稳定模型参数
 * @param credential 仅包含 SecretRef 的凭据绑定
 * @author refinex
 */
public record ModelSpec(
    String provider, String modelName, ModelParameters parameters, CredentialSpec credential) {

    /**
     * 模型 Provider 名称的小写稳定标识格式。
     */
    private static final Pattern PROVIDER = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    /**
     * 校验并创建模型规范。
     *
     * @param provider   Provider 名称，最长 64 字符
     * @param modelName  模型名称，最长 128 字符
     * @param parameters 稳定模型参数
     * @param credential 引用式凭据绑定
     * @throws NullPointerException     当参数或凭据为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 Provider 或模型名称不满足约束时抛出
     */
    public ModelSpec {
        SnapshotRequirements.matching(provider, "ModelSpec provider", PROVIDER, 64);
        SnapshotRequirements.text(modelName, "ModelSpec modelName", 128);
        Objects.requireNonNull(parameters, "ModelSpec parameters must not be null");
        Objects.requireNonNull(credential, "ModelSpec credential must not be null");
    }
}
