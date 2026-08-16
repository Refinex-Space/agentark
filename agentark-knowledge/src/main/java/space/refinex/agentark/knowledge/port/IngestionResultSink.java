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

import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;

import java.util.concurrent.CompletionStage;

/**
 * 定义 Worker 通过幂等 Internal Command 提交摄取结果的 Port，禁止直接写 Control Schema。
 *
 * @author refinex
 */
@FunctionalInterface
public interface IngestionResultSink {

    /**
     * 提交当前 Attempt 结果；相同幂等键和内容必须返回同一持久结果。
     *
     * @param result 待提交结果
     * @return Control 已接受的持久结果
     */
    CompletionStage<IngestionResult> submit(IngestionResult result);
}
