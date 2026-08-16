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

package space.refinex.agentark.knowledge.adapter.out.vector.qdrant;

/**
 * 表示未暴露 Provider 响应正文的稳定 Qdrant 不可用异常。
 *
 * @author refinex
 */
public final class QdrantUnavailableException extends RuntimeException {

    /**
     * 创建 Qdrant 不可用异常。
     *
     * @param message 安全上下文
     * @param cause   原始异常
     */
    public QdrantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
