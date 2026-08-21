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

package space.refinex.agentark.server.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 验证 Argon2id、Pepper、长度、弱密码和随机临时密码边界。
 *
 * @author refinex
 */
class GatewayIdentityPasswordServiceTest {

    /** 证明正确 Pepper 可校验且错误密码或不同 Pepper 不能匹配。 */
    @Test
    void hashesWithArgon2AndDeploymentPepper() {
        GatewayIdentityPasswordService service = service("pepper-one");
        String encoded = service.encode("a sufficiently long passphrase");

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(service.matches("a sufficiently long passphrase", encoded)).isTrue();
        assertThat(service.matches("another sufficiently long passphrase", encoded)).isFalse();
        assertThat(service("pepper-two").matches("a sufficiently long passphrase", encoded)).isFalse();
    }

    /** 证明正式密码拒绝短值、账号复用和常见弱密码。 */
    @Test
    void enforcesPasswordPolicyWithoutCompositionRules() {
        GatewayIdentityPasswordService service = service("pepper-one");
        assertThatThrownBy(() -> service.validateNewPassword(
            "short", "operator", "operator@example.test"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateNewPassword(
            "operator", "operator", "operator@example.test"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> service.validateNewPassword(
            "a long phrase with spaces", "operator", "operator@example.test"))
            .doesNotThrowAnyException();
    }

    /** 证明临时密码具有 192 bit 随机输入且两次生成不同。 */
    @Test
    void generatesDistinctTemporaryPasswords() {
        GatewayIdentityPasswordService service = service("pepper-one");
        String first = service.temporaryPassword();
        String second = service.temporaryPassword();
        assertThat(first).hasSize(32).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]{32}");
    }

    /** 创建测试密码服务。 */
    private static GatewayIdentityPasswordService service(String pepper) {
        GatewayIdentityProperties properties = new GatewayIdentityProperties();
        properties.setPasswordPepper(pepper);
        return new GatewayIdentityPasswordService(properties);
    }
}
