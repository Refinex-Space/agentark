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

package space.refinex.agentark.foundation.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 定义可观测性开关、Metric Tag 白名单和显式正文采集选项；Secret 永不可配置采集。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.observability")
public class AgentArkObservabilityProperties {

    /**
     * 是否启用 AgentArk 可观测约定。
     */
    private boolean enabled = true;

    /**
     * Metric 与 Span Attribute 允许使用的低基数字段。
     */
    private Set<String> allowedTags =
        new LinkedHashSet<>(Set.of("operation", "outcome", "error.category", "runtime.provider"));

    /**
     * 是否显式允许采集 Prompt 正文，默认关闭。
     */
    private boolean collectPromptText;

    /**
     * 是否显式允许采集 Tool 参数，默认关闭。
     */
    private boolean collectToolArguments;

    /**
     * 是否显式允许采集文档正文，默认关闭。
     */
    private boolean collectDocumentText;

    /**
     * 返回可观测约定是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置可观测约定启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回低基数 Tag 白名单。
     *
     * @return 不可变 Tag 集合
     */
    public Set<String> getAllowedTags() {
        return Set.copyOf(allowedTags);
    }

    /**
     * 设置低基数 Tag 白名单。
     *
     * @param allowedTags Tag 集合
     */
    public void setAllowedTags(Set<String> allowedTags) {
        this.allowedTags =
            new LinkedHashSet<>(
                java.util.Objects.requireNonNull(allowedTags, "allowedTags must not be null"));
    }

    /**
     * 返回是否采集 Prompt 正文。
     *
     * @return 允许时为 {@code true}
     */
    public boolean isCollectPromptText() {
        return collectPromptText;
    }

    /**
     * 设置是否采集 Prompt 正文。
     *
     * @param collectPromptText 是否允许
     */
    public void setCollectPromptText(boolean collectPromptText) {
        this.collectPromptText = collectPromptText;
    }

    /**
     * 返回是否采集 Tool 参数。
     *
     * @return 允许时为 {@code true}
     */
    public boolean isCollectToolArguments() {
        return collectToolArguments;
    }

    /**
     * 设置是否采集 Tool 参数。
     *
     * @param collectToolArguments 是否允许
     */
    public void setCollectToolArguments(boolean collectToolArguments) {
        this.collectToolArguments = collectToolArguments;
    }

    /**
     * 返回是否采集文档正文。
     *
     * @return 允许时为 {@code true}
     */
    public boolean isCollectDocumentText() {
        return collectDocumentText;
    }

    /**
     * 设置是否采集文档正文。
     *
     * @param collectDocumentText 是否允许
     */
    public void setCollectDocumentText(boolean collectDocumentText) {
        this.collectDocumentText = collectDocumentText;
    }
}
