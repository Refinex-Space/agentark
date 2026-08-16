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

package space.refinex.agentark.server.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 定义 Gateway 路由、安全缓存、CORS、超时、请求大小和限流配置。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.gateway")
public class GatewayProperties {

    /** Control Plane 内部基础地址。 */
    private URI controlBaseUrl = URI.create("http://localhost:8081");

    /** Runtime Plane 内部基础地址。 */
    private URI runtimeBaseUrl = URI.create("http://localhost:8082");

    /** Scheduler Plane 内部基础地址。 */
    private URI schedulerBaseUrl = URI.create("http://localhost:8083");

    /** 精确允许的浏览器 Origin；空集合表示禁止跨域。 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 普通公共路由最大请求体。 */
    private DataSize maxRequestSize = DataSize.ofMegabytes(2);

    /** Webhook 路由最大请求体。 */
    private DataSize webhookMaxRequestSize = DataSize.ofMegabytes(1);

    /** 建立下游连接的超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 非 SSE 下游首个响应的超时。 */
    private Duration responseTimeout = Duration.ofSeconds(30);

    /** API Key 边缘认证正缓存 TTL。 */
    private Duration apiKeyCacheTtl = Duration.ofSeconds(10);

    /** API Key 正缓存最大条目数。 */
    private int apiKeyCacheMaxEntries = 10_000;

    /** 是否启用 Redis 固定窗口限流。 */
    private boolean rateLimitEnabled;

    /** 普通公共请求单主体单窗口额度。 */
    private long defaultRateLimit = 600;

    /** Webhook 单来源单窗口额度。 */
    private long webhookRateLimit = 120;

    /** 固定限流窗口。 */
    private Duration rateLimitWindow = Duration.ofMinutes(1);

    /**
     * 返回 Control 基础地址。
     *
     * @return HTTP 或 HTTPS 绝对地址
     */
    public URI getControlBaseUrl() {
        return controlBaseUrl;
    }

    /**
     * 设置 Control 基础地址。
     *
     * @param controlBaseUrl HTTP 或 HTTPS 绝对地址
     */
    public void setControlBaseUrl(URI controlBaseUrl) {
        this.controlBaseUrl = requireServiceUri(controlBaseUrl, "controlBaseUrl");
    }

    /**
     * 返回 Runtime 基础地址。
     *
     * @return HTTP 或 HTTPS 绝对地址
     */
    public URI getRuntimeBaseUrl() {
        return runtimeBaseUrl;
    }

    /**
     * 设置 Runtime 基础地址。
     *
     * @param runtimeBaseUrl HTTP 或 HTTPS 绝对地址
     */
    public void setRuntimeBaseUrl(URI runtimeBaseUrl) {
        this.runtimeBaseUrl = requireServiceUri(runtimeBaseUrl, "runtimeBaseUrl");
    }

    /**
     * 返回 Scheduler 基础地址。
     *
     * @return HTTP 或 HTTPS 绝对地址
     */
    public URI getSchedulerBaseUrl() {
        return schedulerBaseUrl;
    }

    /**
     * 设置 Scheduler 基础地址。
     *
     * @param schedulerBaseUrl HTTP 或 HTTPS 绝对地址
     */
    public void setSchedulerBaseUrl(URI schedulerBaseUrl) {
        this.schedulerBaseUrl = requireServiceUri(schedulerBaseUrl, "schedulerBaseUrl");
    }

