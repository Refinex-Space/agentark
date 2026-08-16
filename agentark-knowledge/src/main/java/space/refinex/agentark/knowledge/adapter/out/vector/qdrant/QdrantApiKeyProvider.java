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

package space.refinex.agentark.knowledge.adapter.out.vector.qdrant;

import java.util.Optional;

/**
 * 定义按请求解析 Qdrant API Key 的 Adapter 内部端口，返回数组由调用方立即清零。
 *
 * @author refinex
 */
@FunctionalInterface
public interface QdrantApiKeyProvider {

    /**
     * 解析当前 Qdrant 凭据；本地无认证 Profile 可返回空。
     *
     * @return 可选 API Key 字符副本
     */
    Optional<char[]> resolve();
}
