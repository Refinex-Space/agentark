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

import java.util.Objects;
import java.util.Optional;

/**
 * 表示单次 HTTP 请求的稳定关联信息和可选已认证租户上下文。
 *
 * @param requestId 非空请求关联标识
 * @param traceId   32 位小写十六进制 W3C Trace 标识
 * @param tenant    可选的已认证租户范围
 * @author refinex
 */
public record RequestContext(String requestId, String traceId, Optional<TenantContext> tenant) {

    /**
     * 校验请求关联字段并创建不可变上下文。
     *
     * @param requestId 请求标识
     * @param traceId   W3C Trace 标识
     * @param tenant    可选租户范围
     * @throws IllegalArgumentException 当请求标识为空或 Trace 标识格式错误时抛出
     * @throws NullPointerException     当租户容器为 {@code null} 时抛出
     */
    public RequestContext {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must contain 1 to 128 characters");
        }
        if (traceId == null || !traceId.matches("[0-9a-f]{32}") || traceId.matches("0{32}")) {
            throw new IllegalArgumentException("traceId must be a non-zero W3C trace id");
        }
        tenant = Objects.requireNonNull(tenant, "tenant must not be null");
    }
}
