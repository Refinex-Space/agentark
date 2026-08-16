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

package space.refinex.agentark.control.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 定义仅供 local Profile 使用的文件 Secret Provider 开关和受控根目录。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.control.secret")
public class SecretProperties {

    /**
     * 是否启用开发 Local File Provider；默认关闭。
     */
    private boolean localProviderEnabled;

    /**
     * Local File Provider 专用根目录。
     */
    private Path localRoot = Path.of(".agentark", "secrets");

    /**
     * @return 是否启用开发 Local File Provider
     */
    public boolean isLocalProviderEnabled() {
        return localProviderEnabled;
    }

    /**
     * @param localProviderEnabled 是否启用
     */
    public void setLocalProviderEnabled(boolean localProviderEnabled) {
        this.localProviderEnabled = localProviderEnabled;
    }

    /**
     * @return 本地 Secret 专用根目录
     */
    public Path getLocalRoot() {
        return localRoot;
    }

    /**
     * @param localRoot 非空专用根目录
     */
    public void setLocalRoot(Path localRoot) {
        this.localRoot = java.util.Objects.requireNonNull(
            localRoot, "localRoot must not be null");
    }
}

