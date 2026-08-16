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

package space.refinex.agentark.control.iam.application;

import space.refinex.agentark.control.iam.domain.PermissionRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 定义 Control 可授予权限的唯一代码注册表，并提供内置角色权限集合。
 *
 * @author refinex
 */
public final class PermissionRegistry {

    /**
     * 允许创建新的组织根。
     */
    public static final String ORGANIZATION_CREATE = "organization:create";

    /**
     * 允许读取组织元数据。
     */
    public static final String ORGANIZATION_READ = "organization:read";

    /**
     * 允许在组织内创建项目。
     */
    public static final String PROJECT_CREATE = "project:create";

    /**
     * 允许读取项目元数据。
     */
    public static final String PROJECT_READ = "project:read";

    /**
     * 允许在项目内创建环境。
     */
    public static final String ENVIRONMENT_CREATE = "environment:create";

    /**
     * 允许读取环境元数据。
     */
    public static final String ENVIRONMENT_READ = "environment:read";

    /**
     * 允许读取项目成员关系。
     */
    public static final String MEMBERSHIP_READ = "membership:read";

    /**
     * 允许变更项目成员关系。
     */
    public static final String MEMBERSHIP_MANAGE = "membership:manage";

    /**
     * 允许读取角色与权限绑定。
     */
    public static final String ROLE_READ = "role:read";

    /**
     * 允许创建角色和变更角色绑定。
     */
    public static final String ROLE_MANAGE = "role:manage";

    /**
     * 允许读取服务账号元数据。
     */
    public static final String SERVICE_ACCOUNT_READ = "service_account:read";

    /**
     * 允许创建或停用服务账号。
     */
    public static final String SERVICE_ACCOUNT_MANAGE = "service_account:manage";

    /**
     * 允许读取 API Key 非秘密元数据。
     */
    public static final String API_KEY_READ = "api_key:read";

    /**
     * 允许创建、轮换和吊销 API Key。
     */
    public static final String API_KEY_MANAGE = "api_key:manage";

    /**
     * 允许读取项目 AI 资产目录和不可变版本。
     */
    public static final String CATALOG_READ = "catalog:read";

    /**
     * 允许创建、归档 AI 资产并追加不可变版本。
     */
    public static final String CATALOG_MANAGE = "catalog:manage";

    /**
     * 允许读取 Secret 非敏感元数据和环境绑定。
     */
    public static final String SECRET_READ = "secret:read";

    /**
     * 允许管理 Secret 元数据和环境绑定，但不能读取 Secret 值。
     */
    public static final String SECRET_MANAGE = "secret:manage";

    /**
     * 允许读取 Knowledge 元数据、文档 ACL 和可用 Revision。
     */
    public static final String KNOWLEDGE_READ = "knowledge:read";

    /**
     * 允许管理 Knowledge Base、文档、Profile 与 Revision。
     */
    public static final String KNOWLEDGE_MANAGE = "knowledge:manage";

    /**
     * 允许描述 Knowledge 摄取请求并推进受控状态机。
     */
    public static final String KNOWLEDGE_INGEST = "knowledge:ingest";

    /** 允许读取 Agent、Draft、Revision 和校验报告。 */
    public static final String AGENT_READ = "agent:read";

    /** 允许创建 Agent 并更新 Draft。 */
    public static final String AGENT_MANAGE = "agent:manage";

    /** 允许把已校验 Draft 发布为不可变 Revision。 */
    public static final String AGENT_PUBLISH = "agent:publish";

    /** 允许读取 Environment Deployment 与历史。 */
    public static final String DEPLOYMENT_READ = "deployment:read";

    /** 允许创建、Promote、Rollback、Enable 和 Disable Deployment。 */
    public static final String DEPLOYMENT_MANAGE = "deployment:manage";

    /**
     * 按固定顺序保存所有注册权限，便于 Flyway 和 OpenAPI 对齐校验。
     */
    private static final Map<String, Definition> DEFINITIONS = definitions();

