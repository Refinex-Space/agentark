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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 验证 Gateway 空业务应用能以响应式 Web 容器启动且未声明业务路由。
 *
 * @author refinex
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentArkGatewayApplicationTest {

    /**
     * 读取测试运行期端口、应用标识和 Gateway 配置。
     */
    @Autowired
    private Environment environment;

    /**
     * 证明 Gateway 能在随机端口启动，且当前配置不包含业务路由定义。
     */
    @Test
    void startsWithoutBusinessRoutes() {
        assertThat(environment.getProperty("local.server.port", Integer.class)).isPositive();
        assertThat(environment.getProperty("spring.application.name"))
            .isEqualTo("agentark-gateway-server");
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.routes")).isNull();
    }
}
