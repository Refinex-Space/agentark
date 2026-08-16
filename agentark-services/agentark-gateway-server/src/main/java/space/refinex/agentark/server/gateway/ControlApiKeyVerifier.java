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
 * 定义 Gateway 对 Control API Key 摘要认证结果的响应式读取端口。
 *
 * @author refinex
 */
public interface ControlApiKeyVerifier {

    /**
     * 校验 API Key 明文；实现不得持久化、缓存或记录原始凭据。
     *
     * @param credential 当前请求携带的完整 API Key
     * @return 成功时返回非秘密主体，凭据无效时返回空
     */
    Mono<Optional<AgentArkPrincipal>> verify(String credential);
}
