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

package space.refinex.agentark.control.catalog.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 集中维护资产字段格式和 MySQL 微秒精度，不负责序列化或业务流程。
 *
 * @author refinex
 */
final class CatalogFieldPolicy {

    /**
     * 禁止实例化静态字段策略。
     */
    private CatalogFieldPolicy() {
    }

    /**
     * @param value 待校验稳定 Key
     * @param name  字段名
     * @return 合法原值
     */
    static String key(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase key");
        }
        return value;
    }

    /**
     * @param value     待校验文本
     * @param name      字段名
     * @param maxLength 最大字符数
     * @return 去除首尾空白的文本
     */
    static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return value.trim();
    }

    /**
     * @param value     可选文本
     * @param name      字段名
     * @param maxLength 最大字符数
     * @return 空串或规范文本
     */
    static String optionalText(String value, String name, int maxLength) {
        return value == null || value.isBlank() ? "" : text(value, name, maxLength);
    }

    /**
     * @param value JSON 字符串
     * @param name  字段名
     * @return 非空 JSON 字符串
     */
    static String json(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * @param value 时刻
     * @param name  字段名
     * @return MySQL 可稳定往返的微秒精度时刻
     */
    static Instant instant(Instant value, String name) {
        return java.util.Objects.requireNonNull(value, name + " must not be null")
            .truncatedTo(ChronoUnit.MICROS);
    }
}

