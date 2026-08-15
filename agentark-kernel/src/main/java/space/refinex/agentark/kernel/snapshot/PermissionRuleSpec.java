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

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 表示规范资源键上的显式权限决策。
 *
 * @param resource 形如 {@code tool:filesystem.write} 的规范资源键
 * @param decision ALLOW、ASK 或 DENY 决策
 * @author refinex
 */
public record PermissionRuleSpec(String resource, PermissionDecision decision) {

    /**
     * 权限资源键的命名格式。
     */
    private static final Pattern RESOURCE =
        Pattern.compile("[a-z][a-z0-9_-]*:[a-zA-Z0-9][a-zA-Z0-9._*-]{0,126}");

    /**
     * 校验并创建权限规则。
     *
     * @param resource 规范资源键，最长 128 字符
     * @param decision 权限决策
     * @throws NullPointerException     当决策为 {@code null} 时抛出
     * @throws IllegalArgumentException 当资源键为空、超长或格式不合法时抛出
     */
    public PermissionRuleSpec {
        SnapshotRequirements.matching(resource, "PermissionRuleSpec resource", RESOURCE, 128);
        Objects.requireNonNull(decision, "PermissionRuleSpec decision must not be null");
    }
}
