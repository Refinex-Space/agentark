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

package space.refinex.agentark.runtime.provider.agentscope.secret;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 验证短生命周期 Secret 的防御性复制、脱敏输出和主动清零。
 *
 * @author refinex
 */
class ResolvedSecretTest {

    /** 验证关闭后内部字符被覆盖且日志输出始终脱敏。 */
    @Test
    void clearsValueAndNeverRendersPlaintext() {
        ResolvedSecret secret = new ResolvedSecret("temporary-value".toCharArray());

        assertThat(secret.copyValue()).containsExactly("temporary-value".toCharArray());
        assertThat(secret.toString()).isEqualTo("ResolvedSecret[redacted]");
        secret.close();

        assertThat(secret.copyValue()).containsOnly('\0');
        assertThat(secret.toString()).doesNotContain("temporary-value");
    }
}
