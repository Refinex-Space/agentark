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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.knowledge.port.IngestionPlanSource;
import space.refinex.agentark.knowledge.port.IngestionResultSink;

/**
 * 聚合 Scheduler 访问 Control 的版本化 Knowledge 计划与结果命令，不暴露 Control Entity。
 *
 * @author refinex
 */
public interface ControlInternalClient extends IngestionPlanSource, IngestionResultSink {
}
