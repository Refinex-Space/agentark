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

package space.refinex.agentark.control.iam.adapter.in.web;

import java.util.Set;

/**
 * 定义 Control IAM 只供版本化内部客户端使用的语言中立响应。
 *
 * @author refinex
 */
public final class IamInternalApiModels {

    /** 禁止实例化内部响应容器。 */
    private IamInternalApiModels() {
    }

    /**
     * 表示已经由 Control 摘要、到期和吊销状态校验的 API Key 主体。
     *
     * @param issuer         凭据签发方
     * @param subject        服务账号主体标识
     * @param principalType  固定为 API_KEY
     * @param authorities    API Key 收窄后的权限集合
     * @param organizationId 所属组织标识
     * @param projectId      所属项目标识
     * @author refinex
     */
    public record ApiKeyVerificationResponse(
        String issuer,
        String subject,
        String principalType,
        Set<String> authorities,
        String organizationId,
        String projectId) {

        /**
         * 防御性复制权限集合。
         *
         * @param issuer         凭据签发方
         * @param subject        服务账号主体标识
         * @param principalType  主体类型
         * @param authorities    权限集合
         * @param organizationId 组织标识
         * @param projectId      项目标识
         */
        public ApiKeyVerificationResponse {
            issuer = requireText(issuer, "issuer");
            subject = requireText(subject, "subject");
            if (!"API_KEY".equals(principalType)) {
                throw new IllegalArgumentException("principalType must be API_KEY");
            }
            authorities = Set.copyOf(java.util.Objects.requireNonNull(
                authorities, "authorities must not be null"));
            organizationId = requireText(organizationId, "organizationId");
            projectId = requireText(projectId, "projectId");
        }

        /**
         * 校验内部响应文本字段不为空白。
         *
         * @param value 字段值
         * @param name  字段名
         * @return 原始非空白值
         */
        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
