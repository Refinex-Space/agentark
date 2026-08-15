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

package space.refinex.agentark.foundation.security;

import java.util.Optional;

/**
 * 定义 API Key 认证扩展点；实现属于 Control IAM，必须只持久化摘要并支持轮换和吊销。
 *
 * @author refinex
 */
@FunctionalInterface
public interface ApiKeyAuthenticator {

    /**
     * 校验一次 API Key 凭据并返回已认证主体，调用完成后实现必须清理敏感字符数组。
     *
     * @param keyId    非秘密的 Key 标识
     * @param secret   仅本次调用可用的明文字符数组，禁止日志记录或持久化
     * @param audience 当前服务 Audience
     * @return 认证成功的 API Key 主体；失败时为空
     */
    Optional<AgentArkPrincipal> authenticate(String keyId, char[] secret, String audience);
}
