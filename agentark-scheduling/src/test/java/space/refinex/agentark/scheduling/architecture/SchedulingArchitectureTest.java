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

package space.refinex.agentark.scheduling.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 防止 Scheduler 重新引入 Runtime 实现、Harness 推理循环或反向 Adapter 依赖。
 *
 * @author refinex
 */
class SchedulingArchitectureTest {

    /** 创建 Scheduler 架构测试实例。 */
    SchedulingArchitectureTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Scheduling 模块不链接 Runtime 实现或 AgentScope Harness。 */
    @Test
    void doesNotDependOnRuntimeImplementationOrHarness() {
        var classes = new ClassFileImporter().importPackages(
            "space.refinex.agentark.scheduling");
        var runtimeImplementationPackage = "space.refinex.agentark." + "runtime..";
        var harnessPackage = "io.agentscope." + "harness..";

        noClasses().should().dependOnClassesThat()
            .resideInAnyPackage(
                runtimeImplementationPackage, harnessPackage)
            .check(classes);
    }

    /** 证明 Domain 与 Application 不反向依赖 Adapter。 */
    @Test
    void keepsDomainAndApplicationIndependentFromAdapters() {
        var classes = new ClassFileImporter().importPackages(
            "space.refinex.agentark.scheduling");

        noClasses().that().resideInAnyPackage(
                "space.refinex.agentark.scheduling.domain..",
                "space.refinex.agentark.scheduling.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("space.refinex.agentark.scheduling.adapter..")
            .check(classes);
    }
}
