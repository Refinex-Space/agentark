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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
     * 是否强制 Skill 签名、SBOM、扫描证明和许可证白名单；生产默认启用。
     */
    private boolean skillSupplyChainRequired = true;

    /**
     * 单个 Skill CycloneDX SBOM 最大字节数。
     */
    private DataSize maxSkillSbomSize = DataSize.ofMegabytes(2);

    /**
     * 允许进入 Runtime 的 SPDX 许可证标识集合。
     */
    private Set<String> allowedSkillLicenses = new LinkedHashSet<>(Set.of("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause"));

    /**
     * Skill 签名 Key ID 到 Base64 X.509 Ed25519 公钥的部署级信任根。
     */
    private Map<String, String> trustedSkillSigningKeys = new LinkedHashMap<>();

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
        if (maxArtifactSize == null
            || maxArtifactSize.toBytes() < 1
            || maxArtifactSize.toBytes() > DataSize.ofMegabytes(64).toBytes()) {
            throw new IllegalArgumentException("maxArtifactSize must be between 1 byte and 64 MiB");
        }
        this.maxArtifactSize = maxArtifactSize;
    }

    /**
     * @return 是否强制 Skill 供应链证明
     */
    public boolean isSkillSupplyChainRequired() {
        return skillSupplyChainRequired;
    }

    /**
     * @param skillSupplyChainRequired 生产必须为 true，本地开发可以显式关闭
     */
    public void setSkillSupplyChainRequired(boolean skillSupplyChainRequired) {
        this.skillSupplyChainRequired = skillSupplyChainRequired;
    }

    /**
     * @return Skill SBOM 最大大小
     */
    public DataSize getMaxSkillSbomSize() {
        return maxSkillSbomSize;
    }

    /**
     * @param maxSkillSbomSize 1 字节到 8 MiB 的大小
     */
    public void setMaxSkillSbomSize(DataSize maxSkillSbomSize) {
        if (maxSkillSbomSize == null
            || maxSkillSbomSize.toBytes() < 1
            || maxSkillSbomSize.toBytes() > DataSize.ofMegabytes(8).toBytes()) {
            throw new IllegalArgumentException("maxSkillSbomSize must be between 1 byte and 8 MiB");
        }
        this.maxSkillSbomSize = maxSkillSbomSize;
    }

    /**
     * @return 允许的 SPDX 许可证集合
     */
    public Set<String> getAllowedSkillLicenses() {
        return Set.copyOf(allowedSkillLicenses);
    }

    /**
     * @param allowedSkillLicenses 非空 SPDX 许可证白名单
     */
    public void setAllowedSkillLicenses(Set<String> allowedSkillLicenses) {
        if (allowedSkillLicenses == null
            || allowedSkillLicenses.isEmpty()
            || allowedSkillLicenses.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("allowedSkillLicenses must not be empty");
        }
        this.allowedSkillLicenses = new LinkedHashSet<>(allowedSkillLicenses);
    }

    /**
     * @return Key ID 到 Base64 Ed25519 公钥的只读视图
     */
    public Map<String, String> getTrustedSkillSigningKeys() {
        return Map.copyOf(trustedSkillSigningKeys);
    }

    /**
     * @param trustedSkillSigningKeys 部署 Secret 或受保护配置注入的信任根
     */
    public void setTrustedSkillSigningKeys(Map<String, String> trustedSkillSigningKeys) {
        this.trustedSkillSigningKeys = new LinkedHashMap<>(trustedSkillSigningKeys == null ? Map.of() : trustedSkillSigningKeys);
    }
}
