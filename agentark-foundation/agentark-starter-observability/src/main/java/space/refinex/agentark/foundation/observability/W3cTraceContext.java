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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表示严格解析后的 W3C traceparent 字段，不负责采信未经网关校验的外部租户信息。
 *
 * @param version 两位小写十六进制版本
 * @param traceId 非零 32 位小写十六进制 Trace ID
 * @param spanId  非零 16 位小写十六进制 Parent Span ID
 * @param flags   两位小写十六进制 Trace Flags
 * @author refinex
 */
public record W3cTraceContext(String version, String traceId, String spanId, String flags) {

    /**
     * W3C traceparent 四段格式。
     */
    private static final Pattern FORMAT =
        Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    /**
     * 校验并创建 Trace Context。
     *
     * @param version 版本
     * @param traceId Trace ID
     * @param spanId  Parent Span ID
     * @param flags   Trace Flags
     * @throws IllegalArgumentException 当任一字段不符合 W3C 格式或标识全零时抛出
     */
    public W3cTraceContext {
        String value = version + "-" + traceId + "-" + spanId + "-" + flags;
        if (!FORMAT.matcher(value).matches() || traceId.matches("0{32}") || spanId.matches("0{16}")) {
            throw new IllegalArgumentException("trace context is not a valid W3C traceparent");
        }
    }

    /**
     * 严格解析 W3C traceparent。
     *
     * @param traceParent HTTP traceparent 字段
     * @return 解析后的 Trace Context
     * @throws IllegalArgumentException 当字段缺失、格式非法或标识全零时抛出
     */
    public static W3cTraceContext parse(String traceParent) {
        if (traceParent == null) {
            throw new IllegalArgumentException("traceparent must not be null");
        }
        Matcher matcher = FORMAT.matcher(traceParent);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("traceparent format is invalid");
        }
        return new W3cTraceContext(
            matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
    }

    /**
     * 返回规范 W3C traceparent 字符串。
     *
     * @return 四段小写十六进制字符串
     */
    @Override
    public String toString() {
        return version + "-" + traceId + "-" + spanId + "-" + flags;
    }
}
