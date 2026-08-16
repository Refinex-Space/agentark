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

import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeProviderMetadata;

/**
 * 提供当前 Runtime 进程可执行 Provider 的语言中立能力元数据。
 *
 * @author refinex
 */
@FunctionalInterface
public interface RuntimeProviderCatalog {

    /**
     * 读取当前进程唯一启用的 Runtime Provider 元数据。
     *
     * @return Provider 元数据
     */
    RuntimeProviderMetadata current();
}
