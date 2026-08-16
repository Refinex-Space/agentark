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

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.domain.SchedulerModels.Job;
import space.refinex.agentark.scheduling.domain.SchedulerModels.SchedulerOutbox;
import space.refinex.agentark.scheduling.domain.SchedulerModels.TriggerCursor;
import space.refinex.agentark.scheduling.domain.SchedulerModels.TriggerDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Trigger、Cursor 与 Webhook Replay Protection 的 Scheduler MySQL 事务端口。
 *
 * @author refinex
 */
public interface TriggerRepository {

    /**
     * 在同一事务中创建 Trigger、可选 Cron Cursor 和 Outbox 事实。
     *
     * @param trigger Trigger 定义
     * @param cursor  Cron Cursor；Webhook 必须为空
     * @param outbox  同事务创建事件
     */
    void insert(
        TriggerDefinition trigger,
        Optional<TriggerCursor> cursor,
        SchedulerOutbox outbox);

    /**
     * 按租户和稳定 Key 读取 Trigger。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            项目内稳定 Key
     * @return 已存在 Trigger
     */
    Optional<TriggerDefinition> findByKey(
        OrganizationId organizationId,
        ProjectId projectId,
        String key);

    /**
     * 读取 Trigger。
     *
     * @param triggerId Trigger UUIDv7
     * @return Trigger
     */
    Optional<TriggerDefinition> find(UUID triggerId);

    /**
     * 列出到期且启用的 Cron Trigger 和 Cursor。
     *
     * @param now   当前时间
     * @param limit 最大数量
     * @return 到期计划列表
     */
    List<DueTrigger> findDue(Instant now, int limit);

    /**
     * 以 Cursor 乐观锁原子插入幂等 Job 并推进下次点火时间。
     *
     * @param trigger     Trigger
     * @param cursor      读取时 Cursor
     * @param job         当前计划时间对应 Job
     * @param scheduledAt 计划点火时间
     * @param nextFireAt  下次计划时间
     * @param fireToken   当前点火 Token
     * @param outbox      同事务接单事件
     * @return 是否成功推进；并发实例已推进时为 false
     */
    boolean fire(
        TriggerDefinition trigger,
        TriggerCursor cursor,
        Job job,
        Instant scheduledAt,
        Instant nextFireAt,
        String fireToken,
        SchedulerOutbox outbox);

    /**
     * 原子消费 Webhook Nonce 并插入其幂等 Job。
     *
     * @param trigger     Webhook Trigger
     * @param nonce       外部唯一 Nonce
     * @param requestHash 请求体 SHA-256
     * @param expiresAt   Replay Protection 到期时间
     * @param job         待插入 Job
     * @param outbox      同事务接单事件
     * @return 首次消费为 true，重复 Nonce 为 false
     */
    boolean acceptWebhook(
        TriggerDefinition trigger,
        String nonce,
        String requestHash,
        Instant expiresAt,
        Job job,
        SchedulerOutbox outbox);

    /**
     * @param trigger Trigger 定义
     * @param cursor  当前 Cursor
     * @author refinex
     */
    record DueTrigger(TriggerDefinition trigger, TriggerCursor cursor) {

        /**
         * 校验到期 Trigger 与 Cursor 属于同一标识。
         */
        public DueTrigger {
            if (trigger == null || cursor == null || !trigger.id().equals(cursor.triggerId())) {
                throw new IllegalArgumentException("due trigger and cursor must match");
            }
        }
    }
}
