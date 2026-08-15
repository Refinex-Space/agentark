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

package space.refinex.agentark.foundation.persistence;

import java.time.Instant;

/**
 * 定义数据库持久化对象应暴露的审计字段读取契约，不提供通用 BaseEntity 实现。
 *
 * @author refinex
 */
public interface DatabaseAuditFields {

    /**
     * 返回记录首次持久化的 UTC 时刻。
     *
     * @return 非空创建时间
     */
    Instant createdAt();

    /**
     * 返回记录最后一次成功更新的 UTC 时刻。
     *
     * @return 非空更新时间
     */
    Instant updatedAt();

    /**
     * 返回创建记录的已认证主体稳定标识。
     *
     * @return 非空主体标识，不是显示名
     */
    String createdBy();

    /**
     * 返回用于乐观锁比较的非负版本号。
     *
     * @return 非负版本号
     */
    long version();
}
