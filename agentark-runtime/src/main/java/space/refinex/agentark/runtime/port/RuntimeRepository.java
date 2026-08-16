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
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

import java.time.Instant;
import java.util.Optional;

/**
 * 定义 Session、Turn、Run、幂等记录和 Runtime Outbox 的聚合持久化端口。
 *
 * @author refinex
 */
public interface RuntimeRepository {

    /**
     * 在同一本地事务中创建固定 Snapshot 的 Session 与完成态幂等记录。
     *
     * @param session     新 Session
     * @param idempotency 完成态幂等记录
     */
    void insertSession(Session session, IdempotencyRecord idempotency);

    /**
     * 读取 Session。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    Optional<Session> findSession(SessionId sessionId);

    /**
     * 在 Session 行锁保护下分配下一个 Turn 序号。
     *
     * @param sessionId Session 标识
     * @return 从一开始单调递增的序号
     */
    long nextTurnSequence(SessionId sessionId);

    /**
     * 在同一本地事务中创建 Turn、首个 Run、Work Item、接受 Event、Outbox 和幂等结果。
     *
     * @param turn        新 Turn
     * @param run         首个 Run Attempt
     * @param workItem    持久 Work Item
     * @param eventId     接受 Event 全局标识
     * @param occurredAt  接受事实发生时刻
     * @param outbox      Runtime Outbox
     * @param idempotency 完成态幂等记录
     */
    RuntimeEvent insertAcceptedTurn(
        Turn turn,
        Run run,
        RuntimeWorkItem workItem,
        EventId eventId,
        Instant occurredAt,
        RuntimeOutboxEvent outbox,
        IdempotencyRecord idempotency);

    /**
     * 对 FAILED/TIMED_OUT Turn 追加新 Run Attempt 和 Work Item，不覆盖旧 Run。
     *
     * @param turn        当前 Turn
     * @param run         新 Run Attempt
     * @param workItem    新 Work Item
     * @param outbox      重试 Outbox
     */
    void insertRetryRun(
        Turn turn, Run run, RuntimeWorkItem workItem, RuntimeOutboxEvent outbox);

    /**
     * 读取 Turn。
     *
     * @param turnId Turn 标识
     * @return Turn
     */
    Optional<Turn> findTurn(TurnId turnId);

    /**
     * 读取 Run。
     *
     * @param runId Run 标识
     * @return Run
     */
    Optional<Run> findRun(RunId runId);

    /**
     * 使用当前 Fencing Token 原子转换 Run 状态。
     *
     * @param runId        Run 标识
     * @param current      预期当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @param errorCode    失败错误码
     * @return 实际更新行数
     */
    int transitionRun(
        RunId runId,
        RunStatus current,
        RunStatus target,
        FencingToken fencingToken,
        Instant occurredAt,
        Optional<String> errorCode);

    /**
     * 使用当前 Fencing Token 和乐观锁原子转换 Turn 状态。
     *
     * @param turnId       Turn 标识
     * @param current      预期当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @return 实际更新行数
     */
    int transitionTurn(
        TurnId turnId,
        TurnStatus current,
        TurnStatus target,
        FencingToken fencingToken,
        Instant occurredAt);

    /**
     * 将新 Claim 的递增令牌同步到 Run 与 Turn，旧令牌不能倒退。
     *
     * @param runId        Run 标识
     * @param turnId       Turn 标识
     * @param fencingToken 新令牌
     */
    void assignFencingToken(RunId runId, TurnId turnId, FencingToken fencingToken);

    /**
     * 追加 Runtime Outbox 事实。
     *
     * @param outboxEvent Outbox 事件
     */
    void insertOutbox(RuntimeOutboxEvent outboxEvent);

    /**
     * 按作用域和 Key 读取幂等记录。
     *
     * @param scopeType      作用域类型
     * @param scopeId        作用域标识
     * @param idempotencyKey 幂等键
     * @return 幂等记录
     */
    Optional<IdempotencyRecord> findIdempotency(
        String scopeType, String scopeId, String idempotencyKey);

    /**
     * 插入独立命令的持久幂等结果；必须与所属状态变化处于同一事务。
     *
     * @param idempotency 幂等记录
     */
    void insertIdempotency(IdempotencyRecord idempotency);

    /**
     * 将失联且不可恢复的旧 Run 标记为 ABANDONED，并原子追加新 Run Attempt 与 Work Item。
     *
     * @param turn        当前 Turn
     * @param abandoned   已使用新 Fencing Token 接管的旧 Run
     * @param retry       新 Run Attempt
     * @param workItem    新 Run 对应 Work Item
     * @param outboxEvent 恢复诊断 Outbox
     * @param occurredAt  状态转换时刻
     */
    void replaceOrphanRun(
        Turn turn,
        Run abandoned,
        Run retry,
        RuntimeWorkItem workItem,
        RuntimeOutboxEvent outboxEvent,
        Instant occurredAt);
}