    /**
     * 组织所有者拥有的 Phase 07 权限。
     */
    private static final Set<String> ORGANIZATION_OWNER = Set.copyOf(DEFINITIONS.keySet());

    /**
     * 项目管理员拥有除创建组织外的项目权限。
     */
    private static final Set<String> PROJECT_ADMIN = Set.of(
        ORGANIZATION_READ,
        PROJECT_READ,
        ENVIRONMENT_CREATE,
        ENVIRONMENT_READ,
        MEMBERSHIP_READ,
        MEMBERSHIP_MANAGE,
        ROLE_READ,
        ROLE_MANAGE,
        SERVICE_ACCOUNT_READ,
        SERVICE_ACCOUNT_MANAGE,
        API_KEY_READ,
        API_KEY_MANAGE,
        CATALOG_READ,
        CATALOG_MANAGE,
        SECRET_READ,
        SECRET_MANAGE,
        KNOWLEDGE_READ,
        KNOWLEDGE_MANAGE,
        KNOWLEDGE_INGEST,
        AGENT_READ,
        AGENT_MANAGE,
        AGENT_PUBLISH,
        DEPLOYMENT_READ,
        DEPLOYMENT_MANAGE);

    /**
     * 项目开发者拥有的非授权管理权限。
     */
    private static final Set<String> PROJECT_DEVELOPER = Set.of(
        ORGANIZATION_READ,
        PROJECT_READ,
        ENVIRONMENT_READ,
        SERVICE_ACCOUNT_READ,
        API_KEY_READ,
        CATALOG_READ,
        CATALOG_MANAGE,
        KNOWLEDGE_READ,
        KNOWLEDGE_MANAGE,
        KNOWLEDGE_INGEST,
        AGENT_READ,
        AGENT_MANAGE,
        AGENT_PUBLISH,
        DEPLOYMENT_READ,
        DEPLOYMENT_MANAGE);

    /**
     * 项目只读角色拥有的最低读取权限。
     */
    private static final Set<String> PROJECT_VIEWER = Set.of(
        ORGANIZATION_READ,
        PROJECT_READ,
        ENVIRONMENT_READ,
        MEMBERSHIP_READ,
        ROLE_READ,
        SERVICE_ACCOUNT_READ,
        API_KEY_READ,
        CATALOG_READ,
        KNOWLEDGE_READ,
        AGENT_READ,
        DEPLOYMENT_READ);

    /**
     * 禁止实例化静态权限注册表。
     */
    private PermissionRegistry() {
    }

    /**
     * 返回全部权限定义的不可变视图。
     *
     * @return 按稳定顺序排列的权限定义
     */
    public static Map<String, Definition> definitionsView() {
        return DEFINITIONS;
    }

    /**
     * 校验一组权限全部来自当前注册表。
     *
     * @param permissionKeys 待校验权限键
     * @return 防御性复制后的权限集合
     * @throws IllegalArgumentException 当集合为空或包含未知权限时抛出
     */
    public static Set<String> requireRegistered(Set<String> permissionKeys) {
        Set<String> checked = Set.copyOf(
            java.util.Objects.requireNonNull(permissionKeys, "permissionKeys must not be null"));
        if (checked.isEmpty() || !DEFINITIONS.keySet().containsAll(checked)) {
            throw new IllegalArgumentException("permission keys must be registered and non-empty");
        }
        return checked;
    }

    /**
     * 返回组织所有者内置角色权限。
     *
     * @return 不可变权限集合
     */
    public static Set<String> organizationOwnerPermissions() {
        return ORGANIZATION_OWNER;
    }

    /**
     * 返回项目管理员内置角色权限。
     *
     * @return 不可变权限集合
     */
    public static Set<String> projectAdminPermissions() {
        return PROJECT_ADMIN;
    }

    /**
     * 返回项目开发者内置角色权限。
     *
     * @return 不可变权限集合
     */
    public static Set<String> projectDeveloperPermissions() {
        return PROJECT_DEVELOPER;
    }

    /**
     * 返回项目只读内置角色权限。
     *
     * @return 不可变权限集合
     */
    public static Set<String> projectViewerPermissions() {
        return PROJECT_VIEWER;
    }

