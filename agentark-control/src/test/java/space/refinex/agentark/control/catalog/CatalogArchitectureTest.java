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

package space.refinex.agentark.control.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.catalog.adapter.in.web.CatalogController;
import space.refinex.agentark.control.catalog.application.CatalogApplicationService;
import space.refinex.agentark.control.secret.adapter.in.web.SecretController;
import space.refinex.agentark.control.secret.application.SecretApplicationService;

import java.lang.reflect.Modifier;

/**
 * 验证事务与方法安全类型可被 Spring 基于类代理，且不采用 final 静默绕过代理。
 *
 * @author refinex
 */
class CatalogArchitectureTest {

    /** 创建 Catalog 架构测试实例。 */
    CatalogArchitectureTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 验证 Catalog 与 Secret 的事务服务和 Controller 可代理。 */
    @Test
    void keepsTransactionalAndSecuredTypesProxyable() {
        assertThat(Modifier.isFinal(CatalogApplicationService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(SecretApplicationService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(CatalogController.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(SecretController.class.getModifiers())).isFalse();
    }
}

