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

import space.refinex.agentark.runtime.port.UsageGovernanceStore.UsageExportRecord;

/**
 * 定义 Runtime 向 Control Internal Contract 幂等提交 Usage 的客户端端口。
 *
 * @author refinex
 */
@FunctionalInterface
public interface UsageGovernanceClient {

    /**
     * 幂等提交一条 Usage 投影；Control 接受重放也视为成功。
     *
     * @param record Usage 投影
     */
    void export(UsageExportRecord record);
}
