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
 * 表示唯一约束、状态前置条件或乐观锁导致的稳定资源冲突。
 *
 * @author refinex
 */
public final class IamConflictException extends RuntimeException {

    /**
     * Java 序列化兼容标识。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建安全的冲突异常。
     *
     * @param message 不含底层 SQL 或敏感数据的消息
     */
    public IamConflictException(String message) {
        super(message);
    }
}
