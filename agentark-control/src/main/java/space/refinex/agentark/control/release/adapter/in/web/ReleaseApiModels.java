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

package space.refinex.agentark.control.release.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.ReleaseModels.TrafficPolicy;
import space.refinex.agentark.control.release.domain.ReleaseModels.TrafficPolicyType;

/**
 * 集中定义 Agent Release 与 Deployment Public API 请求契约。
 *
 * @author refinex
 */
public final class ReleaseApiModels {

    /**
     * 禁止实例化 API 模型容器。
     */
    private ReleaseApiModels() {
    }

    /**
     * @param key         项目内稳定 Key
     * @param name        展示名称
     * @param description 用途说明
     * @param draft       首个 Draft
     * @author refinex
     */
    public record CreateAgentRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull @Valid AgentDraftSpec draft) {
    }

    /**
     * @param expectedVersion Draft 乐观锁版本
     * @param draft           新 Draft
     * @author refinex
     */
    public record UpdateDraftRequest(
        @PositiveOrZero long expectedVersion,
        @NotNull @Valid AgentDraftSpec draft) {
    }

    /**
     * @param idempotencyKey       调用方幂等键
     * @param expectedDraftVersion 预期 Draft 版本
     * @author refinex
     */
    public record PublishRequest(
        @NotBlank @Size(max = 128) String idempotencyKey,
        @PositiveOrZero long expectedDraftVersion) {
    }

    /**
     * @param agentId       Agent 的 UUIDv7 标识
     * @param revisionId    初始 Revision UUIDv7
     * @param trafficPolicy 流量策略：FULL、CANARY；Phase 10 只执行 FULL
     * @param canaryPercent Canary 百分比
     * @author refinex
     */
    public record CreateDeploymentRequest(
        @NotBlank String agentId,
        @NotBlank String revisionId,
        @NotBlank @Pattern(regexp = "FULL|CANARY") String trafficPolicy,
        @Min(0) @Max(99) int canaryPercent) {
        /**
         * 把公开请求中的稳定字符串转换为强类型流量策略。
         *
         * @return 强类型流量策略
         */
        public TrafficPolicy policy() {
            return new TrafficPolicy(TrafficPolicyType.valueOf(trafficPolicy), canaryPercent);
        }
    }

    /**
     * @param revisionId      目标 Revision UUIDv7
     * @param expectedVersion Deployment 乐观锁版本
     * @author refinex
     */
    public record MoveDeploymentRequest(
        @NotBlank String revisionId, @PositiveOrZero long expectedVersion) {
    }

    /**
     * @param expectedVersion Deployment 乐观锁版本
     * @author refinex
     */
    public record ChangeDeploymentStatusRequest(@PositiveOrZero long expectedVersion) {
    }
}
