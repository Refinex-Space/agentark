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

package space.refinex.agentark.control.release.adapter.in.web;

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
 * 将 Release 异常映射为不暴露 SQL、租户存在性和 Secret 信息的稳定 RFC 9457 响应。
 *
 * @author refinex
 */
@RestControllerAdvice(assignableTypes = ReleaseController.class)
public final class ReleaseProblemDetailAdvice {

    /**
     * 请求上下文访问器。
     */
    private final RequestContextAccessor accessor;

    /**
     * 创建 Release 异常映射器。
     *
     * @param accessor 请求上下文访问器
     */
    public ReleaseProblemDetailAdvice(RequestContextAccessor accessor) {
        this.accessor = java.util.Objects.requireNonNull(accessor, "accessor must not be null");
    }

    /**
     * 映射认证主体的授权拒绝。
     *
     * @param exception 授权拒绝异常
     * @return 不泄露资源存在性的 403 Problem Detail
     */
    @ExceptionHandler({IamAccessDeniedException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "ARK-RELEASE-FORBIDDEN-00001",
            "没有访问权限", "当前主体无权执行该发布或部署操作");
    }

    /**
     * 映射不存在或不可见资源。
     *
     * @param exception 不可见资源异常
     * @return 稳定 404 Problem Detail
     */
    @ExceptionHandler(IamNotFoundException.class)
    public ProblemDetail notFound(IamNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ARK-RELEASE-NOT-FOUND-00001",
            "资源不存在", "请求的发布资源不存在或不可见");
    }

    /**
     * 映射状态、版本或数据库约束冲突。
     *
     * @param exception 冲突异常
     * @return 不暴露约束名称的 409 Problem Detail
     */
    @ExceptionHandler({IamConflictException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "ARK-RELEASE-CONFLICT-00001",
            "资源状态冲突", "Draft、资产、Runtime 能力或 Deployment 版本不满足前置条件");
    }

    /**
     * 映射参数解析和 Bean Validation 错误。
     *
     * @param exception 请求参数异常
     * @return 稳定 400 Problem Detail
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ProblemDetail invalid(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "ARK-RELEASE-INVALID-REQUEST-00001",
            "请求参数无效", "发布、部署或 Runtime 能力声明不符合契约");
    }

    /**
     * 创建包含稳定错误码和 Request ID 的 RFC 9457 响应。
     *
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param title  中文错误标题
     * @param detail 不含内部实现的错误详情
     * @return RFC 9457 Problem Detail
     */
    private ProblemDetail problem(
        HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:agentark:error:" + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", accessor.current()
            .map(context -> context.requestId()).orElseGet(() -> UUID.randomUUID().toString()));
        return problem;
    }
}
