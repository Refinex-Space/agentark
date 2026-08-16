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

package space.refinex.agentark.knowledge.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 定义 Knowledge Revision 从描述、摄取、验证、可用到清理完成的完整状态机。
 *
 * @author refinex
 */
public enum KnowledgeRevisionStatus {

    /**
     * 已创建元数据，尚未提交摄取。
     */
    CREATED,

    /**
     * 已描述摄取请求，等待后续阶段的异步执行器处理。
     */
    INGESTING,

    /**
     * 摄取结果等待完整性和可检索性验证。
     */
    VERIFYING,

    /**
     * 已验证且允许 Agent Revision 引用。
     */
    READY,

    /**
     * 摄取或验证失败，可重新提交摄取或进入删除流程。
     */
    FAILED,

    /**
     * 已停止新增引用，但仍保留既有引用和派生数据。
     */
    DEPRECATED,

    /**
     * 已进入原文件与派生数据清理流程。
     */
    DELETING,

    /**
     * 原文件与派生数据均已清理，状态终止。
     */
    DELETED;

    /**
     * 保存每个状态允许到达的下一状态，未列出的转换一律拒绝。
     */
    private static final Map<KnowledgeRevisionStatus, Set<KnowledgeRevisionStatus>> TRANSITIONS =
        Map.of(
            CREATED, EnumSet.of(INGESTING, DELETING),
            INGESTING, EnumSet.of(VERIFYING, FAILED),
            VERIFYING, EnumSet.of(READY, FAILED),
            READY, EnumSet.of(DEPRECATED, DELETING),
            FAILED, EnumSet.of(INGESTING, DELETING),
            DEPRECATED, EnumSet.of(DELETING),
            DELETING, EnumSet.of(DELETED),
            DELETED, EnumSet.noneOf(KnowledgeRevisionStatus.class));

    /**
     * 判断当前状态是否允许转换到目标状态。
     *
     * @param target 目标状态
     * @return 允许转换时返回 {@code true}
     */
    public boolean canTransitionTo(KnowledgeRevisionStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    /**
     * 校验并返回目标状态，供聚合执行单一状态转换。
     *
     * @param target 目标状态
     * @return 已通过状态机校验的目标状态
     * @throws IllegalStateException 当转换不在白名单内时抛出
     */
    public KnowledgeRevisionStatus requireTransitionTo(KnowledgeRevisionStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("knowledge revision transition is not allowed");
        }
        return target;
    }
}
