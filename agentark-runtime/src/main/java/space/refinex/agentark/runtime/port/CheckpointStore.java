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

import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.domain.RuntimeModels.Checkpoint;

import java.util.Optional;

/**
 * 定义只引用已提交 Agent State Version 的可恢复 Checkpoint Store。
 *
 * @author refinex
 */
public interface CheckpointStore {

    /**
     * 追加 Checkpoint；过期 Fencing Token 或未提交 State 必须失败。
     *
     * @param checkpoint 待追加 Checkpoint
     */
    void append(Checkpoint checkpoint);

    /**
     * 查找 Run 最新可恢复 Checkpoint。
     *
     * @param runId Run 标识
     * @return 最新可恢复 Checkpoint
     */
    Optional<Checkpoint> findLatestRecoverable(RunId runId);
}
