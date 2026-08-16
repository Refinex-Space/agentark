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

package space.refinex.agentark.knowledge.application;

/**
 * 表示资源不存在或因租户边界不可见，避免泄露跨租户资源是否存在。
 *
 * @author refinex
 */
public final class KnowledgeNotFoundException extends RuntimeException {

    /**
     * 创建安全的 Knowledge 资源未找到异常。
     *
     * @param message 不包含跨租户资源细节的错误信息
     */
    public KnowledgeNotFoundException(String message) {
        super(message);
    }
}
