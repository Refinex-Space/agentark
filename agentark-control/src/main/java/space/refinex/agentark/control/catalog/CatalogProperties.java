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

package space.refinex.agentark.control.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 定义 AI 资产目录开关和 Skill Artifact 单次上传上限。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.control.catalog")
public class CatalogProperties {

    /**
     * 是否启用资产目录；默认启用以保持 Control 领域完整。
     */
    private boolean enabled = true;

    /**
     * 单次 Skill Artifact 最大字节数。
     */
    private DataSize maxArtifactSize = DataSize.ofMegabytes(10);

    /**
     * @return 资产目录是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return 单次 Artifact 最大字节数
     */
    public DataSize getMaxArtifactSize() {
        return maxArtifactSize;
    }

    /**
     * @param maxArtifactSize 正数且不超过 64 MiB 的大小
     */
    public void setMaxArtifactSize(DataSize maxArtifactSize) {
        if (maxArtifactSize == null || maxArtifactSize.toBytes() < 1
            || maxArtifactSize.toBytes() > DataSize.ofMegabytes(64).toBytes()) {
            throw new IllegalArgumentException("maxArtifactSize must be between 1 byte and 64 MiB");
        }
        this.maxArtifactSize = maxArtifactSize;
    }
}

