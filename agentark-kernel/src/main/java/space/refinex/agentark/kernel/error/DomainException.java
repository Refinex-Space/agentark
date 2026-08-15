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

package space.refinex.agentark.kernel.error;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * 携带稳定错误码和不可变校验明细的领域异常，不绑定任何传输协议。
 *
 * @author refinex
 */
public final class DomainException extends RuntimeException {

    /**
     * Java 序列化兼容标识；领域契约本身不使用 Java 原生序列化。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 可稳定映射到 API 或 Event 的平台错误码。
     */
    private final DomainErrorCode errorCode;

    /**
     * 构造时防御性复制的结构化校验明细。
     */
    private final List<Violation> violations;

    /**
     * 创建包含结构化校验明细的领域异常。
     *
     * @param errorCode  稳定平台错误码
     * @param message    面向诊断的非空异常消息
     * @param violations 校验明细，不允许为 {@code null}
     * @throws NullPointerException     当错误码或校验明细为 {@code null} 时抛出
     * @throws IllegalArgumentException 当异常消息为空时抛出
     */
    public DomainException(DomainErrorCode errorCode, String message, List<Violation> violations) {
        super(requireMessage(message));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.violations =
            List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    }

    /**
     * 创建由底层异常引起且没有字段校验明细的领域异常。
     *
     * @param errorCode 稳定平台错误码
     * @param message   面向诊断的非空异常消息
     * @param cause     必须保留的原始异常
     * @throws NullPointerException     当错误码或原始异常为 {@code null} 时抛出
     * @throws IllegalArgumentException 当异常消息为空时抛出
     */
    public DomainException(DomainErrorCode errorCode, String message, Throwable cause) {
        super(requireMessage(message), Objects.requireNonNull(cause, "cause must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.violations = List.of();
    }

    /**
     * 返回稳定平台错误码。
     *
     * @return 领域错误码
     */
    public DomainErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 返回不可修改的结构化校验明细。
     *
     * @return 不可变校验明细列表
     */
    public List<Violation> violations() {
        return violations;
    }

    /**
     * 校验异常消息，避免产生无法诊断的空异常。
     *
     * @param message 待校验消息
     * @return 校验通过的原消息
     * @throws IllegalArgumentException 当消息为空时抛出
     */
    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("DomainException message must not be blank");
        }
        return message;
    }
}