    /**
     * 返回精确 CORS Origin 白名单。
     *
     * @return 不可变 Origin 集合
     */
    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    /**
     * 设置 CORS Origin 白名单；生产只允许 HTTPS，本机开发允许 loopback HTTP。
     *
     * @param allowedOrigins 精确 Origin 集合
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        var checked = new ArrayList<String>();
        for (String origin : java.util.Objects.requireNonNull(
            allowedOrigins, "allowedOrigins must not be null")) {
            URI value = URI.create(origin);
            boolean loopbackHttp = "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(value.getHost())
                || "127.0.0.1".equals(value.getHost())
                || "[::1]".equals(value.getHost())
                || "::1".equals(value.getHost()));
            if (origin.contains("*")
                || !value.isAbsolute()
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null
                || !(value.getPath().isEmpty() || "/".equals(value.getPath()))
                || !("https".equalsIgnoreCase(value.getScheme()) || loopbackHttp)) {
                throw new IllegalArgumentException(
                    "allowedOrigins must contain exact HTTPS or loopback HTTP origins");
            }
            checked.add(origin);
        }
        this.allowedOrigins = checked;
    }

    /**
     * 返回普通请求体上限。
     *
     * @return 正数大小
     */
    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * 设置普通请求体上限。
     *
     * @param maxRequestSize 正数且不超过十六 MiB
     */
    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = requireSize(maxRequestSize, "maxRequestSize");
    }

    /**
     * 返回 Webhook 请求体上限。
     *
     * @return 正数大小
     */
    public DataSize getWebhookMaxRequestSize() {
        return webhookMaxRequestSize;
    }

    /**
     * 设置 Webhook 请求体上限。
     *
     * @param webhookMaxRequestSize 正数且不超过十六 MiB
     */
    public void setWebhookMaxRequestSize(DataSize webhookMaxRequestSize) {
        this.webhookMaxRequestSize = requireSize(
            webhookMaxRequestSize, "webhookMaxRequestSize");
    }

    /**
     * 返回下游连接超时。
     *
     * @return 正数有限时长
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 设置下游连接超时。
     *
     * @param connectTimeout 正数有限时长
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requireDuration(connectTimeout, "connectTimeout", Duration.ofSeconds(30));
    }

    /**
     * 返回普通响应超时。
     *
     * @return 正数有限时长
     */
    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * 设置普通响应超时。
     *
     * @param responseTimeout 正数有限时长
     */
    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = requireDuration(
            responseTimeout, "responseTimeout", Duration.ofMinutes(5));
    }

    /**
     * 返回 API Key 缓存 TTL。
     *
     * @return 不超过三十秒的正数时长
     */
    public Duration getApiKeyCacheTtl() {
        return apiKeyCacheTtl;
    }

    /**
     * 设置 API Key 缓存 TTL。
     *
     * @param apiKeyCacheTtl 正数且不超过三十秒
     */
    public void setApiKeyCacheTtl(Duration apiKeyCacheTtl) {
        this.apiKeyCacheTtl = requireDuration(
            apiKeyCacheTtl, "apiKeyCacheTtl", Duration.ofSeconds(30));
    }

    /**
     * 返回 API Key 缓存容量。
     *
     * @return 正数容量
     */
    public int getApiKeyCacheMaxEntries() {
        return apiKeyCacheMaxEntries;
    }

    /**
     * 设置 API Key 缓存容量。
     *
     * @param apiKeyCacheMaxEntries 一至十万条
     */
    public void setApiKeyCacheMaxEntries(int apiKeyCacheMaxEntries) {
        if (apiKeyCacheMaxEntries < 1 || apiKeyCacheMaxEntries > 100_000) {
            throw new IllegalArgumentException("apiKeyCacheMaxEntries must be between 1 and 100000");
        }
        this.apiKeyCacheMaxEntries = apiKeyCacheMaxEntries;
    }

    /**
     * 返回是否启用 Redis 限流。
     *
     * @return 启用时为 true
     */
    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    /**
     * 设置是否启用 Redis 限流。
     *
     * @param rateLimitEnabled 是否启用
     */
    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    /**
     * 返回普通请求窗口额度。
     *
     * @return 正数额度
     */
    public long getDefaultRateLimit() {
        return defaultRateLimit;
    }

    /**
     * 设置普通请求窗口额度。
     *
     * @param defaultRateLimit 正数额度
     */
    public void setDefaultRateLimit(long defaultRateLimit) {
        this.defaultRateLimit = requireLimit(defaultRateLimit, "defaultRateLimit");
    }

    /**
     * 返回 Webhook 窗口额度。
     *
     * @return 正数额度
     */
    public long getWebhookRateLimit() {
        return webhookRateLimit;
    }

    /**
     * 设置 Webhook 窗口额度。
     *
     * @param webhookRateLimit 正数额度
     */
    public void setWebhookRateLimit(long webhookRateLimit) {
        this.webhookRateLimit = requireLimit(webhookRateLimit, "webhookRateLimit");
    }

    /**
     * 返回固定限流窗口。
     *
     * @return 正数有限时长
     */
    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    /**
     * 设置固定限流窗口。
     *
     * @param rateLimitWindow 正数且不超过一小时
     */
    public void setRateLimitWindow(Duration rateLimitWindow) {
        this.rateLimitWindow = requireDuration(
            rateLimitWindow, "rateLimitWindow", Duration.ofHours(1));
    }

    /** 校验平面基础地址。 */
    private URI requireServiceUri(URI value, String name) {
        if (value == null
            || !value.isAbsolute()
            || value.getHost() == null
            || value.getUserInfo() != null
            || value.getQuery() != null
            || value.getFragment() != null
            || !(value.getPath().isEmpty() || "/".equals(value.getPath()))
            || !("http".equalsIgnoreCase(value.getScheme())
            || "https".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException(
                name + " must be an absolute root HTTP(S) URI without credentials or parameters");
        }
        return value;
    }

    /** 校验请求体大小。 */
    private DataSize requireSize(DataSize value, String name) {
        if (value == null || value.toBytes() < 1 || value.toBytes() > DataSize.ofMegabytes(16).toBytes()) {
            throw new IllegalArgumentException(name + " must be between 1 byte and 16 MiB");
        }
        return value;
    }

    /** 校验配置时长。 */
    private Duration requireDuration(Duration value, String name, Duration maximum) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most " + maximum);
        }
        return value;
    }

    /** 校验限流额度。 */
    private long requireLimit(long value, String name) {
        if (value < 1 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be between 1 and 1000000");
        }
        return value;
    }
}
