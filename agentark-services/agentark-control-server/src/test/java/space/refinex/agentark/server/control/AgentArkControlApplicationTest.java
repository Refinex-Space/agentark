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

package space.refinex.agentark.server.control;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Import;
import space.refinex.agentark.control.catalog.CatalogControlConfiguration;
import space.refinex.agentark.control.iam.IamControlConfiguration;

/**
 * 验证 Control 空业务应用能以 Spring MVC 容器独立启动。
 *
 * @author refinex
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "agentark.control.iam.enabled=false",
        "agentark.control.catalog.enabled=false",
        "agentark.control.knowledge.enabled=false",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
            + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
    })
class AgentArkControlApplicationTest {

    /**
     * 创建 Control 服务上下文测试实例。
     */
    AgentArkControlApplicationTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 读取测试运行期端口与应用标识。
     */
    @Autowired
    private Environment environment;

    /**
     * 证明 Control 能在 test Profile 不连接外部数据库时完成应用上下文启动。
     */
    @Test
    void startsAsEmptyMvcApplication() {
        assertThat(environment.getProperty("local.server.port", Integer.class)).isPositive();
        assertThat(environment.getProperty("spring.application.name"))
            .isEqualTo("agentark-control-server");
    }

    /**
     * 证明生产组合根显式导入 IAM、Catalog 和 Knowledge，防止模块仅在集成测试中生效。
     */
    @Test
    void importsAllControlCapabilitiesAtCompositionRoot() {
        Import imports = AgentArkControlApplication.class.getAnnotation(Import.class);
        assertThat(imports.value()).containsExactlyInAnyOrder(
            IamControlConfiguration.class,
            CatalogControlConfiguration.class,
            KnowledgeControlBridgeConfiguration.class);
    }
}
