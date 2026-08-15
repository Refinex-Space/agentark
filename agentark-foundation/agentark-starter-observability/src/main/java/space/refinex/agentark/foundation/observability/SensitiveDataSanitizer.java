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

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 对结构化日志字段执行敏感键拒绝和正文采集策略，敏感值统一替换且不会进入返回对象。
 *
 * @author refinex
 */
public final class SensitiveDataSanitizer {

    /**
     * 无条件拒绝采集的敏感字段词根。
     */
    private static final Set<String> SECRET_TOKENS =
        Set.of(
            "secret",
            "token",
            "password",
            "credential",
            "authorization",
            "cookie",
            "api_key",
            "apikey");

    /**
     * 可配置正文采集边界。
     */
    private final ObservabilityDataPolicy dataPolicy;

    /**
     * 创建敏感数据清理器。
     *
     * @param dataPolicy 正文采集策略
     */
    public SensitiveDataSanitizer(ObservabilityDataPolicy dataPolicy) {
        this.dataPolicy = java.util.Objects.requireNonNull(dataPolicy, "dataPolicy must not be null");
    }

    /**
     * 清理结构化日志字段，输出值最长 2048 字符且不可变。
     *
     * @param fields 候选字段
     * @return 排序后的安全字段
     */
    public Map<String, String> sanitize(Map<String, String> fields) {
        java.util.Objects.requireNonNull(fields, "fields must not be null");
        Map<String, String> sanitized = new TreeMap<>();
        fields.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return Map.copyOf(sanitized);
    }

    /**
     * 根据字段名和正文策略返回安全值。
     *
     * @param key   字段名
     * @param value 原始值
     * @return 原值、截断值或固定脱敏标记
     */
    private String sanitizeValue(String key, String value) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("structured log field name is invalid");
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if (SECRET_TOKENS.stream().anyMatch(normalized::contains)
            || (!dataPolicy.collectPromptText() && normalized.contains("prompt"))
            || (!dataPolicy.collectToolArguments()
            && (normalized.contains("tool.argument") || normalized.contains("tool_argument")))
            || (!dataPolicy.collectDocumentText()
            && (normalized.contains("document.text") || normalized.contains("document_text")))) {
            return "[REDACTED]";
        }
        String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= 2048 ? safe : safe.substring(0, 2048);
    }
}
