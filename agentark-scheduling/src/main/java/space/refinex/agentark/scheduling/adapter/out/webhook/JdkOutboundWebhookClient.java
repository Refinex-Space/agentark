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

package space.refinex.agentark.scheduling.adapter.out.webhook;

import space.refinex.agentark.scheduling.port.OutboundWebhookClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 使用 JDK HttpClient 投递 HTTPS Webhook，禁用重定向并限制响应摘要字节数。
 *
 * <p>生产端点的 DNS/IP SSRF 校验属于 Control 配置解析器；本适配器再次拒绝非 HTTPS、
 * UserInfo 与重定向，且不接收 Credential Header。
 *
 * @author refinex
 */
public final class JdkOutboundWebhookClient implements OutboundWebhookClient {

    /**
     * 最大响应摘要字节数。
     */
    private static final int MAX_RESPONSE_BYTES = 4096;

    /**
     * 禁用自动重定向的 HTTP Client。
     */
    private final HttpClient httpClient;

    /**
     * 创建 Outbound Webhook Client。
     *
     * @param httpClient 配置为 NEVER Redirect 的客户端
     */
    public JdkOutboundWebhookClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("outbound webhook redirects must be disabled");
        }
    }

    /**
     * 异步投递请求，并把网络错误分类为可重试失败。
     *
     * @param request 投递请求
     * @return Webhook 回执
     */
    @Override
    public CompletionStage<WebhookReceipt> send(WebhookRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.endpoint())
            .timeout(request.timeout())
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", request.providerIdempotencyKey())
            .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
        request.headers().forEach((name, value) -> {
            if (!name.matches("X-[A-Za-z0-9-]{1,60}")
                || name.equalsIgnoreCase("X-Forwarded-For")
                || value == null || value.length() > 1024) {
                throw new IllegalArgumentException("outbound webhook header is not allowed");
            }
            builder.header(name, value);
        });
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> {
                int status = response.statusCode();
                return new WebhookReceipt(
                    status,
                    Optional.of(boundedResponseSummary(status, response.body())),
                    status == 429 || status >= 500);
            })
            .exceptionally(exception -> new WebhookReceipt(
                599, Optional.of("network-error"), true));
    }

    /**
     * 最多读取 4097 字节并立即关闭响应流，防止恶意响应造成无界内存占用。
     *
     * @param status HTTP 状态码
     * @param body   流式响应正文
     * @return 不含正文内容的有界摘要
     */
    static String boundedResponseSummary(int status, InputStream body) {
        Objects.requireNonNull(body, "body must not be null");
        try (body) {
            int observed = body.readNBytes(MAX_RESPONSE_BYTES + 1).length;
            String length = observed > MAX_RESPONSE_BYTES
                ? ">" + MAX_RESPONSE_BYTES : Integer.toString(observed);
            return "http-status:" + status + ";body-bytes:" + length;
        } catch (IOException exception) {
            throw new IllegalStateException("outbound webhook response could not be read", exception);
        }
    }
}
