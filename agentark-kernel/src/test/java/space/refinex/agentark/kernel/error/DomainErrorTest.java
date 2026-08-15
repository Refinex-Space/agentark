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

package space.refinex.agentark.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证领域错误码、违规详情与领域异常的稳定性约束。
 *
 * @author refinex
 */
class DomainErrorTest {

  /** 验证领域异常保留稳定错误码，并对违规详情集合执行防御性复制。 */
  @Test
  void carriesStableCodeAndDefensivelyCopiedViolations() {
    DomainErrorCode code = new DomainErrorCode("ARK-CONTROL-AGENT-42201");
    List<Violation> source =
        new ArrayList<>(
            List.of(new Violation("model.credential", "SECRET_NOT_ACCESSIBLE", "Not bound")));

    DomainException exception = new DomainException(code, "Revision is invalid", source);
    source.clear();

    assertThat(exception.errorCode()).isEqualTo(code);
    assertThat(exception.violations()).hasSize(1).isUnmodifiable();
  }

  /** 验证不符合命名协议的错误码、违规码及非法异常参数均被拒绝。 */
  @Test
  void rejectsUnstableErrorAndViolationCodes() {
    assertThatThrownBy(() -> new DomainErrorCode("CONTROL_422"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DomainErrorCode("ARK-CONTROL-AGENT-EXTRA-42201"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Violation("model", "secret-missing", "Missing"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DomainException(
                    new DomainErrorCode("ARK-CONTROL-AGENT-42201"),
                    "Invalid",
                    (List<Violation>) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new DomainException(new DomainErrorCode("ARK-CONTROL-AGENT-42201"), " ", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
