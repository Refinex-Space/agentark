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

package space.refinex.agentark.control.secret.application.port;

import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.domain.SecretMetadata;

import java.io.IOException;

/**
 * 定义按外部 Provider 元数据短期解析 Secret 的生产 SPI，返回值必须及时关闭。
 *
 * @author refinex
 */
public interface SecretResolver {

    /**
     * @param metadata 已授权且启用的非敏感元数据
     * @return 可清零的短期 Secret 字符数组
     * @throws IOException Provider 读取失败时抛出
     */
    ResolvedSecret resolve(SecretMetadata metadata) throws IOException;
}

