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

package space.refinex.agentark.kernel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * 验证 Kernel 与外部框架、适配器和服务启动模块之间的依赖边界。
 *
 * @author refinex
 */
class KernelArchitectureTest {

  /** 仅包含生产代码的 AgentArk 类集合，供全部架构规则复用。 */
  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("space.refinex.agentark");

  /** 验证 Kernel 不依赖框架、持久化、缓存、序列化或运行时供应商实现。 */
  @Test
  void kernelHasNoFrameworkPersistenceOrProviderDependencies() {
    noClasses()
        .that()
        .resideInAPackage("..kernel..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "com.baomidou..",
            "io.agentscope..",
            "com.fasterxml..",
            "org.redisson..",
            "redis.clients..")
        .check(PRODUCTION_CLASSES);
  }

  /** 验证领域包不能反向依赖适配器包。 */
  @Test
  void domainPackagesCannotDependOnAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter..")
        .allowEmptyShould(true)
        .check(PRODUCTION_CLASSES);
  }

  /** 验证库代码不能依赖仅供进程启动使用的 Server 包。 */
  @Test
  void librariesCannotDependOnServerPackages() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..server..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..server..")
        .check(PRODUCTION_CLASSES);
  }

  /** 验证 Spring Boot 启动注解只能出现在 Server 包。 */
  @Test
  void springBootApplicationCanAppearOnlyInServerPackages() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..server..")
        .should()
        .beAnnotatedWith("org.springframework.boot.autoconfigure.SpringBootApplication")
        .check(PRODUCTION_CLASSES);
  }

  /** 验证故意构造的领域到适配器依赖能够触发架构规则失败。 */
  @Test
  void intentionalDomainToAdapterViolationIsDetected() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages("space.refinex.agentark.kernel.architecture.fixture");
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..fixture.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..fixture.adapter..");

    assertThatThrownBy(() -> rule.check(fixture)).isInstanceOf(AssertionError.class);
  }
}
