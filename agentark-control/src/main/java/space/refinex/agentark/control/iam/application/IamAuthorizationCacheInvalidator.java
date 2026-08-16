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

package space.refinex.agentark.control.iam.application;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import space.refinex.agentark.control.iam.application.port.AuthorizationCache;

import java.util.Objects;

/**
 * 在授权事实提交后消费失效事件，主动清理短 TTL 缓存。
 *
 * @author refinex
 */
public final class IamAuthorizationCacheInvalidator {

    /**
     * 有效权限缓存端口。
     */
    private final AuthorizationCache cache;

    /**
     * 创建授权缓存失效监听器。
     *
     * @param cache 缓存端口
     */
    public IamAuthorizationCacheInvalidator(AuthorizationCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    /**
     * 在提交后按项目或组织范围主动失效缓存；无事务的受控调用也立即执行。
     *
     * @param event 授权事实变化事件
     */
    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true)
    public void invalidate(IamAuthorizationChanged event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.projectId().isPresent()) {
            cache.evictProject(event.projectId().orElseThrow());
        } else {
            cache.evictOrganization(event.organizationId());
        }
    }
}
