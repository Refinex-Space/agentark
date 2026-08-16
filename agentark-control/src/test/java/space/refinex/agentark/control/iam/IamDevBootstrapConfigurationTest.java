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

package space.refinex.agentark.control.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证开发引导即使被错误开启也不能进入生产 Profile。
 *
 * @author refinex
 */
class IamDevBootstrapConfigurationTest {

    /** 轻量配置上下文执行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(IamDevBootstrapConfiguration.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
        .withPropertyValues("agentark.control.iam.dev-bootstrap.enabled=true");

    /** 创建开发引导配置测试。 */
    IamDevBootstrapConfigurationTest() {
        // JUnit Jupiter 为测试生命周期创建实例。
    }

    /** 验证生产 Profile 不装配任何开发引导执行器。 */
    @Test
    void disablesDevBootstrapInProductionProfile() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }
}
