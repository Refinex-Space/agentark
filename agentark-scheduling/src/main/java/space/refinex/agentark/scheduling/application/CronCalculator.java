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

package space.refinex.agentark.scheduling.application;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 只负责使用明确 IANA 时区计算下一 Cron 时间，不创建或执行 Job。
 *
 * @author refinex
 */
public final class CronCalculator {

    /**
     * 创建无状态 Cron 计算器。
     */
    public CronCalculator() {
    }

    /**
     * 计算严格晚于锚点的下一次计划时间，DST 重叠与缺口交给 ZoneRules 解析。
     *
     * @param expression Spring 六段 Cron 表达式
     * @param zoneId     IANA 时区
     * @param after      不包含的 UTC 锚点
     * @return 下一 UTC 时间
     */
    public Instant next(String expression, ZoneId zoneId, Instant after) {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(after, "after must not be null");
        ZonedDateTime next = CronExpression.parse(expression)
            .next(ZonedDateTime.ofInstant(after, zoneId));
        if (next == null || !next.toInstant().isAfter(after)) {
            throw new IllegalArgumentException("cron expression has no next execution");
        }
        return next.toInstant();
    }
}
