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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 仅保留白名单 Metric Tag 并限制值长度，防止 Secret 泄漏和高基数标签失控。
 *
 * @author refinex
 */
public final class MetricTagPolicy {

    /**
     * 允许输出的 Metric Tag 名称。
     */
    private final Set<String> allowedTags;

    /**
     * 创建 Metric Tag 白名单策略。
     *
     * @param allowedTags 非空、稳定且不含敏感字段的 Tag 名称集合
     * @throws IllegalArgumentException 当集合为空或名称格式不合法时抛出
     */
    public MetricTagPolicy(Set<String> allowedTags) {
        this.allowedTags =
            Set.copyOf(java.util.Objects.requireNonNull(allowedTags, "allowedTags must not be null"));
        if (this.allowedTags.isEmpty()
            || this.allowedTags.stream().anyMatch(value -> !value.matches("[a-z][a-z0-9.]{0,62}"))) {
            throw new IllegalArgumentException("allowed metric tags are invalid");
        }
    }

    /**
     * 过滤并排序 Metric Tag，拒绝超过 128 字符的值。
     *
     * @param candidates 候选 Tag
     * @return 仅含白名单字段的不可变有序 Map
     * @throws IllegalArgumentException 当白名单字段值为空或过长时抛出
     */
    public Map<String, String> filter(Map<String, String> candidates) {
        java.util.Objects.requireNonNull(candidates, "candidates must not be null");
        Map<String, String> accepted = new TreeMap<>();
        candidates.forEach(
            (key, value) -> {
                if (allowedTags.contains(key)) {
                    if (value == null || value.isBlank() || value.length() > 128) {
                        throw new IllegalArgumentException("metric tag value is invalid");
                    }
                    accepted.put(key, value);
                }
            });
        return Map.copyOf(accepted);
    }
}
