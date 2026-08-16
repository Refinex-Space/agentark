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

package space.refinex.agentark.scheduling.port;

/**
 * 按 SecretRef 临时解析 Webhook 验签密钥；调用方负责立即清零返回数组。
 *
 * @author refinex
 */
@FunctionalInterface
public interface WebhookSecretResolver {

    /**
     * 解析指定 SecretRef 的当前密钥值，禁止缓存、记录或持久化。
     *
     * @param secretRef 外部 Secret 引用
     * @return 可清零字符数组
     */
    char[] resolve(String secretRef);
}
