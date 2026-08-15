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

package space.refinex.agentark.server.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 验证 Scheduler 空工作器能以最小管理端点容器独立启动。
 *
 * @author refinex
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
    })
class AgentArkSchedulerApplicationTest {

    /**
     * 创建 Scheduler 服务上下文测试实例。
     */
    AgentArkSchedulerApplicationTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 读取测试运行期端口与应用标识。
     */
    @Autowired
    private Environment environment;

    /**
     * 证明 Scheduler 能在无 Job、Trigger 和 Harness 依赖时完成启动。
     */
    @Test
    void startsAsEmptyWorker() {
        assertThat(environment.getProperty("local.server.port", Integer.class)).isPositive();
        assertThat(environment.getProperty("spring.application.name"))
            .isEqualTo("agentark-scheduler-server");
    }
}
