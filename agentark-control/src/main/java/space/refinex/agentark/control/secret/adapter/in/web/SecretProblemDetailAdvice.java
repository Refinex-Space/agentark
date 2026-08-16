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

package space.refinex.agentark.control.secret.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.IamNotFoundException;
import space.refinex.agentark.foundation.web.RequestContextAccessor;

import java.net.URI;
import java.util.UUID;

/**
 * 将 Secret 元数据异常映射为不暴露 Provider 内部信息和值的稳定 RFC 9457 响应。
 *
 * @author refinex
 */
@RestControllerAdvice(assignableTypes = SecretController.class)
public final class SecretProblemDetailAdvice {

    /**
     * 请求上下文访问器。
     */
    private final RequestContextAccessor requestContextAccessor;

    /**
     * @param requestContextAccessor 请求关联上下文
     */
    public SecretProblemDetailAdvice(RequestContextAccessor requestContextAccessor) {
        this.requestContextAccessor = java.util.Objects.requireNonNull(
            requestContextAccessor, "requestContextAccessor must not be null");
    }

    /**
     * @param exception 授权拒绝
     * @return 403 ProblemDetail
     */
    @ExceptionHandler({IamAccessDeniedException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "ARK-SECRET-FORBIDDEN-00001", "没有访问权限",
            "当前主体无权访问该 Secret 元数据");
    }

    /**
     * @param exception 隐藏资源存在性的未找到异常
     * @return 404 ProblemDetail
     */
    @ExceptionHandler(IamNotFoundException.class)
    public ProblemDetail notFound(IamNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ARK-SECRET-NOT-FOUND-00001", "资源不存在",
            "请求的 Secret 元数据或绑定不存在或不可见");
    }

    /**
     * @param exception 冲突异常
     * @return 409 ProblemDetail
     */
    @ExceptionHandler({IamConflictException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "ARK-SECRET-CONFLICT-00001", "资源状态冲突",
            "Secret 元数据或环境绑定不满足请求前置条件");
    }

    /**
     * @param exception 输入异常
     * @return 400 ProblemDetail
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ProblemDetail invalid(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "ARK-SECRET-INVALID-REQUEST-00001", "请求参数无效",
            "Secret Provider、定位、Scope、标识或游标不符合契约");
    }

    /**
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param title  中文标题
     * @param detail 安全详情
     * @return RFC 9457 响应
     */
    private ProblemDetail problem(
        HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:agentark:error:" + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestContextAccessor.current()
            .map(context -> context.requestId()).orElseGet(() -> UUID.randomUUID().toString()));
        return problem;
    }
}

