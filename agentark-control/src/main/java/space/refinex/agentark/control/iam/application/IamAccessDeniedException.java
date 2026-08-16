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

package space.refinex.agentark.control.iam.application;

import java.io.Serial;

/**
 * 表示主体已认证但不具备目标 Scope 权限的稳定授权失败。
 *
 * @author refinex
 */
public final class IamAccessDeniedException extends RuntimeException {

    /**
     * Java 序列化兼容标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建不暴露资源内部信息的授权异常。
     *
     * @param message 安全且非空的诊断消息
     */
    public IamAccessDeniedException(String message) {
        super(message);
    }
}
