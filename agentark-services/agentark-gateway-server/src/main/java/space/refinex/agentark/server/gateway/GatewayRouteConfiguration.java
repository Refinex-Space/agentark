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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * 声明 Gateway 的固定公共路由表、请求体上限和逐路由超时边界。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayRouteConfiguration {

    /**
     * 创建无状态 Gateway 路由表；内部路径始终在边缘终止，不转发到任何平面。
     *
     * @param builder    Gateway 路由构建器
     * @param properties Gateway 受控配置
     * @return 固定优先级路由表
     */
    @Bean
    public RouteLocator gatewayRouteLocator(
        RouteLocatorBuilder builder, GatewayProperties properties) {
        int connectTimeoutMillis = Math.toIntExact(properties.getConnectTimeout().toMillis());
        long responseTimeoutMillis = properties.getResponseTimeout().toMillis();
        return builder.routes()
            .route("internal-path-reject", route -> route.order(-100)
                .path("/internal", "/internal/**")
                .filters(filters -> filters.setStatus(HttpStatus.NOT_FOUND))
                .uri("no://op"))
            .route("runtime-event-stream", route -> route.order(-20)
                .path("/api/v1/runtime/runs/*/events:stream")
                .filters(filters -> filters
                    .setRequestSize(properties.getMaxRequestSize())
                    .setResponseHeader("Cache-Control", "no-store")
                    .setResponseHeader("X-Accel-Buffering", "no"))
                .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMillis)
                .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, -1L)
                .uri(properties.getRuntimeBaseUrl()))
            .route("runtime-public", route -> route.order(0)
                .path("/api/v1/runtime/**")
                .filters(filters -> filters.setRequestSize(properties.getMaxRequestSize()))
                .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMillis)
                .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMillis)
                .uri(properties.getRuntimeBaseUrl()))
            .route("scheduler-webhook", route -> route.order(0)
                .method(HttpMethod.POST)
                .and()
                .path("/api/v1/scheduler/webhooks/**")
                .filters(filters -> filters.setRequestSize(properties.getWebhookMaxRequestSize()))
                .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMillis)
                .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMillis)
                .uri(properties.getSchedulerBaseUrl()))
            .route("scheduler-public", route -> route.order(10)
                .path("/api/v1/scheduler/**")
                .filters(filters -> filters.setRequestSize(properties.getMaxRequestSize()))
                .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMillis)
                .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMillis)
                .uri(properties.getSchedulerBaseUrl()))
            .route("control-public", route -> route.order(20)
                .path("/api/v1/**")
                .filters(filters -> filters.setRequestSize(properties.getMaxRequestSize()))
                .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMillis)
                .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMillis)
                .uri(properties.getControlBaseUrl()))
            .build();
    }
}
