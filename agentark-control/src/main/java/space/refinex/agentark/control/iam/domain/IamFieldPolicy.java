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

package space.refinex.agentark.control.iam.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 集中维护 IAM 字段的稳定格式和 MySQL 微秒精度边界，不承担领域流程。
 *
 * @author refinex
 */
final class IamFieldPolicy {

    /**
     * 禁止实例化仅提供领域字段校验的类型。
     */
    private IamFieldPolicy() {
    }

    /**
     * 校验稳定 Slug 或 Key。
     *
     * @param value 待校验值
     * @param name  错误上下文中的字段名
     * @return 原始合法值
     */
    static String slug(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase slug");
        }
        return value;
    }

    /**
     * 校验面向用户的非空短文本。
     *
     * @param value     待校验值
     * @param name      错误上下文中的字段名
     * @param maxLength 最大字符数
     * @return 去除首尾空白后的值
     */
    static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds maximum length");
        }
        return normalized;
    }

    /**
     * 校验非负乐观锁版本。
     *
     * @param value 版本值
     * @return 原值
     */
    static long version(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        return value;
    }

    /**
     * 将时刻截断到 MySQL TIMESTAMP(6) 可稳定往返的微秒精度。
     *
     * @param value 必需 UTC 时间线时刻
     * @param name  错误上下文中的字段名
     * @return 微秒精度时刻
     */
    static Instant instant(Instant value, String name) {
        return java.util.Objects.requireNonNull(value, name + " must not be null")
            .truncatedTo(ChronoUnit.MICROS);
    }
}
