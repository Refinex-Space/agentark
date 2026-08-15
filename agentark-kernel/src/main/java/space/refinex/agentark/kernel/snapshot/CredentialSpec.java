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

import space.refinex.agentark.kernel.ref.SecretRef;

/**
 * 表示只能包含不透明 Secret 引用和解析策略的凭据绑定，类型上禁止保存明文凭据。
 *
 * @param secretRef        外部 Secret Manager 中的非敏感引用
 * @param resolutionPolicy Runtime 解析 Secret 版本的策略
 * @author refinex
 */
public record CredentialSpec(SecretRef secretRef, SecretResolutionPolicy resolutionPolicy) {

    /**
     * 校验并创建凭据绑定。
     *
     * @param secretRef        Secret 引用
     * @param resolutionPolicy Secret 解析策略
     * @throws NullPointerException 当任一字段为 {@code null} 时抛出
     */
    public CredentialSpec {
        Objects.requireNonNull(secretRef, "CredentialSpec secretRef must not be null");
        Objects.requireNonNull(resolutionPolicy, "CredentialSpec resolutionPolicy must not be null");
    }
}
