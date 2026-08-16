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

package space.refinex.agentark.knowledge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 固化 Knowledge Domain/Application 的 Provider 中立边界和 Control 组合根依赖方向。
 *
 * @author refinex
 */
class KnowledgeArchitectureTest {

    /** 创建 Knowledge 架构测试实例。 */
    KnowledgeArchitectureTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 证明 Domain 不依赖 Spring、Jackson、MyBatis、AgentScope 或向量数据库客户端。 */
    @Test
    void keepsDomainFreeFromFrameworkAndProviderTypes() {
        var classes = new ClassFileImporter().importPackages("space.refinex.agentark.knowledge");

        noClasses().that().resideInAPackage("..knowledge.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "tools.jackson..", "org.apache.ibatis..",
                "com.baomidou..", "io.agentscope..", "io.qdrant..", "co.elastic..",
                "io.milvus..", "com.pgvector..")
            .check(classes);
    }

    /** 证明 Domain/Application 不直接依赖 AgentScope、向量数据库或 Control 实现。 */
    @Test
    void keepsApplicationProviderNeutralAndControlIndependent() {
        var classes = new ClassFileImporter().importPackages("space.refinex.agentark.knowledge");

        noClasses().that().resideInAnyPackage("..knowledge.domain..", "..knowledge.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.agentscope..", "io.qdrant..", "co.elastic..", "io.milvus..",
                "com.pgvector..", "space.refinex.agentark.control..")
            .check(classes);
    }
}
