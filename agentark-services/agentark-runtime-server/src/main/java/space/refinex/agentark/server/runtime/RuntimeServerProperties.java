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

package space.refinex.agentark.server.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * 定义 Runtime Worker、Control Internal Client 与 Lease 的非业务配置。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.runtime")
public class RuntimeServerProperties {

    /**
     * Control Internal API 基础地址。
     */
    private URI controlBaseUrl;

    /**
     * 由 Secret 注入且只进入 Authorization Header 的服务身份 Token。
     */
    private String internalServiceToken;

    /**
     * Runtime Instance 在部署范围内的稳定 Key。
     */
    private String instanceKey;

    /**
     * Work Item 与 Redis Lease TTL。
     */
    private Duration leaseTtl = Duration.ofSeconds(30);

    /**
     * 是否启动持久 Work Queue Worker。
     */
    private boolean workerEnabled;

    /**
     * 返回 Control Internal API 基础地址。
     *
     * @return Control 地址
     */
    public URI getControlBaseUrl() {
        return controlBaseUrl;
    }

    /**
     * 设置 Control Internal API 基础地址。
     *
     * @param controlBaseUrl HTTP 或 HTTPS 地址
     */
    public void setControlBaseUrl(URI controlBaseUrl) {
        this.controlBaseUrl = controlBaseUrl;
    }

    /**
     * 返回服务身份 Token。
     *
     * @return 可为空的 Token
     */
    public String getInternalServiceToken() {
        return internalServiceToken;
    }

    /**
     * 设置服务身份 Token。
     *
     * @param internalServiceToken Secret 注入值
     */
    public void setInternalServiceToken(String internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * 返回 Runtime Instance Key。
     *
     * @return Instance Key
     */
    public String getInstanceKey() {
        return instanceKey;
    }

    /**
     * 设置 Runtime Instance Key。
     *
     * @param instanceKey 部署范围内稳定值
     */
    public void setInstanceKey(String instanceKey) {
        this.instanceKey = instanceKey;
    }

    /**
     * 返回 Lease TTL。
     *
     * @return Lease TTL
     */
    public Duration getLeaseTtl() {
        return leaseTtl;
    }

    /**
     * 设置 Lease TTL。
     *
     * @param leaseTtl 正数时长
     */
    public void setLeaseTtl(Duration leaseTtl) {
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        this.leaseTtl = leaseTtl;
    }

    /**
     * 返回 Worker 是否启用。
     *
     * @return 启用时为 true
     */
    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    /**
     * 设置 Worker 开关。
     *
     * @param workerEnabled 是否启用
     */
    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }
}
