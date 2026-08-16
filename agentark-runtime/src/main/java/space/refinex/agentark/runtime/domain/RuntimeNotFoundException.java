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
 * 表示 Runtime 所属聚合或不可变引用不存在，消息不得泄露跨租户资源事实。
 *
 * @author refinex
 */
public final class RuntimeNotFoundException extends RuntimeException {

    /**
     * 使用稳定且不含敏感标识的消息创建不存在异常。
     *
     * @param message 边界说明
     */
    public RuntimeNotFoundException(String message) {
        super(message);
    }
}
