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

package space.refinex.agentark.foundation.web;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从受控请求关联字段构造请求上下文，并拒绝格式异常或过长的外部标识。
 *
 * @author refinex
 */
public final class RequestContextFactory {

    /**
     * W3C traceparent 中版本、Trace ID、Span ID 和 Flags 的最小格式。
     */
    private static final Pattern TRACE_PARENT =
        Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    /**
     * 生成不可预测 Trace ID 的安全随机源。
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 从认证上下文解析租户范围的扩展点。
     */
    private final TenantContextResolver tenantContextResolver;

    /**
     * 创建请求上下文工厂。
     *
     * @param tenantContextResolver 仅从已认证上下文解析租户的解析器
     */
    public RequestContextFactory(TenantContextResolver tenantContextResolver) {
        this.tenantContextResolver =
            java.util.Objects.requireNonNull(
                tenantContextResolver, "tenantContextResolver must not be null");
    }

    /**
     * 使用可选外部 Request ID 与 traceparent 创建上下文。
     *
     * @param suppliedRequestId 调用方提供的请求标识，可为空
     * @param traceParent       W3C traceparent，可为空
     * @return 完整请求上下文
     */
    public RequestContext create(String suppliedRequestId, String traceParent) {
        String requestId = normalizeRequestId(suppliedRequestId);
        String traceId = parseTraceId(traceParent).orElseGet(this::newTraceId);
        return new RequestContext(requestId, traceId, tenantContextResolver.resolve());
    }

    /**
     * 校验外部请求标识，非法值不会回显而是生成新标识。
     *
     * @param suppliedRequestId 外部请求标识
     * @return 可安全写入响应 Header 的请求标识
     */
    private String normalizeRequestId(String suppliedRequestId) {
        if (suppliedRequestId != null && suppliedRequestId.matches("[A-Za-z0-9._:-]{1,128}")) {
            return suppliedRequestId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 解析合法且非零的 W3C Trace ID。
     *
     * @param traceParent W3C traceparent Header
     * @return 合法 Trace ID；缺失或非法时为空
     */
    private Optional<String> parseTraceId(String traceParent) {
        if (traceParent == null) {
            return Optional.empty();
        }
        Matcher matcher = TRACE_PARENT.matcher(traceParent);
        if (!matcher.matches() || matcher.group(1).matches("0{32}")) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    /**
     * 生成 16 字节非零随机 Trace ID。
     *
     * @return 32 位小写十六进制标识
     */
    private String newTraceId() {
        byte[] bytes = new byte[16];
        do {
            random.nextBytes(bytes);
        } while (allZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 判断随机字节是否全部为零。
     *
     * @param bytes 待检查字节
     * @return 全零时为 {@code true}
     */
    private boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
