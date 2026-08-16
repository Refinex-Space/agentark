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

package space.refinex.agentark.knowledge.adapter.in.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import space.refinex.agentark.knowledge.application.KnowledgeConflictException;
import space.refinex.agentark.knowledge.application.KnowledgeNotFoundException;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * 将 Knowledge 异常映射为不泄露 SQL、跨租户存在性、文档原文或 Secret 的 RFC 9457 响应。
 *
 * @author refinex
 */
@RestControllerAdvice(assignableTypes = {
    KnowledgeController.class, KnowledgeIngestionInternalController.class
})
public final class KnowledgeProblemDetailAdvice {

    /**
     * 请求关联上下文访问器。
     */
    private final RequestContextAccessor requestContextAccessor;

    /**
     * 创建 Knowledge ProblemDetail 映射器。
     *
     * @param requestContextAccessor 请求关联上下文访问器
     */
    public KnowledgeProblemDetailAdvice(RequestContextAccessor requestContextAccessor) {
        this.requestContextAccessor = Objects.requireNonNull(
            requestContextAccessor, "requestContextAccessor must not be null");
    }

    /**
     * @param exception 授权拒绝
     * @return 403 ProblemDetail
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(RuntimeException exception) {
        return problem(HttpStatus.FORBIDDEN, "ARK-KNOWLEDGE-FORBIDDEN-00001", "没有访问权限",
            "当前主体无权访问该 Knowledge 资源");
    }

    /**
     * @param exception 隐藏资源存在性的未找到异常
     * @return 404 ProblemDetail
     */
    @ExceptionHandler(KnowledgeNotFoundException.class)
    public ProblemDetail notFound(KnowledgeNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ARK-KNOWLEDGE-NOT-FOUND-00001", "资源不存在",
            "请求的 Knowledge 资源不存在或不可见");
    }

    /**
     * @param exception 状态机、唯一约束或对象存储冲突
     * @return 409 ProblemDetail
     */
    @ExceptionHandler({KnowledgeConflictException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "ARK-KNOWLEDGE-CONFLICT-00001", "资源状态冲突",
            "资源状态、版本、完整性或外部对象不满足请求前置条件");
    }

    /**
     * @param exception 输入参数异常
     * @return 400 ProblemDetail
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ProblemDetail invalid(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "ARK-KNOWLEDGE-INVALID-REQUEST-00001", "请求参数无效",
            "Knowledge 标识、元数据、Profile、Revision 或文件参数不符合契约");
    }

    /**
     * 构造带稳定错误码和请求标识的 RFC 9457 响应。
     *
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param title  中文标题
     * @param detail 安全详情
     * @return ProblemDetail
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
