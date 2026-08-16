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

import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;

import java.util.concurrent.CompletionStage;

/**
 * 定义 Scheduler Worker 通过受保护 Internal API 加载不可变摄取计划的 Port。
 *
 * @author refinex
 */
@FunctionalInterface
public interface IngestionPlanSource {

    /**
     * 加载不含凭据值且固定 Revision/Profile/Document 的摄取计划。
     *
     * @param requestId Control 摄取请求标识
     * @return 异步摄取计划
     */
    CompletionStage<IngestionPlan> load(IngestionRequestId requestId);
}
