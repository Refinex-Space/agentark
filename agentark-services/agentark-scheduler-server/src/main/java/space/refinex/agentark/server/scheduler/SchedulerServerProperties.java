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

package space.refinex.agentark.server.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * @param controlBaseUrl       Control Internal API 基础地址
 * @param runtimeBaseUrl       Runtime Internal API 基础地址
 * @param internalServiceToken 短期服务身份 Token，配置层不记录该值
 * @param instanceKey          Scheduler Worker 稳定实例 Key
 * @param leaseTtl             Job Lease 有效期
 * @param workerEnabled        是否启动持久 Worker Loop
 * @param workerPollDelay      无 Job 时的轮询间隔
 * @param cronScanDelay        Cron Cursor 扫描间隔
 * @param workerPoolSize       每个 Job Type 的独立 Worker 数
 * @author refinex
 */
@ConfigurationProperties(prefix = "agentark.scheduler")
public record SchedulerServerProperties(
    URI controlBaseUrl,
    URI runtimeBaseUrl,
    String internalServiceToken,
    String instanceKey,
    Duration leaseTtl,
    boolean workerEnabled,
    Duration workerPollDelay,
    Duration cronScanDelay,
    int workerPoolSize) {

    /**
     * 校验内部服务 URL、实例 Key、Lease、轮询和类型 Worker 并发边界。
     */
    public SchedulerServerProperties {
        if (controlBaseUrl == null || runtimeBaseUrl == null
            || !isHttp(controlBaseUrl) || !isHttp(runtimeBaseUrl)
            || instanceKey == null
            || !instanceKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{2,254}")
            || leaseTtl == null || leaseTtl.compareTo(Duration.ofSeconds(5)) < 0
            || leaseTtl.compareTo(Duration.ofMinutes(30)) > 0
            || workerPollDelay == null || workerPollDelay.isNegative()
            || workerPollDelay.isZero() || workerPollDelay.compareTo(Duration.ofMinutes(1)) > 0
            || cronScanDelay == null || cronScanDelay.compareTo(Duration.ofSeconds(1)) < 0
            || cronScanDelay.compareTo(Duration.ofMinutes(5)) > 0
            || workerPoolSize < 1 || workerPoolSize > 64) {
            throw new IllegalArgumentException("scheduler server properties are invalid");
        }
        internalServiceToken = internalServiceToken == null ? "" : internalServiceToken;
    }

    /**
     * 校验内部 URL 使用 HTTP 或 HTTPS 且不包含 UserInfo。
     *
     * @param uri 待校验 URI
     * @return 合法时为 true
     */
    private static boolean isHttp(URI uri) {
        return ("http".equalsIgnoreCase(uri.getScheme())
            || "https".equalsIgnoreCase(uri.getScheme()))
            && uri.getUserInfo() == null && uri.getFragment() == null;
    }
}
