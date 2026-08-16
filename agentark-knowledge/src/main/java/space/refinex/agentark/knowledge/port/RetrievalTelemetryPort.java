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

package space.refinex.agentark.knowledge.port;

import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalTrace;

/**
 * 定义 Retrieval Trace 与原始 Usage 的真实发布 Port，禁止空实现静默吞掉事实。
 *
 * @author refinex
 */
@FunctionalInterface
public interface RetrievalTelemetryPort {

    /**
     * 发布不含查询正文和 Chunk 正文的检索 Trace。
     *
     * @param trace 检索 Trace
     */
    void record(RetrievalTrace trace);
}
