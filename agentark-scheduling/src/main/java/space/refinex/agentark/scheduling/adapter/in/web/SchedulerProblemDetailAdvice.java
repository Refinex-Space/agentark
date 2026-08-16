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

package space.refinex.agentark.scheduling.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import space.refinex.agentark.scheduling.domain.SchedulerException;

import java.net.URI;

/**
 * 将 Scheduler 稳定异常映射为 RFC 9457 ProblemDetail，不泄漏 Payload、Secret 或响应正文。
 *
 * @author refinex
 */
@RestControllerAdvice(
    assignableTypes = {SchedulerController.class, SchedulerInternalController.class})
public final class SchedulerProblemDetailAdvice {

    /**
     * 映射 Scheduler 领域错误。
     *
     * @param exception Scheduler 异常
     * @return 稳定 ProblemDetail
     */
    @ExceptionHandler(SchedulerException.class)
    public ResponseEntity<ProblemDetail> scheduler(SchedulerException exception) {
        HttpStatus status = switch (exception.code()) {
            case "JOB_NOT_FOUND", "WEBHOOK_TRIGGER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SCHEDULER_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "SCHEDULER_AUTHENTICATION_REQUIRED", "WEBHOOK_SIGNATURE_INVALID",
                 "WEBHOOK_TIMESTAMP_INVALID", "WEBHOOK_TIMESTAMP_EXPIRED" -> HttpStatus.UNAUTHORIZED;
            case "WEBHOOK_REPLAYED", "IDEMPOTENCY_CONFLICT", "STALE_FENCING_TOKEN",
                 "JOB_CANCEL_CONFLICT", "JOB_REDRIVE_CONFLICT", "DEAD_LETTER_NOT_OPEN" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status, exception.code(), exception.getMessage());
    }

    /**
     * 映射 UUID、请求字段和 Bean Validation 错误。
     *
     * @param exception 参数异常
     * @return 400 ProblemDetail
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception exception) {
        return problem(
            HttpStatus.BAD_REQUEST, "SCHEDULER_REQUEST_INVALID",
            "scheduler request does not satisfy the contract");
    }

    /**
     * 构造稳定 ProblemDetail。
     *
     * @param status HTTP 状态
     * @param code   稳定错误码
     * @param detail 安全摘要
     * @return ProblemDetail 响应
     */
    private ResponseEntity<ProblemDetail> problem(
        HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Scheduler operation failed");
        problem.setType(URI.create(
            "https://agentark.dev/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", "ARK-SCHEDULER-" + code);
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
