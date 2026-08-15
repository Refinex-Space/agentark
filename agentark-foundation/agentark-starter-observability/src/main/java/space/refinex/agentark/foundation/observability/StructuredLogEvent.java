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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 表示字段稳定、单行输出且已清理敏感数据的 JSON Structured Log Event。
 *
 * @param timestamp UTC 时间戳
 * @param level     大写日志级别
 * @param message   不含换行的诊断消息
 * @param traceId   可选 W3C Trace ID
 * @param fields    已清理的不可变字符串字段
 * @author refinex
 */
public record StructuredLogEvent(
    Instant timestamp,
    String level,
    String message,
    Optional<String> traceId,
    Map<String, String> fields) {

    /**
     * 校验并创建结构化日志事件。
     *
     * @param timestamp UTC 时间戳
     * @param level     日志级别
     * @param message   诊断消息
     * @param traceId   可选 Trace ID
     * @param fields    安全字段
     * @throws IllegalArgumentException 当级别、消息或 Trace ID 不合法时抛出
     * @throws NullPointerException     当对象参数为 {@code null} 时抛出
     */
    public StructuredLogEvent {
        java.util.Objects.requireNonNull(timestamp, "timestamp must not be null");
        traceId = java.util.Objects.requireNonNull(traceId, "traceId must not be null");
        fields = Map.copyOf(java.util.Objects.requireNonNull(fields, "fields must not be null"));
        if (level == null || !level.matches("TRACE|DEBUG|INFO|WARN|ERROR")) {
            throw new IllegalArgumentException("level is invalid");
        }
        if (message == null || message.isBlank() || message.contains("\n") || message.contains("\r")) {
            throw new IllegalArgumentException("message must be a non-blank single line");
        }
        if (traceId
            .filter(value -> !value.matches("[0-9a-f]{32}") || value.matches("0{32}"))
            .isPresent()) {
            throw new IllegalArgumentException("traceId is invalid");
        }
    }
}
