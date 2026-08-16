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

package space.refinex.agentark.runtime.provider.agentscope.error;

import java.util.Objects;

/**
 * 承载稳定错误码且不夹带 Secret 或隐藏推理内容的 Provider 异常。
 *
 * @author refinex
 */
public final class AgentScopeProviderException extends RuntimeException {

    /**
     * 语言中立稳定错误码。
     */
    private final ProviderErrorCode errorCode;

    /**
     * @param errorCode 稳定错误码
     * @param message   不含敏感信息的诊断摘要
     */
    public AgentScopeProviderException(ProviderErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    /**
     * @param errorCode 稳定错误码
     * @param message   不含敏感信息的诊断摘要
     * @param cause     原始异常
     */
    public AgentScopeProviderException(
        ProviderErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    /**
     * 返回 Runtime 可稳定持久化的错误码。
     *
     * @return Provider 错误码
     */
    public ProviderErrorCode errorCode() {
        return errorCode;
    }
}
