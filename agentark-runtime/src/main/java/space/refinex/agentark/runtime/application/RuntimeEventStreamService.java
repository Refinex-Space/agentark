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

package space.refinex.agentark.runtime.application;

import reactor.core.publisher.Flux;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeEvent;
import space.refinex.agentark.runtime.port.RuntimeEventNotifier;
import space.refinex.agentark.runtime.port.RuntimeEventStore;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 先从 Event Store 回放，再以通知加轮询追平实时事件，并对慢消费者实施有界背压。
 *
 * @author refinex
 */
public final class RuntimeEventStreamService {

    /**
     * 单次回放最大 Event 数量。
     */
    private static final int BATCH_SIZE = 200;

    /**
     * 跨实例通知丢失时的修复轮询周期。
     */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    /**
     * 持久 Event Store。
     */
    private final RuntimeEventStore eventStore;

    /**
     * 可丢失实时提示。
     */
    private final RuntimeEventNotifier notifier;

    /**
     * 创建 Runtime Event Stream 服务。
     *
     * @param eventStore 持久 Event Store
     * @param notifier   实时提示端口
     */
    public RuntimeEventStreamService(
        RuntimeEventStore eventStore, RuntimeEventNotifier notifier) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
    }

    /**
     * 从 Last-Event-ID 指定的 Session Sequence 之后回放并持续追平。
     *
     * @param runId         Run 标识
     * @param afterSequence 已消费 Session Sequence
     * @return 有序、不重复 Event 流
     */
    public Flux<RuntimeEvent> stream(RunId runId, long afterSequence) {
        if (afterSequence < 0) {
            throw new IllegalArgumentException("Last-Event-ID must be a non-negative sequence");
        }
        return Flux.defer(() -> {
            AtomicLong cursor = new AtomicLong(afterSequence);
            Flux<Long> triggers = Flux.merge(
                Flux.interval(Duration.ZERO, POLL_INTERVAL),
                notifier.subscribe(sessionId(runId)));
            return triggers.concatMap(ignored -> load(runId, cursor), 1)
                .onBackpressureBuffer(256,
                    ignored -> {
                    },
                    reactor.core.publisher.BufferOverflowStrategy.ERROR);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 读取游标之后的一批 Event，并原子推进本订阅游标。
     *
     * @param runId  Run 标识
     * @param cursor 当前游标
     * @return 本批 Event
     */
    private Flux<RuntimeEvent> load(RunId runId, AtomicLong cursor) {
        return Flux.defer(() -> {
            List<RuntimeEvent> events = eventStore.listRunAfter(
                runId, cursor.get(), BATCH_SIZE);
            if (!events.isEmpty()) {
                cursor.set(events.getLast().sessionSequence());
            }
            return Flux.fromIterable(events);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 从首个持久事件解析 Session 的做法会产生额外查询，因此通知订阅使用稳定 Run 派生通道，
     * 轮询仍是跨实例唯一正确性来源。
     *
     * @param runId Run 标识
     * @return 仅用于本地提示隔离的派生 Session 标识
     */
    private space.refinex.agentark.kernel.id.SessionId sessionId(RunId runId) {
        List<RuntimeEvent> first = eventStore.listRunAfter(runId, 0, 1);
        if (first.isEmpty()) {
            throw new IllegalArgumentException("run has no persistent accepted event");
        }
        return first.getFirst().sessionId();
    }
}
