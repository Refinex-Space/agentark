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

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.web.AgentArkReactiveWebAutoConfiguration;
import space.refinex.agentark.foundation.web.RequestContext;

import java.util.List;

/**
 * 移除客户端伪造的派生身份 Header，并向下游传播受控请求标识。
 *
 * @author refinex
 */
public final class GatewayHeaderSanitizationFilter implements GlobalFilter, Ordered {

    /**
     * 客户端不得声明的派生身份 Header；认证凭据和租户选择意图不在此列表中。
     */
    private static final List<String> UNTRUSTED_IDENTITY_HEADERS = List.of(
        "X-AgentArk-Principal",
        "X-AgentArk-Principal-Type",
        "X-AgentArk-Service-Id",
        "X-AgentArk-Authorities",
        "X-AgentArk-Authenticated-Organization-Id",
        "X-AgentArk-Authenticated-Project-Id",
        "X-AgentArk-Authenticated-Environment-Id",
        "X-Forwarded-Client-Cert");

    /**
     * 创建无状态 Header 清理过滤器。
     */
    public GatewayHeaderSanitizationFilter() {
    }

    /**
     * 删除所有不可信身份 Header，并使用 Foundation 已校验的请求标识覆盖客户端值。
     *
     * @param exchange 当前请求交换
     * @param chain    后续 Gateway 过滤链
     * @return 过滤完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RequestContext context = exchange.getAttribute(
            AgentArkReactiveWebAutoConfiguration.REQUEST_CONTEXT_ATTRIBUTE);
        var mutatedRequest = exchange.getRequest().mutate().headers(headers -> {
            UNTRUSTED_IDENTITY_HEADERS.forEach(headers::remove);
            if (context != null) {
                headers.set("X-Request-Id", context.requestId());
            }
        }).build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 在下游代理前执行，并在 Foundation 请求上下文过滤器之后读取上下文。
     *
     * @return 高优先级过滤顺序
     */
    @Override
    public int getOrder() {
        return -90;
    }
}
