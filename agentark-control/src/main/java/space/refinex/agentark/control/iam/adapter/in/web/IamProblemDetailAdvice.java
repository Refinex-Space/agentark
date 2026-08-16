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

package space.refinex.agentark.control.iam.adapter.in.web;

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
 * 将 IAM 应用异常映射为稳定 RFC 9457 响应，并隐藏 SQL、约束名和资源存在性细节。
 *
 * @author refinex
 */
@RestControllerAdvice(assignableTypes = IamController.class)
public final class IamProblemDetailAdvice {

    /**
     * 当前请求上下文访问器。
     */
    private final RequestContextAccessor requestContextAccessor;

    /**
     * 创建 IAM 异常映射器。
     *
     * @param requestContextAccessor 请求关联上下文
     */
    public IamProblemDetailAdvice(RequestContextAccessor requestContextAccessor) {
        this.requestContextAccessor = java.util.Objects.requireNonNull(
            requestContextAccessor, "requestContextAccessor must not be null");
    }

    /**
     * 映射应用或方法授权拒绝。
     *
     * @param exception 被隐藏详情的拒绝异常
     * @return 稳定 403 ProblemDetail
     */
    @ExceptionHandler({IamAccessDeniedException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "ARK-IAM-FORBIDDEN-00001", "没有访问权限",
            "当前主体无权执行该操作");
    }

    /**
     * 映射资源不存在且不回显内部查询条件。
     *
     * @param exception 资源不存在异常
     * @return 稳定 404 ProblemDetail
     */
    @ExceptionHandler(IamNotFoundException.class)
    public ProblemDetail notFound(IamNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ARK-IAM-NOT-FOUND-00001", "资源不存在",
            "请求的 IAM 资源不存在或不可见");
    }

    /**
     * 映射乐观锁、唯一约束与显式业务冲突。
     *
     * @param exception 冲突异常
     * @return 稳定 409 ProblemDetail
     */
    @ExceptionHandler({IamConflictException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "ARK-IAM-CONFLICT-00001", "资源状态冲突",
            "资源已存在或已经被其他请求修改");
    }

    /**
     * 映射强类型标识、枚举和 Bean Validation 输入错误。
     *
     * @param exception 输入异常
     * @return 稳定 400 ProblemDetail
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "ARK-IAM-INVALID-REQUEST-00001", "请求参数无效",
            "请求字段、标识或资源范围不符合契约");
    }

    /**
     * 构造不含内部异常信息的 ProblemDetail。
     *
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param title  中文标题
     * @param detail 安全详情
     * @return RFC 9457 错误体
     */
    private ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:agentark:error:" + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestContextAccessor.current()
            .map(context -> context.requestId()).orElseGet(() -> UUID.randomUUID().toString()));
        return problem;
    }
}
