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

package space.refinex.agentark.scheduling.port;

/**
 * 表示版本化 Control 或 Runtime Internal Client 的脱敏失败和安全重试分类。
 *
 * @author refinex
 */
public final class InternalClientException extends RuntimeException {

    /**
     * 是否可由幂等 Handler 安全重试。
     */
    private final boolean retryable;

    /**
     * 创建内部 Client 异常。
     *
     * @param code      不含下游正文的稳定错误码
     * @param retryable 是否可安全重试
     */
    public InternalClientException(String code, boolean retryable) {
        super(code);
        this.retryable = retryable;
    }

    /**
     * 返回安全重试分类。
     *
     * @return 可重试时为 true
     */
    public boolean retryable() {
        return retryable;
    }
}
