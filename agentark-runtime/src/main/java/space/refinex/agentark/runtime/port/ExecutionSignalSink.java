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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.runtime.domain.RuntimeModels.ExecutionSignal;
import space.refinex.agentark.runtime.domain.RuntimeModels.Run;
import space.refinex.agentark.runtime.domain.RuntimeModels.Session;

/**
 * 接收 Provider Adapter 的语言中立执行信号，由 Runtime 应用层决定持久化时机。
 *
 * @author refinex
 */
@FunctionalInterface
public interface ExecutionSignalSink {

    /**
     * 接收一个已脱离供应商类型的事件。
     *
     * @param session 当前固定 Snapshot 的 Session
     * @param run     当前 Run Attempt
     * @param signal  待持久或发布的执行信号
     */
    void emit(Session session, Run run, ExecutionSignal signal);
}
