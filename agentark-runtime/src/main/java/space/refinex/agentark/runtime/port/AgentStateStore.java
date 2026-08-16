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

import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.runtime.domain.RuntimeModels.AgentStateVersion;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;

import java.util.Optional;

/**
 * 定义 Provider 中立 Agent State 版本端口，不暴露 AgentScope State 类型或自动建表行为。
 *
 * @author refinex
 */
public interface AgentStateStore {

    /**
     * 追加不可覆盖的 State Version。
     *
     * @param stateVersion 新状态版本
     */
    void append(AgentStateVersion stateVersion);

    /**
     * 将已写 State Version 原子标记为对 Checkpoint 可见。
     *
     * @param stateVersion State Version
     * @param fencingToken 当前有效令牌
     */
    void commit(AgentStateVersion stateVersion, FencingToken fencingToken);

    /**
     * 查找指定稳定 State Key 的最新已提交版本。
     *
     * @param sessionId Session 标识
     * @param agentKey  Agent 稳定 Key
     * @param stateKey  状态 Key
     * @param itemIndex 列表下标
     * @return 最新已提交版本
     */
    Optional<AgentStateVersion> findLatestCommitted(
        SessionId sessionId, String agentKey, String stateKey, int itemIndex);
}
