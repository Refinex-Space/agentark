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

package space.refinex.agentark.scheduling.port;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 定义经过 SSRF 策略解析的 Outbound Webhook 投递端口。
 *
 * @author refinex
 */
@FunctionalInterface
public interface OutboundWebhookClient {

    /**
     * 投递 Webhook；实现必须限制重定向、响应大小、连接与请求超时。
     *
     * @param request 投递请求
     * @return 投递回执
     */
    CompletionStage<WebhookReceipt> send(WebhookRequest request);

    /**
     * @param endpoint               已由配置所有者验证的 HTTPS 端点
     * @param body                   JSON 正文
     * @param headers                不含 Credential 的受限请求头
     * @param providerIdempotencyKey Provider 幂等键
     * @param timeout                请求超时
     * @author refinex
     */
    record WebhookRequest(
        URI endpoint,
        String body,
        Map<String, String> headers,
        String providerIdempotencyKey,
        Duration timeout) {

        /**
         * 校验 HTTPS 端点、正文、请求头、幂等键与超时。
         */
        public WebhookRequest {
            Objects.requireNonNull(endpoint, "endpoint must not be null");
            headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || body == null || body.isBlank()
                || providerIdempotencyKey == null || providerIdempotencyKey.isBlank()
                || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
                throw new IllegalArgumentException("outbound webhook request is invalid");
            }
        }
    }

    /**
     * @param statusCode      HTTP 状态码
     * @param responseSummary 有界脱敏响应摘要
     * @param retryable       是否为 429、5xx 或网络暂态错误
     * @author refinex
     */
    record WebhookReceipt(
        int statusCode, Optional<String> responseSummary, boolean retryable) {

        /**
         * 校验 HTTP 状态码与摘要容器。
         */
        public WebhookReceipt {
            responseSummary = Objects.requireNonNull(
                responseSummary, "responseSummary must not be null");
            if (statusCode < 100 || statusCode > 599
                || responseSummary.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("webhook receipt is invalid");
            }
        }

        /**
         * 返回 Provider 是否接受投递。
         *
         * @return 2xx 时为 true
         */
        public boolean successful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
