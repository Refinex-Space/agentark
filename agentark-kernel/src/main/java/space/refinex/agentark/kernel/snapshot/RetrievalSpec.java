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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 表示固化到知识修订版本绑定中的 Provider 中立检索参数。
 *
 * @param topK           最多返回的候选文档数量
 * @param scoreThreshold 相关性分数阈值，闭区间为 0 到 1
 * @param reranker       发布时固定的重排器名称
 * @author refinex
 */
public record RetrievalSpec(int topK, BigDecimal scoreThreshold, String reranker) {

    /**
     * 相关性分数阈值的最大值。
     */
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * 校验并创建检索参数。
     *
     * @param topK           候选数量，范围为 1 到 1000
     * @param scoreThreshold 相关性阈值
     * @param reranker       重排器名称，最长 128 字符
     * @throws NullPointerException     当阈值为 {@code null} 时抛出
     * @throws IllegalArgumentException 当数量、阈值或重排器名称不满足约束时抛出
     */
    public RetrievalSpec {
        if (topK < 1 || topK > 1_000) {
            throw new IllegalArgumentException("RetrievalSpec topK must be between 1 and 1000");
        }
        Objects.requireNonNull(scoreThreshold, "RetrievalSpec scoreThreshold must not be null");
        if (scoreThreshold.signum() < 0 || scoreThreshold.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("RetrievalSpec scoreThreshold must be between 0 and 1");
        }
        SnapshotRequirements.text(reranker, "RetrievalSpec reranker", 128);
    }
}
