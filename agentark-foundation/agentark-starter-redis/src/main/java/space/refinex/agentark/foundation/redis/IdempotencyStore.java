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

package space.refinex.agentark.foundation.redis;

import java.time.Duration;

/**
 * 定义 Redis 幂等加速记录；持久业务工作和结果仍必须由所属 MySQL 事务保存。
 *
 * @author refinex
 */
public interface IdempotencyStore {

    /**
     * 原子声明幂等键并区分重放与冲突。
     *
     * @param namespace       业务命名空间
     * @param idempotencyKey  调用方提供的非秘密幂等键
     * @param requestHash     规范化请求摘要
     * @param resultReference 已持久化结果的稳定引用
     * @param ttl             Redis 加速记录的有限存活时间
     * @return 幂等判断结果
     */
    IdempotencyDecision claim(
        String namespace,
        String idempotencyKey,
        String requestHash,
        String resultReference,
        Duration ttl);
}
