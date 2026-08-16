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

package space.refinex.agentark.knowledge.application;

import java.util.Set;

/**
 * 定义 Knowledge 模块请求组合根校验的稳定权限代码。
 *
 * @author refinex
 */
public final class KnowledgePermissions {

    /**
     * 允许读取 Knowledge 元数据、文档 ACL 和 READY Revision。
     */
    public static final String READ = "knowledge:read";

    /**
     * 允许创建和管理 Knowledge Base、文档、Profile 与 Revision。
     */
    public static final String MANAGE = "knowledge:manage";

    /**
     * 允许描述摄取请求并推动受控状态机。
     */
    public static final String INGEST = "knowledge:ingest";

    /**
     * 当前模块使用的完整权限白名单。
     */
    public static final Set<String> ALL = Set.of(READ, MANAGE, INGEST);

    /**
     * 禁止实例化权限常量类。
     */
    private KnowledgePermissions() {
    }
}
