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

package space.refinex.agentark.knowledge.port;

import java.util.Arrays;
import java.util.Objects;

/**
 * 表示带向量的 Chunk；构造和访问时均复制数组，防止不可变结果被外部修改。
 *
 * @param chunk  原始 Chunk
 * @param vector 有限浮点数组
 * @author refinex
 */
public record EmbeddedChunk(KnowledgeChunk chunk, float[] vector) {

    /**
     * 校验 Chunk 和非空有限向量，并复制调用方数组。
     *
     * @param chunk  原始 Chunk
     * @param vector 向量值
     */
    public EmbeddedChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (vector == null
            || vector.length == 0
            || Arrays.stream(toDoubleArray(vector)).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain finite values");
        }
        vector = vector.clone();
    }

    /**
     * 返回防御性复制后的向量。
     *
     * @return 新的向量数组
     */
    @Override
    public float[] vector() {
        return vector.clone();
    }

    /**
     * 将浮点数组转换成可由 Stream 校验的双精度数组。
     *
     * @param values 浮点数组
     * @return 双精度数组
     */
    private static double[] toDoubleArray(float[] values) {
        double[] converted = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            converted[index] = values[index];
        }
        return converted;
    }
}
