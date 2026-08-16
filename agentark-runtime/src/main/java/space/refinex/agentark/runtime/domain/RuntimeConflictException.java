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

package space.refinex.agentark.runtime.domain;

/**
 * 表示幂等冲突、过期 Owner、乐观锁或状态转换前置条件不成立。
 *
 * @author refinex
 */
public final class RuntimeConflictException extends RuntimeException {

    /**
     * 使用不含敏感载荷的诊断消息创建冲突异常。
     *
     * @param message 稳定边界说明
     */
    public RuntimeConflictException(String message) {
        super(message);
    }
}
