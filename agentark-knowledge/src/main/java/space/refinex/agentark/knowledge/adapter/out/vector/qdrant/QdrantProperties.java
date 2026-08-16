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

package space.refinex.agentark.knowledge.adapter.out.vector.qdrant;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * 定义 Qdrant REST Adapter 的非敏感固定配置。
 *
 * @param endpoint   不含凭据和路径的 Qdrant REST 根地址
 * @param collection 平台受控 Collection 名称
 * @param dimension  固定向量维度
 * @param timeout    单次 HTTP 请求超时
 * @author refinex
 */
public record QdrantProperties(
    URI endpoint, String collection, int dimension, Duration timeout) {

    /**
     * 校验远程 HTTPS、本机 HTTP、Collection 名称、向量维度和超时边界。
     *
     * @param endpoint   Qdrant 根地址
     * @param collection Collection 名称
     * @param dimension  向量维度
     * @param timeout    请求超时
     */
    public QdrantProperties {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host) || "::1".equals(host);
        boolean validScheme = "https".equalsIgnoreCase(endpoint.getScheme())
            || (loopback && "http".equalsIgnoreCase(endpoint.getScheme()));
        String path = endpoint.getPath();
        if (!validScheme
            || endpoint.getUserInfo() != null
            || endpoint.getQuery() != null
            || endpoint.getFragment() != null
            || (path != null && !path.isBlank() && !"/".equals(path))
            || collection == null
            || !collection.matches("[a-z][a-z0-9_-]{2,63}")
            || dimension < 1
            || dimension > 65_536
            || timeout.isZero()
            || timeout.isNegative()
            || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("qdrant properties are invalid");
        }
        endpoint = URI.create(endpoint.toString().replaceAll("/+$", ""));
    }
}
