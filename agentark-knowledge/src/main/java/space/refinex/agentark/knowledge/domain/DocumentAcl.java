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

package space.refinex.agentark.knowledge.domain;

import java.util.Objects;

/**
 * 表示文档在项目内的显式访问控制条目；项目租户授权仍是第一道边界。
 *
 * @param subjectType ACL 主体类型
 * @param subjectId   主体 UUIDv7 规范字符串
 * @param accessLevel 允许的最高访问级别
 * @author refinex
 */
public record DocumentAcl(SubjectType subjectType, String subjectId, AccessLevel accessLevel) {

    /**
     * 校验 ACL 主体与权限级别。
     *
     * @param subjectType ACL 主体类型
     * @param subjectId   主体 UUIDv7 规范字符串
     * @param accessLevel 访问级别
     */
    public DocumentAcl {
        Objects.requireNonNull(subjectType, "subjectType must not be null");
        Objects.requireNonNull(accessLevel, "accessLevel must not be null");
        if (subjectId == null
            || !subjectId.matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("subjectId must be a canonical UUIDv7");
        }
    }

    /**
     * 定义 ACL 可绑定的稳定主体类型。
     *
     * @author refinex
     */
    public enum SubjectType {

        /**
         * 整个项目内已授权主体。
         */
        PROJECT,

        /**
         * 外部用户身份映射。
         */
        USER,

        /**
         * 服务账号主体。
         */
        SERVICE_ACCOUNT,

        /**
         * 项目或环境范围角色。
         */
        ROLE
    }

    /**
     * 定义 ACL 的单调访问级别。
     *
     * @author refinex
     */
    public enum AccessLevel {

        /**
         * 允许读取文档与修订元数据。
         */
        READ,

        /**
         * 允许追加文档修订。
         */
        WRITE,

        /**
         * 允许变更 ACL 和发起删除。
         */
        MANAGE
    }
}
