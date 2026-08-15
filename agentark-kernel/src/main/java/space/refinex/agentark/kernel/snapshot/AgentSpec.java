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
import java.util.regex.Pattern;

/**
 * 表示可执行 Agent 的名称、入口以及 Runtime 编译时必须具备的能力集合。
 *
 * @param name                 发布时固定的 Agent 规范名称
 * @param entrypoint           Provider 中立执行入口
 * @param requiredCapabilities Runtime Provider 必须满足的稳定能力名列表
 * @author refinex
 */
public record AgentSpec(
    String name, AgentEntrypoint entrypoint, List<String> requiredCapabilities) {

    /**
     * Agent 名称和能力名共同使用的小写稳定标识格式。
     */
    private static final Pattern CAPABILITY = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    /**
     * 校验并创建 Agent 执行规范，同时防御性复制能力列表。
     *
     * @param name                 Agent 规范名称，最长 128 字符
     * @param entrypoint           执行入口
     * @param requiredCapabilities 能力名列表，不允许重复或包含空值
     * @throws NullPointerException     当入口或能力列表为 {@code null} 时抛出
     * @throws IllegalArgumentException 当名称、能力名或重复性不满足约束时抛出
     */
    public AgentSpec {
        SnapshotRequirements.matching(name, "AgentSpec name", CAPABILITY, 128);
        Objects.requireNonNull(entrypoint, "AgentSpec entrypoint must not be null");
        requiredCapabilities =
            SnapshotRequirements.immutableList(requiredCapabilities, "AgentSpec requiredCapabilities");
        if (requiredCapabilities.stream()
            .anyMatch(value -> value.length() > 64 || !CAPABILITY.matcher(value).matches())) {
            throw new IllegalArgumentException(
                "AgentSpec requiredCapabilities contains an invalid capability");
        }
        if (requiredCapabilities.stream().distinct().count() != requiredCapabilities.size()) {
            throw new IllegalArgumentException(
                "AgentSpec requiredCapabilities must not contain duplicates");
        }
    }
}
