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

package space.refinex.agentark.control.iam.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * 在 MVC 异常解析器之前写出稳定的认证与授权 RFC 9457 响应。
 *
 * @author refinex
 */
public final class IamSecurityProblemWriter {

    /**
     * 统一 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建安全错误写出器。
     *
     * @param jsonMapper 应用统一 JSON 映射器
     */
    public IamSecurityProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 写出不暴露认证失败原因的 401 响应。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 响应写入失败时抛出
     */
    public void unauthorized(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "ARK-IAM-UNAUTHORIZED-00001",
            "身份认证失败", "请求需要有效的 Bearer Token 或 API Key");
    }

    /**
     * 写出不暴露目标资源内部信息的 403 响应。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @throws IOException 响应写入失败时抛出
     */
    public void forbidden(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "ARK-IAM-FORBIDDEN-00001",
            "没有访问权限", "当前主体无权执行该操作");
    }

    /**
     * 构造并写出安全 ProblemDetail。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param status   HTTP 状态
     * @param code     稳定错误码
     * @param title    中文标题
     * @param detail   安全详情
     * @throws IOException 响应写入失败时抛出
     */
    private void write(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        String code,
        String title,
        String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:agentark:error:" + code));
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId(request));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    /**
     * 只接受长度和字符受控的请求标识，否则生成新的非敏感关联值。
     *
     * @param request 当前请求
     * @return 安全请求标识
     */
    private String requestId(HttpServletRequest request) {
        String candidate = request.getHeader("X-Request-Id");
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
