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

package space.refinex.agentark.foundation.observability;

/**
 * 定义可观测数据采集边界；Secret 永不允许采集，Prompt、Tool 参数和文档正文默认关闭。
 *
 * @param collectPromptText    是否显式允许采集 Prompt 正文
 * @param collectToolArguments 是否显式允许采集 Tool 参数
 * @param collectDocumentText  是否显式允许采集文档正文
 * @author refinex
 */
public record ObservabilityDataPolicy(
    boolean collectPromptText, boolean collectToolArguments, boolean collectDocumentText) {

    /**
     * 创建不可变采集策略；Secret 不属于可配置选项，始终禁止。
     *
     * @param collectPromptText    是否采集 Prompt 正文
     * @param collectToolArguments 是否采集 Tool 参数
     * @param collectDocumentText  是否采集文档正文
     */
    public ObservabilityDataPolicy {
        // 三项显式布尔值已完整表达策略，不存在隐式默认或 Secret 开关。
    }

    /**
     * 返回最小披露的安全默认策略。
     *
     * @return 三类正文均不采集的策略
     */
    public static ObservabilityDataPolicy secureDefaults() {
        return new ObservabilityDataPolicy(false, false, false);
    }
}
