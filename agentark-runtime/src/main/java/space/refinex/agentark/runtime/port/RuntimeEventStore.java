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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeEvent;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;

import java.time.Instant;
import java.util.List;

/**
 * 定义追加式 Runtime Event Store；SSE 只能消费这里的事实，不能替代持久化。
 *
 * @author refinex
 */
public interface RuntimeEventStore {

    /**
     * 在锁定 Session 与 Run 序列后追加 Event，序列分配和插入必须位于同一事务。
     *
     * @param eventId        全局唯一 Event 标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param sessionId      所属 Session
     * @param turnId         所属 Turn
     * @param runId          所属 Run
     * @param type           稳定事件类型
     * @param schemaVersion  Event Schema 版本
     * @param traceId        同一 Run 使用的追踪标识
     * @param payload        内联 JSON 或 ObjectRef
     * @param occurredAt     事实发生时刻
     * @param fencingToken   当前有效栅栏令牌
     * @return 已分配 Session/Run 单调序号的持久 Event
     */
    RuntimeEvent append(
        EventId eventId,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        TurnId turnId,
        RunId runId,
        String type,
        int schemaVersion,
        String traceId,
        RuntimePayload payload,
        Instant occurredAt,
        FencingToken fencingToken);

    /**
     * 按 Session 序号增量读取事实，供恢复和 SSE 消费。
     *
     * @param sessionId    Session 标识
     * @param afterSequence 排除的最后已消费序号
     * @param limit        最大返回数量
     * @return 按 sessionSequence 升序排列的 Event
     */
    List<RuntimeEvent> listAfter(SessionId sessionId, long afterSequence, int limit);
}
