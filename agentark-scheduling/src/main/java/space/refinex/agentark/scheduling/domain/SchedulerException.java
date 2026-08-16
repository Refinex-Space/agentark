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

package space.refinex.agentark.scheduling.domain;

/**
 * 表示 Scheduler 稳定错误码及安全错误消息，不携带 Payload、Credential 或 Provider 正文。
 *
 * @author refinex
 */
public final class SchedulerException extends RuntimeException {

    /**
     * 稳定错误码。
     */
    private final String code;

    /**
     * 创建 Scheduler 领域异常。
     *
     * @param code    大写下划线稳定错误码
     * @param message 不含敏感数据的诊断消息
     */
    public SchedulerException(String code, String message) {
        super(message);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException("scheduler error code is invalid");
        }
        this.code = code;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 稳定错误码
     */
    public String code() {
        return code;
    }
}
