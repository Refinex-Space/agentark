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

package space.refinex.agentark.control.iam.adapter.in.security;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.HexValue;
import space.refinex.agentark.foundation.web.RequestContextAccessor;

import java.util.HexFormat;
import java.util.Set;

/**
 * 从已授权请求上下文生成 organization_id 二进制条件，作为未来非显式 Mapper 的纵深防御。
 *
 * <p>Phase 07 Mapper 自身携带组织与项目 Scope 并显式忽略插件，避免认证前查询产生循环。
 *
 * @author refinex
 */
public final class ControlTenantLineHandler implements TenantLineHandler {

    /**
     * 没有 organization_id 的全局表或规范化关联表。
     */
    private static final Set<String> GLOBAL_TABLES = Set.of(
        "organization",
        "user_identity",
        "permission",
        "role_permission",
        "api_key_scope",
        "flyway_schema_history");

    /**
     * 当前同步请求上下文访问器。
     */
    private final RequestContextAccessor requestContextAccessor;

    /**
     * 创建 Control Tenant SQL 处理器。
     *
     * @param requestContextAccessor 请求上下文访问器
     */
    public ControlTenantLineHandler(RequestContextAccessor requestContextAccessor) {
        this.requestContextAccessor = java.util.Objects.requireNonNull(
            requestContextAccessor, "requestContextAccessor must not be null");
    }

    /**
     * 返回当前已授权 Organization UUIDv7 的 MySQL 二进制字面量；缺少上下文时失败关闭。
     *
     * @return 形如 0x... 的 16 字节表达式
     */
    @Override
    public Expression getTenantId() {
        var organizationId = requestContextAccessor.current()
            .flatMap(context -> context.tenant())
            .map(tenant -> tenant.organizationId().value())
            .orElseThrow(() -> new IllegalStateException(
                "authorized tenant context is required for implicit tenant SQL"));
        byte[] bytes = java.nio.ByteBuffer.allocate(16)
            .putLong(organizationId.getMostSignificantBits())
            .putLong(organizationId.getLeastSignificantBits())
            .array();
        return new HexValue("0x" + HexFormat.of().formatHex(bytes));
    }

    /**
     * 固定隐式租户列为 organization_id。
     *
     * @return 组织租户列名
     */
    @Override
    public String getTenantIdColumn() {
        return "organization_id";
    }

    /**
     * 忽略没有组织列的全局或规范化关联表。
     *
     * @param tableName MyBatis-Plus 解析出的裸表名
     * @return 不应追加组织条件时为 {@code true}
     */
    @Override
    public boolean ignoreTable(String tableName) {
        return GLOBAL_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT));
    }
}
