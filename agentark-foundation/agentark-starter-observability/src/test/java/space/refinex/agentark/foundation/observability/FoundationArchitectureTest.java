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

package space.refinex.agentark.foundation.observability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * 验证六个 Foundation Starter 不引入业务类型、AgentScope/JPA 依赖或跨 Starter 循环。
 *
 * @author refinex
 */
class FoundationArchitectureTest {

  /** Foundation 生产包的 ArchUnit 类视图。 */
  private static final JavaClasses FOUNDATION_CLASSES =
      new ClassFileImporter().importPackages("space.refinex.agentark.foundation");

  /** 验证 Foundation 不依赖 AgentScope、JPA 或任何 Server 包。 */
  @Test
  void foundationDoesNotDependOnForbiddenFrameworkOrServerTypes() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("space.refinex.agentark.foundation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.agentscope..", "jakarta.persistence..", "space.refinex.agentark..server..");

    rule.check(FOUNDATION_CLASSES);
  }

  /** 验证 Foundation 不声明业务 Controller、Mapper、Entity 或持久化 DO。 */
  @Test
  void foundationDoesNotOwnBusinessTypeNames() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("space.refinex.agentark.foundation..")
            .should()
            .haveNameMatching(".*(Controller|Mapper|Entity|DO)$");

    rule.check(FOUNDATION_CLASSES);
  }

  /** 验证六个 Starter 之间不存在生产代码依赖环。 */
  @Test
  void starterPackagesAreFreeOfCycles() {
    slices()
        .matching("space.refinex.agentark.foundation.(*)..")
        .should()
        .beFreeOfCycles()
        .check(FOUNDATION_CLASSES);
  }
}
