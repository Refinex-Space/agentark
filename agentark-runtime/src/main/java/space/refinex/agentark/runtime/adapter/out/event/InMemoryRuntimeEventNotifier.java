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

package space.refinex.agentark.runtime.adapter.out.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.runtime.port.RuntimeEventNotifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用进程内有界广播发送 Event 提示；丢失的提示由持久 Event Store 轮询修复。
 *
 * @author refinex
 */
public final class InMemoryRuntimeEventNotifier implements RuntimeEventNotifier {

    /**
     * 每个 Session 的有界多播 Sink，避免慢订阅者无限占用内存。
     */
    private final Map<SessionId, Sinks.Many<Long>> sinks = new ConcurrentHashMap<>();

    /**
     * 发布已提交 Event 的 Session Sequence 提示。
     *
     * @param sessionId       Session 标识
     * @param sessionSequence 已提交事件序号
     */
    @Override
    public void publish(SessionId sessionId, long sessionSequence) {
        sink(sessionId).tryEmitNext(sessionSequence);
    }

    /**
     * 订阅 Session 的实时提示；背压溢出时断开慢消费者，由其携带游标重连。
     *
     * @param sessionId Session 标识
     * @return 可丢失提示流
     */
    @Override
    public Flux<Long> subscribe(SessionId sessionId) {
        return sink(sessionId).asFlux();
    }

    /**
     * 获取或创建单个 Session 的广播 Sink。
     *
     * @param sessionId Session 标识
     * @return 有界广播 Sink
     */
    private Sinks.Many<Long> sink(SessionId sessionId) {
        return sinks.computeIfAbsent(sessionId, ignored ->
            Sinks.many().multicast().onBackpressureBuffer(256, false));
    }
}
