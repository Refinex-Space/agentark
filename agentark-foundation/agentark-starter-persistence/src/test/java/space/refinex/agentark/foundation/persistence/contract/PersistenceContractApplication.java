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

package space.refinex.agentark.foundation.persistence.contract;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 仅用于启动 MySQL 持久化契约测试的最小 Spring Boot 配置，不是可部署应用。
 *
 * @author refinex
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = PersistenceContractMapper.class)
@Import(PersistenceContractTransactionFixture.class)
public class PersistenceContractApplication {

    /**
     * 创建契约测试配置实例。
     */
    public PersistenceContractApplication() {
        // Spring 测试上下文通过公开构造器创建该配置。
    }
}
