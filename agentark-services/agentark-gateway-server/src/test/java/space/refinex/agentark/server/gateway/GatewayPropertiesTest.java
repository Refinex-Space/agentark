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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 验证 Gateway CORS 与服务地址配置拒绝通配、明文公网和内嵌凭据。
 *
 * @author refinex
 */
class GatewayPropertiesTest {

    /**
     * 验证精确 HTTPS 和 loopback HTTP Origin 可以配置。
     */
    @Test
    void acceptsExactSafeOrigins() {
        GatewayProperties properties = new GatewayProperties();

        properties.setAllowedOrigins(List.of(
            "https://console.example.test", "http://localhost:5173"));

        assertThat(properties.getAllowedOrigins()).containsExactly(
            "https://console.example.test", "http://localhost:5173");
    }

    /**
     * 验证通配和公网明文 Origin 被拒绝。
     */
    @Test
    void rejectsWildcardAndPublicHttpOrigins() {
        GatewayProperties properties = new GatewayProperties();

        assertThatThrownBy(() -> properties.setAllowedOrigins(List.of("https://*.example.test")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setAllowedOrigins(List.of("http://console.example.test")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setAllowedOrigins(List.of("https://console.example.test/path")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
