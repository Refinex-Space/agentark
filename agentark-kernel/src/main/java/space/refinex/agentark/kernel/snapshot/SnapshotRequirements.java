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

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 仅供不可变 Snapshot 值对象构造阶段复用的局部校验规则，不作为通用工具类暴露。
 *
 * @author refinex
 */
final class SnapshotRequirements {

    /**
     * 禁止实例化无状态的 Snapshot 构造校验类。
     */
    private SnapshotRequirements() {
    }

    /**
     * 校验文本非空且不超过指定长度。
     *
     * @param value         待校验文本
     * @param field         写入异常上下文的字段名
     * @param maximumLength 允许的最大字符数
     * @return 校验通过的原文本
     * @throws IllegalArgumentException 当文本为空或超长时抛出
     */
    static String text(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                field + " must contain 1 to " + maximumLength + " characters");
        }
        return value;
    }

    /**
     * 校验文本长度和完整正则格式。
     *
     * @param value         待校验文本
     * @param field         写入异常上下文的字段名
     * @param pattern       必须完整匹配的正则表达式
     * @param maximumLength 允许的最大字符数
     * @return 校验通过的原文本
     * @throws IllegalArgumentException 当文本为空、超长或格式不匹配时抛出
     */
    static String matching(String value, String field, Pattern pattern, int maximumLength) {
        text(value, field, maximumLength);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }

    /**
     * 校验列表及元素非空，并返回防御性不可变副本。
     *
     * @param values 待复制列表
     * @param field  写入异常上下文的字段名
     * @param <T>    列表元素类型
     * @return 不含空元素的不可变列表
     * @throws NullPointerException     当列表为 {@code null} 时抛出
     * @throws IllegalArgumentException 当列表包含 {@code null} 元素时抛出
     */
    static <T> List<T> immutableList(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null elements");
        }
        return List.copyOf(values);
    }
}
