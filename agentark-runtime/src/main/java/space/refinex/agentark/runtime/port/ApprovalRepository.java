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

import space.refinex.agentark.kernel.id.ApprovalId;
import space.refinex.agentark.runtime.domain.RuntimeModels.Approval;
import space.refinex.agentark.runtime.domain.RuntimeModels.ApprovalStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * 定义参数 Hash 不可替换且决策幂等的 Approval 持久化端口。
 *
 * @author refinex
 */
public interface ApprovalRepository {

    /**
     * 创建待审批事实。
     *
     * @param approval 待审批记录
     */
    void insert(Approval approval);

    /**
     * 按标识读取 Approval。
     *
     * @param approvalId Approval 标识
     * @return 审批记录
     */
    Optional<Approval> find(ApprovalId approvalId);

    /**
     * 使用乐观锁写入一次性决策。
     *
     * @param approvalId     Approval 标识
     * @param expectedVersion 预期版本
     * @param target         APPROVED、REJECTED、EXPIRED 或 CANCELLED
     * @param decisionBy     决策主体
     * @param decisionAt     决策时刻
     * @return 实际更新行数
     */
    int decide(
        ApprovalId approvalId,
        long expectedVersion,
        ApprovalStatus target,
        String decisionBy,
        Instant decisionAt);
}
