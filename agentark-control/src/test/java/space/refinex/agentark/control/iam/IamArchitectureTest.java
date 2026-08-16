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

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.iam.adapter.in.web.IamController;
import space.refinex.agentark.control.iam.application.IamApiKeyService;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.application.IamIdentityMappingService;

/**
 * 验证 IAM 事务与方法安全组件满足 Spring 基于类代理的结构约束。
 *
 * @author refinex
 */
class IamArchitectureTest {

    /** 创建 IAM 架构约束测试。 */
    IamArchitectureTest() {
        // JUnit Jupiter 为测试生命周期创建实例。
    }

    /** 验证需要事务或方法安全代理的类型没有被 final 修饰。 */
    @Test
    void keepsTransactionalServicesProxyable() {
        assertThat(Modifier.isFinal(IamApplicationService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(IamApiKeyService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(IamIdentityMappingService.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(IamController.class.getModifiers())).isFalse();
    }
}
