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

package space.refinex.agentark.runtime.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import space.refinex.agentark.runtime.domain.RuntimeAccessDeniedException;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;

import java.net.URI;

/**
 * 将 Runtime 稳定异常映射为 RFC 9457 ProblemDetail，不返回栈、Secret 或业务载荷。
 *
 * @author refinex
 */
@RestControllerAdvice(assignableTypes = RuntimeController.class)
public final class RuntimeProblemDetailAdvice {

    /**
     * 映射跨租户隐藏后的资源不存在。
     *
     * @param exception Runtime 不存在异常
     * @return 404 ProblemDetail
     */
    @ExceptionHandler(RuntimeNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(RuntimeNotFoundException exception) {
        return problem(
            HttpStatus.NOT_FOUND, "ARK-RUNTIME-NOT_FOUND-00001",
            "Runtime resource is not available",
            exception.getMessage());
    }

    /**
     * 映射已认证但权限不足。
     *
     * @param exception Runtime 拒绝异常
     * @return 403 ProblemDetail
     */
    @ExceptionHandler(RuntimeAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(RuntimeAccessDeniedException exception) {
        return problem(
            HttpStatus.FORBIDDEN, "ARK-RUNTIME-FORBIDDEN-00001",
            "Runtime operation is forbidden",
            exception.getMessage());
    }

    /**
     * 映射幂等、乐观锁、状态或 Fencing 冲突。
     *
     * @param exception Runtime 冲突异常
     * @return 409 ProblemDetail
     */
    @ExceptionHandler(RuntimeConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(RuntimeConflictException exception) {
        return problem(
            HttpStatus.CONFLICT, "ARK-RUNTIME-CONFLICT-00001", "Runtime state conflicts",
            exception.getMessage());
    }

    /**
     * 映射请求字段、UUIDv7、游标和状态枚举校验失败。
     *
     * @param exception 参数或 Bean Validation 异常
     * @return 400 ProblemDetail
     */
    @ExceptionHandler({IllegalArgumentException.class, WebExchangeBindException.class})
    public ResponseEntity<ProblemDetail> invalidRequest(Exception exception) {
        return problem(
            HttpStatus.BAD_REQUEST, "ARK-RUNTIME-INVALID_REQUEST-00001",
            "Runtime request is invalid",
            "Request fields do not satisfy the runtime contract");
    }

    /**
     * 构造稳定 ProblemDetail 响应。
     *
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param title  稳定标题
     * @param detail 不含敏感信息的摘要
     * @return ProblemDetail ResponseEntity
     */
    private ResponseEntity<ProblemDetail> problem(
        HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://agentark.dev/problems/" + code.toLowerCase()));
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }
}
