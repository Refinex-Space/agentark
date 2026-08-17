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

package space.refinex.agentark.control.secret.adapter.out.vault;

import java.io.IOException;

/**
 * 按请求读取短期 Vault 工作负载令牌，调用方负责及时清零返回数组。
 *
 * @author refinex
 */
@FunctionalInterface
public interface VaultTokenSource {

    /**
     * 读取当前短期令牌。
     *
     * @return 非空令牌字符数组
     * @throws IOException 令牌不可用或权限不安全时抛出
     */
    char[] load() throws IOException;
}
