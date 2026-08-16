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

package space.refinex.agentark.runtime.provider.agentscope.permission;

import java.util.List;
import java.util.Objects;

/**
 * 表示 Snapshot 冻结的默认权限决策和有序覆盖规则。
 *
 * @param defaultDecision ALLOW、ASK 或 DENY
 * @param rules           有序资源决策规则
 * @author refinex
 */
public record PermissionBinding(String defaultDecision, List<Rule> rules) {

    /**
     * 校验权限决策完整并创建不可变规则列表。
     */
    public PermissionBinding {
        if (defaultDecision == null || defaultDecision.isBlank()) {
            throw new IllegalArgumentException("defaultDecision must not be blank");
        }
        rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
    }

    /**
     * @param resource 资源稳定模式
     * @param decision ALLOW、ASK 或 DENY
     * @author refinex
     */
    public record Rule(String resource, String decision) {

        /**
         * 校验单条权限规则完整。
         */
        public Rule {
            if (resource == null || resource.isBlank() || decision == null || decision.isBlank()) {
                throw new IllegalArgumentException("permission rule is invalid");
            }
        }
    }
}