    /**
     * 构建固定权限定义表。
     *
     * @return 不可变且有序的权限表
     */
    private static Map<String, Definition> definitions() {
        Map<String, Definition> values = new LinkedHashMap<>();
        values.put(ORGANIZATION_CREATE, new Definition("创建组织", PermissionRiskLevel.HIGH));
        values.put(ORGANIZATION_READ, new Definition("读取组织", PermissionRiskLevel.LOW));
        values.put(PROJECT_CREATE, new Definition("创建项目", PermissionRiskLevel.MEDIUM));
        values.put(PROJECT_READ, new Definition("读取项目", PermissionRiskLevel.LOW));
        values.put(ENVIRONMENT_CREATE, new Definition("创建环境", PermissionRiskLevel.MEDIUM));
        values.put(ENVIRONMENT_READ, new Definition("读取环境", PermissionRiskLevel.LOW));
        values.put(MEMBERSHIP_READ, new Definition("读取成员关系", PermissionRiskLevel.LOW));
        values.put(MEMBERSHIP_MANAGE, new Definition("管理成员关系", PermissionRiskLevel.HIGH));
        values.put(ROLE_READ, new Definition("读取角色", PermissionRiskLevel.LOW));
        values.put(ROLE_MANAGE, new Definition("管理角色和绑定", PermissionRiskLevel.HIGH));
        values.put(SERVICE_ACCOUNT_READ, new Definition("读取服务账号", PermissionRiskLevel.LOW));
        values.put(SERVICE_ACCOUNT_MANAGE, new Definition("管理服务账号", PermissionRiskLevel.HIGH));
        values.put(API_KEY_READ, new Definition("读取 API Key 元数据", PermissionRiskLevel.MEDIUM));
        values.put(API_KEY_MANAGE, new Definition("管理 API Key", PermissionRiskLevel.HIGH));
        values.put(CATALOG_READ, new Definition("读取 AI 资产目录", PermissionRiskLevel.LOW));
        values.put(CATALOG_MANAGE, new Definition("管理 AI 资产目录", PermissionRiskLevel.HIGH));
        values.put(SECRET_READ, new Definition("读取 Secret 非敏感元数据", PermissionRiskLevel.MEDIUM));
        values.put(SECRET_MANAGE, new Definition("管理 Secret 元数据与环境绑定", PermissionRiskLevel.HIGH));
        values.put(KNOWLEDGE_READ, new Definition("读取 Knowledge 元数据", PermissionRiskLevel.LOW));
        values.put(KNOWLEDGE_MANAGE, new Definition("管理 Knowledge 元数据", PermissionRiskLevel.HIGH));
        values.put(KNOWLEDGE_INGEST, new Definition("描述 Knowledge 摄取请求", PermissionRiskLevel.HIGH));
        values.put(AGENT_READ, new Definition("读取 Agent 发布资源", PermissionRiskLevel.LOW));
        values.put(AGENT_MANAGE, new Definition("管理 Agent Draft", PermissionRiskLevel.HIGH));
        values.put(AGENT_PUBLISH, new Definition("发布 Agent Revision", PermissionRiskLevel.HIGH));
        values.put(DEPLOYMENT_READ, new Definition("读取 Environment Deployment", PermissionRiskLevel.LOW));
        values.put(DEPLOYMENT_MANAGE, new Definition("管理 Environment Deployment", PermissionRiskLevel.HIGH));
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 表示权限注册项的描述和风险元数据。
     *
     * @param description 中文职责说明
     * @param riskLevel   风险等级
     * @author refinex
     */
    public record Definition(String description, PermissionRiskLevel riskLevel) {

        /**
         * 校验注册元数据完整性。
         *
         * @param description 职责说明
         * @param riskLevel   风险等级
         */
        public Definition {
            if (description == null || description.isBlank() || description.length() > 255) {
                throw new IllegalArgumentException(
                    "permission description must contain 1 to 255 characters");
            }
            java.util.Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        }
    }
}
