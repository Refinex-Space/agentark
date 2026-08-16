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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Cron 计算与执行解耦，并覆盖 IANA 时区 DST 缺口行为。
 *
 * @author refinex
 */
class CronCalculatorTest {

    /** 创建 Cron 测试实例。 */
    CronCalculatorTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明纽约春季 DST 不存在的 02:30 不会被平移成错误即时点。 */
    @Test
    void skipsNonexistentLocalTimeDuringDstGap() {
        Instant next = new CronCalculator().next(
            "0 30 2 * * *", ZoneId.of("America/New_York"),
            Instant.parse("2026-03-08T00:00:00Z"));

        assertThat(next).isEqualTo(Instant.parse("2026-03-09T06:30:00Z"));
    }

    /** 证明同一表达式在 UTC 中严格返回锚点后的下一次时间。 */
    @Test
    void returnsStrictlyLaterUtcFireTime() {
        Instant anchor = Instant.parse("2026-08-16T10:15:00Z");

        assertThat(new CronCalculator().next("0 0 * * * *", ZoneId.of("UTC"), anchor))
            .isEqualTo(Instant.parse("2026-08-16T11:00:00Z"));
    }
}
