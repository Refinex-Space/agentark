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

package space.refinex.agentark.kernel.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 验证 UUIDv7 的 RFC 9562 布局、时间语义与输入边界。
 *
 * @author refinex
 */
class UuidV7Test {

  /** 验证生成结果使用 RFC 9562 的版本与变体位，并保留毫秒时间戳。 */
  @Test
  void generatesRfc9562UuidWithSuppliedTimestamp() {
    Instant timestamp = Instant.parse("2026-08-15T06:00:00.123Z");

    UUID value = UuidV7.generate(timestamp, new Random(42));

    assertThat(value.version()).isEqualTo(7);
    assertThat(value.variant()).isEqualTo(2);
    assertThat(UuidV7.timestamp(value)).isEqualTo(timestamp);
  }

  /** 验证解析过程拒绝非 UUIDv7、非小写规范形式和非法文本。 */
  @Test
  void rejectsValuesThatAreNotCanonicalUuidV7() {
    String valid = UuidV7.generate().toString();

    assertThatThrownBy(() -> UuidV7.parse(UUID.randomUUID().toString(), "TestId"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UuidV7.parse(valid.toUpperCase(), "TestId"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UuidV7.parse("not-a-uuid", "TestId"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 验证生成过程拒绝 UUIDv7 48 位时间字段范围之外的时间。 */
  @Test
  void rejectsTimestampOutsideUuidV7Range() {
    assertThatThrownBy(
            () -> UuidV7.generate(Instant.parse("1969-12-31T23:59:59.999Z"), new Random(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
