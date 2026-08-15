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

package space.refinex.agentark.kernel.snapshot;

import java.util.List;
import java.util.Objects;

/**
 * 表示默认权限决策和有序的不可变覆盖规则。
 *
 * @param defaultDecision 没有匹配规则时使用的默认决策
 * @param rules           按声明顺序保存的资源规则
 * @author refinex
 */
public record PermissionSpec(PermissionDecision defaultDecision, List<PermissionRuleSpec> rules) {

    /**
     * 校验并创建权限规范，同时防御性复制规则列表。
     *
     * @param defaultDecision 默认权限决策
     * @param rules           不允许资源键重复的规则列表
     * @throws NullPointerException     当默认决策或规则列表为 {@code null} 时抛出
     * @throws IllegalArgumentException 当列表包含空元素或重复资源键时抛出
     */
    public PermissionSpec {
        Objects.requireNonNull(defaultDecision, "PermissionSpec defaultDecision must not be null");
        rules = SnapshotRequirements.immutableList(rules, "PermissionSpec rules");
        if (rules.stream().map(PermissionRuleSpec::resource).distinct().count() != rules.size()) {
            throw new IllegalArgumentException("PermissionSpec rules must not repeat a resource");
        }
    }
}
