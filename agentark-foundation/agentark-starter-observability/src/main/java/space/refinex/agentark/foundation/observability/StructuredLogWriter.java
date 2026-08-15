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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 将候选字段先清理再编码为单行 JSON，调用方负责交给具体日志框架输出。
 *
 * @author refinex
 */
public final class StructuredLogWriter {

    /**
     * Jackson 3 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 敏感数据清理器。
     */
    private final SensitiveDataSanitizer sanitizer;

    /**
     * 创建结构化日志写入器。
     *
     * @param jsonMapper Jackson 3 映射器
     * @param sanitizer  敏感数据清理器
     */
    public StructuredLogWriter(JsonMapper jsonMapper, SensitiveDataSanitizer sanitizer) {
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.sanitizer = java.util.Objects.requireNonNull(sanitizer, "sanitizer must not be null");
    }

    /**
     * 创建并编码单行结构化日志。
     *
     * @param level   日志级别
     * @param message 单行诊断消息
     * @param traceId 可选 W3C Trace ID
     * @param fields  候选字段
     * @return 不含换行的 JSON 字符串
     * @throws IllegalStateException JSON 编码失败时抛出
     */
    public String write(
        String level, String message, Optional<String> traceId, Map<String, String> fields) {
        StructuredLogEvent event =
            new StructuredLogEvent(Instant.now(), level, message, traceId, sanitizer.sanitize(fields));
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (JacksonException error) {
            throw new IllegalStateException("failed to encode structured log event", error);
        }
    }
}
