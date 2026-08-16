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

package space.refinex.agentark.server.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import space.refinex.agentark.runtime.application.RuntimeWorker;

import java.util.Objects;

/**
 * 以有界固定延迟轮询持久 Work Queue，并记录不含业务载荷的失败上下文。
 *
 * @author refinex
 */
public final class RuntimeWorkerScheduler {

    /**
     * Worker 调度诊断日志。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeWorkerScheduler.class);

    /**
     * 单项 Runtime Worker。
     */
    private final RuntimeWorker worker;

    /**
     * Runtime 实例生命周期，用于排空时阻止领取新任务。
     */
    private final RuntimeInstanceLifecycle instanceLifecycle;

    /**
     * 创建 Worker 调度器。
     *
     * @param worker            Runtime Worker
     * @param instanceLifecycle Runtime 实例生命周期
     */
    public RuntimeWorkerScheduler(
        RuntimeWorker worker, RuntimeInstanceLifecycle instanceLifecycle) {
        this.worker = Objects.requireNonNull(worker, "worker must not be null");
        this.instanceLifecycle = Objects.requireNonNull(
            instanceLifecycle, "instanceLifecycle must not be null");
    }

    /**
     * 轮询并最多处理一个 Work Item，避免单次调度无限占用线程。
     */
    @Scheduled(fixedDelayString = "${agentark.runtime.worker-poll-delay:250ms}")
    public void poll() {
        if (!instanceLifecycle.acceptingWork()) {
            return;
        }
        try {
            worker.runOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn("Runtime worker iteration failed", exception);
        }
    }
}
