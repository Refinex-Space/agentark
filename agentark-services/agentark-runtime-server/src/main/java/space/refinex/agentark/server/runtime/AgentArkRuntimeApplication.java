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

package space.refinex.agentark.server.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentArk Runtime Plane 启动入口，当前只装配 WebFlux、Reactor 与管理端点。
 *
 * @author refinex
 */
@SpringBootApplication
public class AgentArkRuntimeApplication {

    /**
     * 启动 Runtime Spring Boot 应用；Phase 05 不引入 Dataplane 业务或 AgentScope Harness。
     *
     * @param args 传递给 Spring Boot 的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentArkRuntimeApplication.class, args);
    }
}
