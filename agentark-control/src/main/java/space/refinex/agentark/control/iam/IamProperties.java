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

package space.refinex.agentark.control.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 定义 Control IAM 授权缓存和本地 Bootstrap 的安全配置。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.control.iam")
public class IamProperties {

    /**
     * 是否启用 Control IAM 装配；无数据库的轻量上下文测试可以显式关闭。
     */
    private boolean enabled = true;

    /**
     * 授权缓存最大陈旧窗口，默认十五秒且不得超过一分钟。
     */
    private Duration authorizationCacheTtl = Duration.ofSeconds(15);

    /**
     * 返回 IAM 业务装配是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 IAM 业务装配启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回授权缓存 TTL。
     *
     * @return 正数且不超过一分钟的时长
     */
    public Duration getAuthorizationCacheTtl() {
        return authorizationCacheTtl;
    }

    /**
     * 设置授权缓存 TTL。
     *
     * @param authorizationCacheTtl 正数且不超过一分钟的时长
     */
    public void setAuthorizationCacheTtl(Duration authorizationCacheTtl) {
        if (authorizationCacheTtl == null
            || authorizationCacheTtl.isZero()
            || authorizationCacheTtl.isNegative()
            || authorizationCacheTtl.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException(
                "authorizationCacheTtl must be positive and at most one minute");
        }
        this.authorizationCacheTtl = authorizationCacheTtl;
    }
}
