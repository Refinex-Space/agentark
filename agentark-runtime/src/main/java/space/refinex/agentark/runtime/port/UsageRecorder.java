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

import space.refinex.agentark.runtime.domain.RuntimeModels.UsageRecord;

/**
 * 定义 Provider 请求去重的追加式用量记录端口。
 *
 * @author refinex
 */
public interface UsageRecorder {

    /**
     * 追加用量事实；重复 Provider Request ID 必须幂等。
     *
     * @param usageRecord 用量事实
     */
    void record(UsageRecord usageRecord);
}
