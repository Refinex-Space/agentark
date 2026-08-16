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

package space.refinex.agentark.runtime.provider.agentscope.secret;

import space.refinex.agentark.kernel.ref.SecretRef;

/**
 * 按需解析 Snapshot 中 SecretRef 的运行时端口，实现不得将明文写入缓存或持久层。
 *
 * @author refinex
 */
@FunctionalInterface
public interface SecretResolver {

    /**
     * 解析指定 Secret 引用。
     *
     * @param secretRef        Secret 逻辑引用
     * @param resolutionPolicy LATEST_ENABLED 或 PINNED_VERSION
     * @return 需在 RuntimeHandle 关闭时清零的 Secret
     */
    ResolvedSecret resolve(SecretRef secretRef, String resolutionPolicy);
}
