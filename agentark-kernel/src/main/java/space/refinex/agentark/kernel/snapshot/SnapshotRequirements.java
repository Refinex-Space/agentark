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
import java.math.BigInteger;
import java.util.*;
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

    /**
     * 校验配置对象只包含语言中立 JSON 值，并按输入顺序创建深层不可变副本。
     *
     * @param values 待复制的配置对象
     * @param field  写入异常上下文的字段名
     * @return 深层不可变配置对象
     * @throws NullPointerException     当配置对象为 {@code null} 时抛出
     * @throws IllegalArgumentException 当键为空、值为 {@code null} 或包含非 JSON 类型时抛出
     */
    static Map<String, Object> immutableJsonObject(Map<String, Object> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(field + " must not contain blank keys");
            }
            copy.put(key, immutableJsonValue(value, field + "." + key));
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 将单个 JSON 值递归转换为不可变值。
     *
     * @param value 待复制值
     * @param field 写入异常上下文的字段名
     * @return 不可变 JSON 值
     */
    private static Object immutableJsonValue(Object value, String field) {
        if ((value instanceof Double doubleValue && !Double.isFinite(doubleValue))
            || (value instanceof Float floatValue && !Float.isFinite(floatValue))) {
            throw new IllegalArgumentException(field + " must contain a finite JSON number");
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
            || value instanceof Long || value instanceof Short || value instanceof Byte
            || value instanceof BigInteger || value instanceof BigDecimal
            || value instanceof Double || value instanceof Float) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException(field + " must contain string keys");
                }
                typed.put(text, nested);
            });
            return immutableJsonObject(typed, field);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream()
                .map(item -> immutableJsonValue(item, field + "[]"))
                .toList());
        }
        throw new IllegalArgumentException(field + " contains a non-JSON value");
    }
}
