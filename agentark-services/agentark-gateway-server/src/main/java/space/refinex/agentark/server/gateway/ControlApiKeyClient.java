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

package space.refinex.agentark.server.gateway;

import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;

import java.util.Optional;

/**
 * 定义不带缓存的 Control API Key 内部客户端，便于独立验证边缘缓存行为。
 *
 * @author refinex
 */
public interface ControlApiKeyClient {

    /**
     * 请求 Control 独立校验原始 API Key。
     *
     * @param credential 完整 API Key，仅用于当前下游请求
     * @return 成功主体或无效凭据空结果
     */
    Mono<Optional<AgentArkPrincipal>> verifyRemotely(String credential);
}
