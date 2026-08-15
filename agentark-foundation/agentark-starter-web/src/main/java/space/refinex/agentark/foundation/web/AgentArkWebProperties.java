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

package space.refinex.agentark.foundation.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定义 Web Starter 的开关、请求关联头和分页上限，不读取进程环境。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.web")
public class AgentArkWebProperties {

    /**
     * 是否启用 Web Starter 的公共自动配置。
     */
    private boolean enabled = true;

    /**
     * 请求关联标识使用的 HTTP Header 名称。
     */
    private String requestIdHeader = "X-Request-ID";

    /**
     * Cursor 分页允许的最大单页条目数。
     */
    private int maxPageSize = 200;

    /**
     * 返回 Web Starter 是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Web Starter 启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回请求关联标识 Header 名称。
     *
     * @return 非空 Header 名称
     */
    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    /**
     * 设置请求关联标识 Header 名称。
     *
     * @param requestIdHeader 非空 Header 名称
     * @throws IllegalArgumentException 当名称为空时抛出
     */
    public void setRequestIdHeader(String requestIdHeader) {
        if (requestIdHeader == null || requestIdHeader.isBlank()) {
            throw new IllegalArgumentException("requestIdHeader must not be blank");
        }
        this.requestIdHeader = requestIdHeader;
    }

    /**
     * 返回 Cursor 分页最大单页条目数。
     *
     * @return 1 到 1000 之间的上限
     */
    public int getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置 Cursor 分页最大单页条目数。
     *
     * @param maxPageSize 1 到 1000 之间的上限
     * @throws IllegalArgumentException 当上限越界时抛出
     */
    public void setMaxPageSize(int maxPageSize) {
        if (maxPageSize < 1 || maxPageSize > 1000) {
            throw new IllegalArgumentException("maxPageSize must be between 1 and 1000");
        }
        this.maxPageSize = maxPageSize;
    }
}
