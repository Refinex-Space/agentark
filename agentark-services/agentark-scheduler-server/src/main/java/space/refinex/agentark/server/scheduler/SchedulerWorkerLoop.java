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

package space.refinex.agentark.server.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import space.refinex.agentark.scheduling.application.CronTriggerService;
import space.refinex.agentark.scheduling.application.SchedulerWorker;

import java.util.Objects;

/**
 * 在显式启用时驱动 Cron Cursor 和已装配 Handler 的持久 Job，不包含 Agent 推理循环。
 *
 * @author refinex
 */
public final class SchedulerWorkerLoop {

    /**
     * 持久调度任务 Worker。
     */
    private final SchedulerWorker worker;

    /**
     * Cron Trigger 服务。
     */
    private final CronTriggerService cron;

    /**
     * Server 配置。
     */
    private final SchedulerServerProperties properties;

    /**
     * 创建 Scheduler Loop。
     *
     * @param worker     Durable Job Worker
     * @param cron       Cron Trigger 服务
     * @param properties Server 配置
     */
    public SchedulerWorkerLoop(
        SchedulerWorker worker,
        CronTriggerService cron,
        SchedulerServerProperties properties) {
        this.worker = Objects.requireNonNull(worker, "worker must not be null");
        this.cron = Objects.requireNonNull(cron, "cron must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 按固定延迟为每个已装配 Job Type 尝试领取一项工作。
     */
    @Scheduled(fixedDelayString = "${agentark.scheduler.worker-poll-delay:250ms}")
    public void poll() {
        if (!properties.workerEnabled()) {
            return;
        }
        worker.supportedTypes().forEach(worker::tick);
    }

    /**
     * 按固定延迟扫描到期 Cron Cursor，仅创建 Job。
     */
    @Scheduled(fixedDelayString = "${agentark.scheduler.cron-scan-delay:30s}")
    public void cron() {
        if (properties.workerEnabled()) {
            cron.fireDue(100);
        }
    }
}
