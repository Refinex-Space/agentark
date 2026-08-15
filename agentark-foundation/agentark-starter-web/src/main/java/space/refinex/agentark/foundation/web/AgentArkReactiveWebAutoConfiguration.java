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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * 仅在 Reactive 应用中通过 Reactor Context 传播请求上下文并映射全局错误。
 *
 * @author refinex
 */
@AutoConfiguration(after = AgentArkWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "agentark.foundation.web",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentArkReactiveWebAutoConfiguration {

    /**
     * Reactive Exchange 中保存请求上下文的稳定属性键。
     */
    public static final String REQUEST_CONTEXT_ATTRIBUTE =
        "space.refinex.agentark.foundation.web.request-context";

    /**
     * 创建 Reactive Web 自动配置。
     */
    public AgentArkReactiveWebAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建将请求上下文写入 Exchange 属性和 Reactor Context 的过滤器。
     *
     * @param properties            Web 配置属性
     * @param requestContextFactory 请求上下文工厂
     * @return Reactive 请求上下文过滤器
     */
    @Bean(name = "agentArkReactiveRequestContextFilter")
    @ConditionalOnMissingBean(name = "agentArkReactiveRequestContextFilter")
    public WebFilter agentArkReactiveRequestContextFilter(
        AgentArkWebProperties properties, RequestContextFactory requestContextFactory) {
        return (exchange, chain) -> {
            RequestContext context =
                requestContextFactory.create(
                    exchange.getRequest().getHeaders().getFirst(properties.getRequestIdHeader()),
                    exchange.getRequest().getHeaders().getFirst("traceparent"));
            exchange.getAttributes().put(REQUEST_CONTEXT_ATTRIBUTE, context);
            exchange.getResponse().getHeaders().set(properties.getRequestIdHeader(), context.requestId());
            return chain
                .filter(exchange)
                .contextWrite(reactorContext -> reactorContext.put(RequestContext.class, context));
        };
    }

    /**
     * 创建 Reactive 全局异常处理器，响应只包含安全 ProblemDetail 字段。
     *
     * @param problemDetailFactory  ProblemDetail 工厂
     * @param requestContextFactory 请求上下文工厂
     * @param jsonMapper            Jackson 3 JSON 映射器
     * @return Reactive 全局异常处理器
     */
    @Bean(name = "agentArkReactiveProblemDetailExceptionHandler")
    @ConditionalOnMissingBean(name = "agentArkReactiveProblemDetailExceptionHandler")
    public WebExceptionHandler agentArkReactiveProblemDetailExceptionHandler(
        ProblemDetailFactory problemDetailFactory,
        RequestContextFactory requestContextFactory,
        JsonMapper jsonMapper) {
        return (exchange, error) -> {
            RequestContext context = exchange.getAttribute(REQUEST_CONTEXT_ATTRIBUTE);
            if (context == null) {
                context = requestContextFactory.create(null, null);
            }
            var problem = problemDetailFactory.create(error, context);
            exchange
                .getResponse()
                .setStatusCode(org.springframework.http.HttpStatusCode.valueOf(problem.getStatus()));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            try {
                DataBuffer buffer =
                    exchange.getResponse().bufferFactory().wrap(jsonMapper.writeValueAsBytes(problem));
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (tools.jackson.core.JacksonException serializationFailure) {
                return Mono.error(serializationFailure);
            }
        };
    }
}
