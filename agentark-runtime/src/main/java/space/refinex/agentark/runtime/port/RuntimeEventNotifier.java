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

import reactor.core.publisher.Flux;
import space.refinex.agentark.kernel.id.SessionId;

/**
 * 提供可丢失的实时事件通知；Runtime Event Store 始终是唯一事实来源。
 *
 * @author refinex
 */
public interface RuntimeEventNotifier {

    /**
     * 在 Event 事务提交后发布 Session Sequence 提示。
     *
     * @param sessionId       Session 标识
     * @param sessionSequence 已提交事件序号
     */
    void publish(SessionId sessionId, long sessionSequence);

    /**
     * 订阅指定 Session 的实时提示；断线或丢失必须由持久回放修复。
     *
     * @param sessionId Session 标识
     * @return 可丢失的序号提示流
     */
    Flux<Long> subscribe(SessionId sessionId);
}
