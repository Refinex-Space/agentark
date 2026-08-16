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

package space.refinex.agentark.runtime.domain;

import space.refinex.agentark.runtime.domain.RuntimeModels.ApprovalStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.RunStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.TurnStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一校验 Turn、Run 与 Approval 的单向状态转换，拒绝跳过事实或重开终态。
 *
 * @author refinex
 */
public final class RuntimeStateMachine {

    /**
     * Turn 合法后继状态表。
     */
    private static final Map<TurnStatus, Set<TurnStatus>> TURN_TRANSITIONS = Map.of(
        TurnStatus.ACCEPTED, Set.of(TurnStatus.QUEUED, TurnStatus.CANCELLED),
        TurnStatus.QUEUED, Set.of(TurnStatus.RUNNING, TurnStatus.CANCELLED, TurnStatus.TIMED_OUT),
        TurnStatus.RUNNING, Set.of(
            TurnStatus.WAITING_APPROVAL, TurnStatus.COMPLETED, TurnStatus.FAILED,
            TurnStatus.CANCELLED, TurnStatus.TIMED_OUT),
        TurnStatus.WAITING_APPROVAL, Set.of(
            TurnStatus.RUNNING, TurnStatus.FAILED, TurnStatus.CANCELLED, TurnStatus.TIMED_OUT));

    /**
     * Run 合法后继状态表。
     */
    private static final Map<RunStatus, Set<RunStatus>> RUN_TRANSITIONS = Map.of(
        RunStatus.CREATED, Set.of(RunStatus.CLAIMED, RunStatus.CANCELLED),
        RunStatus.CLAIMED, Set.of(RunStatus.RUNNING, RunStatus.ABANDONED, RunStatus.CANCELLED),
        RunStatus.RUNNING, Set.of(
            RunStatus.PAUSED, RunStatus.SUCCEEDED, RunStatus.FAILED,
            RunStatus.CANCELLED, RunStatus.ABANDONED),
        RunStatus.PAUSED, Set.of(RunStatus.RUNNING, RunStatus.CANCELLED, RunStatus.ABANDONED));

    /**
     * Approval 合法后继状态表。
     */
    private static final Map<ApprovalStatus, Set<ApprovalStatus>> APPROVAL_TRANSITIONS = Map.of(
        ApprovalStatus.PENDING, Set.of(
            ApprovalStatus.APPROVED, ApprovalStatus.REJECTED,
            ApprovalStatus.EXPIRED, ApprovalStatus.CANCELLED));

    /**
     * 禁止实例化无状态状态机。
     */
    private RuntimeStateMachine() {
    }

    /**
     * 校验 Turn 状态转换。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @throws IllegalStateException 非法跳转或终态重开时抛出
     */
    public static void requireTurnTransition(TurnStatus current, TurnStatus target) {
        requireTransition("turn", current, target, TURN_TRANSITIONS);
    }

    /**
     * 校验 Run 状态转换。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @throws IllegalStateException 非法跳转或终态重开时抛出
     */
    public static void requireRunTransition(RunStatus current, RunStatus target) {
        requireTransition("run", current, target, RUN_TRANSITIONS);
    }

    /**
     * 校验 Approval 状态转换。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @throws IllegalStateException 非法跳转或终态重开时抛出
     */
    public static void requireApprovalTransition(
        ApprovalStatus current, ApprovalStatus target) {
        requireTransition("approval", current, target, APPROVAL_TRANSITIONS);
    }

    /**
     * 判断 Run 是否已进入不可重开的终态。
     *
     * @param status Run 状态
     * @return SUCCEEDED、FAILED、CANCELLED 或 ABANDONED 时返回 true
     */
    public static boolean isTerminal(RunStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return status == RunStatus.SUCCEEDED || status == RunStatus.FAILED
            || status == RunStatus.CANCELLED || status == RunStatus.ABANDONED;
    }

    /**
     * 校验 Turn 可通过显式 Retry 命令创建新 Run Attempt。
     *
     * @param status 当前 Turn 状态
     * @throws IllegalStateException Turn 不是 FAILED 或 TIMED_OUT 时抛出
     */
    public static void requireRetryable(TurnStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status != TurnStatus.FAILED && status != TurnStatus.TIMED_OUT) {
            throw new IllegalStateException("turn is not retryable: " + status);
        }
    }

    /**
     * 使用指定转换表验证状态后继关系。
     *
     * @param name        聚合名称
     * @param current     当前状态
     * @param target      目标状态
     * @param transitions 合法转换表
     * @param <S>         枚举状态类型
     */
    private static <S extends Enum<S>> void requireTransition(
        String name, S current, S target, Map<S, Set<S>> transitions) {
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!transitions.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException(
                name + " transition is not allowed: " + current + " -> " + target);
        }
    }
}
