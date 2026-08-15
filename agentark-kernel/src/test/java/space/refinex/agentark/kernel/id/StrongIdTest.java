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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 对全部强类型标识执行统一的生成、解析和 UUIDv7 约束测试。
 *
 * @author refinex
 */
class StrongIdTest {

  /**
   * 验证每种强类型标识都能生成、往返解析并拒绝非 UUIDv7 值。
   *
   * @param type 待验证的强类型标识类
   * @throws ReflectiveOperationException 反射调用契约不完整时抛出
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("strongIdTypes")
  void everyStrongIdGeneratesParsesAndRejectsNonV7(Class<? extends StrongId> type)
      throws ReflectiveOperationException {
    StrongId generated = (StrongId) type.getMethod("generate").invoke(null);
    StrongId parsed =
        (StrongId) type.getMethod("parse", String.class).invoke(null, generated.asString());

    assertThat(generated.value().version()).isEqualTo(7);
    assertThat(generated.value().variant()).isEqualTo(2);
    assertThat(parsed).isEqualTo(generated);
    assertThat(generated.generatedAt()).isNotNull();
    assertThatThrownBy(() -> type.getConstructor(UUID.class).newInstance(UUID.randomUUID()))
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  /**
   * 从密封接口读取所有获准的强类型标识实现。
   *
   * @return 强类型标识类流
   */
  private static Stream<Class<? extends StrongId>> strongIdTypes() {
    Class<?>[] permitted = StrongId.class.getPermittedSubclasses();
    assertThat(permitted).hasSize(20);
    return Arrays.stream(permitted).map(type -> type.asSubclass(StrongId.class));
  }
}
