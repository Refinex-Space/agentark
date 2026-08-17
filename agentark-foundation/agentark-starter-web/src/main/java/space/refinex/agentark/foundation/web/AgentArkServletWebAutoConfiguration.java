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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.json.JsonMapper;

/**
 * 仅在 Servlet 应用中装配请求上下文过滤器和全局 ProblemDetail 异常解析器。
 *
 * @author refinex
 */
@AutoConfiguration(after = AgentArkWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "agentark.foundation.web",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentArkServletWebAutoConfiguration {

    /**
     * 只记录未知异常类型链和关联标识的安全诊断日志器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(
        AgentArkServletWebAutoConfiguration.class);

    /**
     * 创建 Servlet Web 自动配置。
     */
    public AgentArkServletWebAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建请求关联上下文过滤器。
     *
     * @param properties             Web 配置属性
     * @param requestContextFactory  请求上下文工厂
     * @param requestContextAccessor 同步上下文访问器
     * @return 每个请求建立和清理上下文的过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentArkServletRequestContextFilter agentArkServletRequestContextFilter(
        AgentArkWebProperties properties,
        RequestContextFactory requestContextFactory,
        RequestContextAccessor requestContextAccessor) {
        return new AgentArkServletRequestContextFilter(
            properties, requestContextFactory, requestContextAccessor);
    }

    /**
     * 创建面向 Servlet 的全局异常解析器，未知异常不会回显原始消息。
     *
     * @param problemDetailFactory   ProblemDetail 工厂
     * @param requestContextAccessor 当前同步请求上下文访问器
     * @param requestContextFactory  请求上下文工厂
     * @param properties             Web 配置属性
     * @param jsonMapper             Jackson 3 JSON 映射器
     * @return 可写出 application/problem+json 的异常解析器
     */
    @Bean(name = "agentArkProblemDetailExceptionResolver")
    @ConditionalOnMissingBean(name = "agentArkProblemDetailExceptionResolver")
    public HandlerExceptionResolver agentArkProblemDetailExceptionResolver(
        ProblemDetailFactory problemDetailFactory,
        RequestContextAccessor requestContextAccessor,
        RequestContextFactory requestContextFactory,
        AgentArkWebProperties properties,
        JsonMapper jsonMapper) {
        return (HttpServletRequest request,
                HttpServletResponse response,
                Object handler,
                Exception error) -> {
            RequestContext context =
                requestContextAccessor
                    .current()
                    .orElseGet(
                        () ->
                            requestContextFactory.create(
                                request.getHeader(properties.getRequestIdHeader()),
                                request.getHeader("traceparent")));
            var problem = problemDetailFactory.create(error, context);
            if (problem.getStatus() >= 500) {
                LOGGER.error(
                    "Unhandled request failure requestId={} traceId={} errorTypes={}",
                    context.requestId(), context.traceId(), errorTypes(error));
            }
            response.setStatus(problem.getStatus());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            try {
                jsonMapper.writeValue(response.getOutputStream(), problem);
                return new ModelAndView();
            } catch (java.io.IOException writeFailure) {
                throw new IllegalStateException("failed to write ProblemDetail response", writeFailure);
            }
        };
    }

    /**
     * 构造最多八层的异常类名链，不记录异常消息、SQL、参数或请求正文。
     *
     * @param error 未知请求异常
     * @return 使用箭头连接的异常类名链
     */
    private static String errorTypes(Throwable error) {
        StringBuilder types = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (!types.isEmpty()) {
                types.append("->");
            }
            types.append(current.getClass().getName());
            if (current instanceof java.sql.SQLException sqlException) {
                types.append("[sqlState=")
                    .append(sqlException.getSQLState())
                    .append(",vendorCode=")
                    .append(sqlException.getErrorCode())
                    .append(']');
            }
            current = current.getCause();
            depth++;
        }
        return types.toString();
    }
}
