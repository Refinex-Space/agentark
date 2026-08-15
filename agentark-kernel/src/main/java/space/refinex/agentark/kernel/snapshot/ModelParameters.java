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
 * 表示 AgentArk 无需依赖厂商 SDK 即可理解的稳定模型参数。
 *
 * @param temperature 模型采样温度，闭区间为 0 到 2
 * @param maxTokens   单次模型输出允许的最大 Token 数
 * @author refinex
 */
public record ModelParameters(BigDecimal temperature, int maxTokens) {

    /**
     * 平台契约允许的最大采样温度。
     */
    private static final BigDecimal MAX_TEMPERATURE = BigDecimal.valueOf(2);

    /**
     * 校验并创建模型参数。
     *
     * @param temperature 采样温度
     * @param maxTokens   最大输出 Token 数
     * @throws NullPointerException     当温度为 {@code null} 时抛出
     * @throws IllegalArgumentException 当温度越界或 Token 数小于 1 时抛出
     */
    public ModelParameters {
        Objects.requireNonNull(temperature, "ModelParameters temperature must not be null");
        if (temperature.signum() < 0 || temperature.compareTo(MAX_TEMPERATURE) > 0) {
            throw new IllegalArgumentException("ModelParameters temperature must be between 0 and 2");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("ModelParameters maxTokens must be positive");
        }
    }
}
