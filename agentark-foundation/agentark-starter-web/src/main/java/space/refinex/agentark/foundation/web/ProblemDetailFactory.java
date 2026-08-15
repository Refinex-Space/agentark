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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import space.refinex.agentark.kernel.error.DomainException;
import space.refinex.agentark.kernel.error.Violation;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

/**
 * 将领域异常和未知异常映射为 RFC 9457 ProblemDetail，并隐藏内部异常与敏感载荷。
 *
 * @author refinex
 */
public final class ProblemDetailFactory {

    /**
     * 未知内部错误使用的稳定平台错误码。
     */
    private static final String INTERNAL_ERROR_CODE = "ARK-WEB-INTERNAL-00001";

    /**
     * 创建无共享状态的 ProblemDetail 工厂。
     */
    public ProblemDetailFactory() {
        // 显式构造器用于说明工厂没有可变配置。
    }

    /**
     * 将异常转换为安全的 RFC 9457 错误对象。
     *
     * @param error   请求处理异常
     * @param context 当前请求上下文
     * @return 不包含堆栈、Secret 或 Provider 原始对象的错误详情
     */
    public ProblemDetail create(Throwable error, RequestContext context) {
        if (error instanceof DomainException domainException) {
            return domainProblem(domainException, context);
        }
        if (error instanceof ConstraintViolationException validationException) {
            return validationProblem(validationException, context);
        }
        if (error instanceof IllegalArgumentException) {
            return problem(
                HttpStatus.BAD_REQUEST,
                "ARK-WEB-INVALID_REQUEST-00001",
                "请求参数不合法",
                "请求参数未通过校验",
                List.of(),
                context);
        }
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            INTERNAL_ERROR_CODE,
            "服务器内部错误",
            "请求处理失败，请使用关联标识联系管理员",
            List.of(),
            context);
    }

    /**
     * 将领域异常映射为不可泄露内部实现的 422 错误。
     *
     * @param error   领域异常
     * @param context 请求上下文
     * @return 领域错误详情
     */
    private ProblemDetail domainProblem(DomainException error, RequestContext context) {
        return problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            error.errorCode().value(),
            "业务规则校验失败",
            error.getMessage(),
            error.violations(),
            context);
    }

    /**
     * 将 Bean Validation 约束错误转换为字段稳定且排序确定的 400 错误。
     *
     * @param error   Bean Validation 约束异常
     * @param context 请求上下文
     * @return 不包含非法值本身的结构化校验错误
     */
    private ProblemDetail validationProblem(
        ConstraintViolationException error, RequestContext context) {
        List<Violation> violations =
            error.getConstraintViolations().stream()
                .map(this::violationOf)
                .sorted(Comparator.comparing(Violation::path))
                .toList();
        return problem(
            HttpStatus.BAD_REQUEST,
            "ARK-WEB-VALIDATION-00001",
            "请求参数不合法",
            "请求参数未通过约束校验",
            violations,
            context);
    }

    /**
     * 将 Jakarta 约束转换为不包含 Invalid Value 的 AgentArk Violation。
     *
     * @param constraint Jakarta 约束错误
     * @return 长度受限且路径非空的校验明细
     */
    private Violation violationOf(ConstraintViolation<?> constraint) {
        String rawPath = constraint.getPropertyPath().toString();
        String path = rawPath.isBlank() ? "$" : rawPath.substring(0, Math.min(rawPath.length(), 512));
        String rawMessage = constraint.getMessage();
        String message =
            rawMessage == null || rawMessage.isBlank()
                ? "约束校验失败"
                : rawMessage.substring(0, Math.min(rawMessage.length(), 2048));
        return new Violation(path, "CONSTRAINT_VIOLATION", message);
    }

    /**
     * 构造字段稳定的 ProblemDetail。
     *
     * @param status     HTTP 状态
     * @param code       稳定错误码
     * @param title      错误标题
     * @param detail     安全错误详情
     * @param violations 结构化校验明细
     * @param context    请求上下文
     * @return RFC 9457 错误对象
     */
    private ProblemDetail problem(
        HttpStatus status,
        String code,
        String title,
        String detail,
        List<Violation> violations,
        RequestContext context) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:agentark:error:" + code));
        problem.setProperty("code", code);
        problem.setProperty("requestId", context.requestId());
        problem.setProperty("traceId", context.traceId());
        if (!violations.isEmpty()) {
            problem.setProperty("violations", violations);
        }
        return problem;
    }
}
