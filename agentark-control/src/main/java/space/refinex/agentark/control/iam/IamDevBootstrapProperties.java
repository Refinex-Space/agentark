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

/**
 * 定义仅限 local Profile 且默认关闭的 IAM 开发引导资源，不包含口令或共享密钥。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.control.iam.dev-bootstrap")
public class IamDevBootstrapProperties {

    /**
     * 是否显式启用本地开发引导，默认关闭。
     */
    private boolean enabled;

    /**
     * 本地开发主体的受控 Issuer。
     */
    private String issuer = "urn:agentark:local-dev";

    /**
     * 本地开发主体的稳定 Subject。
     */
    private String subject = "local-developer";

    /**
     * 幂等创建的组织 Slug。
     */
    private String organizationSlug = "local-org";

    /**
     * 本地组织展示名称。
     */
    private String organizationName = "本地开发组织";

    /**
     * 幂等创建的项目 Slug。
     */
    private String projectSlug = "local-project";

    /**
     * 本地项目展示名称。
     */
    private String projectName = "本地开发项目";

    /**
     * 幂等创建的环境 Key。
     */
    private String environmentKey = "local";

    /**
     * 本地环境展示名称。
     */
    private String environmentName = "本地环境";

    /**
     * 返回开发引导是否显式启用。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置开发引导是否启用。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回本地开发身份 Issuer。
     *
     * @return 身份签发方
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * 设置本地开发身份 Issuer。
     *
     * @param issuer 身份签发方
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * 返回本地开发身份 Subject。
     *
     * @return 身份主体
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 设置本地开发身份 Subject。
     *
     * @param subject 身份主体
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * 返回本地组织 Slug。
     *
     * @return 组织 Slug
     */
    public String getOrganizationSlug() {
        return organizationSlug;
    }

    /**
     * 设置本地组织 Slug。
     *
     * @param organizationSlug 组织 Slug
     */
    public void setOrganizationSlug(String organizationSlug) {
        this.organizationSlug = organizationSlug;
    }

    /**
     * 返回本地组织展示名称。
     *
     * @return 组织展示名称
     */
    public String getOrganizationName() {
        return organizationName;
    }

    /**
     * 设置本地组织展示名称。
     *
     * @param organizationName 组织展示名称
     */
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    /**
     * 返回本地项目 Slug。
     *
     * @return 项目 Slug
     */
    public String getProjectSlug() {
        return projectSlug;
    }

    /**
     * 设置本地项目 Slug。
     *
     * @param projectSlug 项目 Slug
     */
    public void setProjectSlug(String projectSlug) {
        this.projectSlug = projectSlug;
    }

    /**
     * 返回本地项目展示名称。
     *
     * @return 项目展示名称
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * 设置本地项目展示名称。
     *
     * @param projectName 项目展示名称
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * 返回本地环境 Key。
     *
     * @return 环境 Key
     */
    public String getEnvironmentKey() {
        return environmentKey;
    }

    /**
     * 设置本地环境 Key。
     *
     * @param environmentKey 环境 Key
     */
    public void setEnvironmentKey(String environmentKey) {
        this.environmentKey = environmentKey;
    }

    /**
     * 返回本地环境展示名称。
     *
     * @return 环境展示名称
     */
    public String getEnvironmentName() {
        return environmentName;
    }

    /**
     * 设置本地环境展示名称。
     *
     * @param environmentName 环境展示名称
     */
    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }
}
