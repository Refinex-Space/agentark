-- Phase 07 创建 Control IAM 与租户授权事实；所有表仅属于 agentark_control。

-- 组织是租户资源树根，Slug 全局唯一。
CREATE TABLE organization (
    id BINARY(16) NOT NULL COMMENT '组织 UUIDv7 主键，按时间有序生成',
    slug VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '组织全局唯一标识，仅保存规范化 ASCII Slug',
    name VARCHAR(128) NOT NULL COMMENT '组织显示名称',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '组织状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_organization_slug UNIQUE (slug),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_organization_version CHECK (version >= 0),
    INDEX idx_organization_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织租户根及其生命周期状态';

-- 项目显式携带组织归属，复合唯一键供子资源验证组织链路。
CREATE TABLE project (
    id BINARY(16) NOT NULL COMMENT '项目 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，建立租户所有权边界',
    slug VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '组织内唯一项目标识，仅保存规范化 ASCII Slug',
    name VARCHAR(128) NOT NULL COMMENT '项目显示名称',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_project_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT uk_project_id_org UNIQUE (id, organization_id),
    CONSTRAINT fk_project_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT ck_project_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_project_version CHECK (version >= 0),
    INDEX idx_project_org_status (organization_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织所属项目及其生命周期状态';

-- 环境同时保存组织和项目，复合外键拒绝伪造项目归属。
CREATE TABLE environment (
    id BINARY(16) NOT NULL COMMENT '环境 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    environment_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一环境标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT '环境显示名称',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '环境状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_environment_project_key UNIQUE (project_id, environment_key),
    CONSTRAINT uk_environment_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_environment_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_environment_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_environment_version CHECK (version >= 0),
    INDEX idx_environment_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目所属运行环境及其生命周期状态';

-- 外部用户只保存 Issuer/Subject 映射和展示元数据，不保存 Token 或密码。
CREATE TABLE user_identity (
    id BINARY(16) NOT NULL COMMENT '外部用户身份映射 UUIDv7 主键，按时间有序生成',
    issuer VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OIDC Issuer 规范化标识，与 Subject 共同唯一',
    subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OIDC Subject 稳定标识，不保存 Token',
    display_name VARCHAR(128) NULL COMMENT '身份提供方展示名称，可为空且不作为授权依据',
    email VARCHAR(320) NULL COMMENT '身份提供方邮箱，可为空且不作为唯一登录标识',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户身份状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    last_seen_at TIMESTAMP(6) NOT NULL COMMENT '最近一次成功映射时间，UTC，微秒精度',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_identity_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT ck_user_identity_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_user_identity_version CHECK (version >= 0),
    INDEX idx_user_identity_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部 OIDC Issuer 与 Subject 的本地身份映射';

-- 服务账号属于单个项目，不存放任何凭据。
CREATE TABLE service_account (
    id BINARY(16) NOT NULL COMMENT '服务账号 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    name VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一服务账号名称，仅保存规范化 ASCII 名称',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务账号状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_service_account_project_name UNIQUE (project_id, name),
    CONSTRAINT uk_service_account_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_service_account_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_service_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_service_account_version CHECK (version >= 0),
    INDEX idx_service_account_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目所属非人类服务身份，不存放任何凭据';

-- 成员关系使用多态主体 UUID，应用层校验 UserIdentity 或 ServiceAccount 存在。
CREATE TABLE membership (
    id BINARY(16) NOT NULL COMMENT '项目成员关系 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    principal_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成员主体类型：USER=外部用户，SERVICE_ACCOUNT=服务账号',
    principal_id BINARY(16) NOT NULL COMMENT '成员主体 UUIDv7，由主体类型决定引用用户身份或服务账号',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成员关系状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_membership_project_principal UNIQUE (project_id, principal_type, principal_id),
    CONSTRAINT fk_membership_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_membership_principal_type CHECK (principal_type IN ('USER', 'SERVICE_ACCOUNT')),
    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_membership_version CHECK (version >= 0),
    INDEX idx_membership_scope_status (organization_id, project_id, status, id),
    INDEX idx_membership_principal (principal_type, principal_id, status, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目与用户或服务账号之间的有效成员关系';

-- 权限注册表由版本化 Flyway 管理，业务请求不能任意创建权限键。
CREATE TABLE permission (
    id BINARY(16) NOT NULL COMMENT '权限 UUIDv7 主键，由版本化迁移固定分配',
    permission_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限注册键，采用资源与动作组成的稳定 ASCII 标识',
    description VARCHAR(255) NOT NULL COMMENT '权限中文用途说明',
    risk_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限风险等级：LOW=低风险，MEDIUM=中风险，HIGH=高风险',
    created_at TIMESTAMP(6) NOT NULL COMMENT '注册时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_permission_key UNIQUE (permission_key),
    CONSTRAINT ck_permission_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='由 Flyway 管理且业务请求不可任意扩展的权限注册表';

-- 角色可属于组织或项目；零 UUID 只用于生成列解决 NULL 唯一语义，合法 UUIDv7 不会与其碰撞。
CREATE TABLE role (
    id BINARY(16) NOT NULL COMMENT '角色 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NULL COMMENT '所属项目 UUIDv7；为空表示组织级角色',
    project_scope_id BINARY(16) GENERATED ALWAYS AS
        (COALESCE(project_id, X'00000000000000000000000000000000')) STORED COMMENT '统一项目级与组织级唯一约束的生成列；零 UUID 表示组织级作用域',
    role_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '作用域内唯一角色标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT '角色显示名称',
    built_in BOOLEAN NOT NULL COMMENT '是否为内置角色：0=自定义角色，1=内置角色',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_role_scope_key UNIQUE (organization_id, project_scope_id, role_key),
    CONSTRAINT uk_role_id_scope UNIQUE (id, organization_id, project_scope_id),
    CONSTRAINT fk_role_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_role_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_role_version CHECK (version >= 0),
    INDEX idx_role_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织级或项目级的内置与自定义授权角色';

-- 角色权限采用规范化关联，授权查询不依赖 JSON Path。
CREATE TABLE role_permission (
    role_id BINARY(16) NOT NULL COMMENT '角色 UUIDv7',
    permission_id BINARY(16) NOT NULL COMMENT '权限 UUIDv7',
    created_at TIMESTAMP(6) NOT NULL COMMENT '授权关系创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建授权关系主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id),
    INDEX idx_role_permission_permission (permission_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色与权限注册项之间的规范化多对多关系';

-- Role Binding 保存组织/项目上下文以及具体 Scope ID，防止只有裸资源 ID 的授权。
CREATE TABLE role_binding (
    id BINARY(16) NOT NULL COMMENT '角色绑定 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NULL COMMENT '所属项目 UUIDv7；组织级绑定时为空',
    role_id BINARY(16) NOT NULL COMMENT '被绑定角色 UUIDv7',
    principal_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权主体类型：USER=外部用户，SERVICE_ACCOUNT=服务账号',
    principal_id BINARY(16) NOT NULL COMMENT '授权主体 UUIDv7，由主体类型决定引用用户身份或服务账号',
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权作用域类型：ORGANIZATION=组织，PROJECT=项目，ENVIRONMENT=环境',
    scope_id BINARY(16) NOT NULL COMMENT '授权作用域资源 UUIDv7，必须与作用域类型和所有权链一致',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_role_binding_principal_scope UNIQUE
        (principal_type, principal_id, role_id, scope_type, scope_id),
    CONSTRAINT fk_role_binding_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_role_binding_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_role_binding_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT ck_role_binding_principal_type CHECK (principal_type IN ('USER', 'SERVICE_ACCOUNT')),
    CONSTRAINT ck_role_binding_scope_type CHECK (scope_type IN ('ORGANIZATION', 'PROJECT', 'ENVIRONMENT')),
    CONSTRAINT ck_role_binding_project_presence CHECK
        ((scope_type = 'ORGANIZATION' AND project_id IS NULL)
            OR (scope_type IN ('PROJECT', 'ENVIRONMENT') AND project_id IS NOT NULL)),
    CONSTRAINT ck_role_binding_version CHECK (version >= 0),
    INDEX idx_role_binding_scope_principal
        (organization_id, project_id, principal_type, principal_id, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主体、角色与组织项目环境作用域之间的授权绑定';

-- API Key 只保存公开前缀和 32 字节摘要；明文永不进入数据库。
CREATE TABLE api_key (
    id BINARY(16) NOT NULL COMMENT 'API Key UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    service_account_id BINARY(16) NOT NULL COMMENT '所属服务账号 UUIDv7',
    name VARCHAR(128) NOT NULL COMMENT 'API Key 显示名称，不包含秘密材料',
    prefix CHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公开检索前缀，为 ark_ 后的 12 个 ASCII 字符，不具备认证能力',
    digest BINARY(32) NOT NULL COMMENT 'API Key 完整凭据的 SHA-256 摘要，仅用于常量时间认证比较',
    expires_at TIMESTAMP(6) NULL COMMENT '到期时间，UTC，微秒精度；为空表示不设置自动到期',
    revoked_at TIMESTAMP(6) NULL COMMENT '吊销时间，UTC，微秒精度；为空表示尚未吊销',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_api_key_prefix UNIQUE (prefix),
    CONSTRAINT uk_api_key_digest UNIQUE (digest),
    CONSTRAINT fk_api_key_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_api_key_service_account_scope
        FOREIGN KEY (service_account_id, project_id, organization_id)
        REFERENCES service_account (id, project_id, organization_id),
    CONSTRAINT ck_api_key_version CHECK (version >= 0),
    CONSTRAINT ck_api_key_expiry CHECK (expires_at IS NULL OR expires_at > created_at),
    INDEX idx_api_key_scope_status (organization_id, project_id, revoked_at, expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务账号 API Key 的公开元数据、摘要、有效期和吊销状态';

-- API Key Scope 规范化保存，并通过权限外键拒绝未知权限。
CREATE TABLE api_key_scope (
    api_key_id BINARY(16) NOT NULL COMMENT 'API Key UUIDv7 标识',
    permission_id BINARY(16) NOT NULL COMMENT '允许该 API Key 使用的权限 UUIDv7',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Scope 创建时间，UTC，微秒精度',
    PRIMARY KEY (api_key_id, permission_id),
    CONSTRAINT fk_api_key_scope_key FOREIGN KEY (api_key_id) REFERENCES api_key (id),
    CONSTRAINT fk_api_key_scope_permission FOREIGN KEY (permission_id) REFERENCES permission (id),
    INDEX idx_api_key_scope_permission (permission_id, api_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API Key 权限收窄范围的规范化关联';

-- 权限 ID 是固定合法 UUIDv7；新增权限必须通过后续 Flyway 前向追加。
INSERT INTO permission (id, permission_key, description, risk_level, created_at) VALUES
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000001', '-', '')), 'organization:create', '创建组织', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000002', '-', '')), 'organization:read', '读取组织', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000003', '-', '')), 'project:create', '创建项目', 'MEDIUM', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000004', '-', '')), 'project:read', '读取项目', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000005', '-', '')), 'environment:create', '创建环境', 'MEDIUM', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000006', '-', '')), 'environment:read', '读取环境', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000007', '-', '')), 'membership:read', '读取成员关系', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000008', '-', '')), 'membership:manage', '管理成员关系', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000009', '-', '')), 'role:read', '读取角色', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000a', '-', '')), 'role:manage', '管理角色和绑定', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000b', '-', '')), 'service_account:read', '读取服务账号', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000c', '-', '')), 'service_account:manage', '管理服务账号', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000d', '-', '')), 'api_key:read', '读取 API Key 元数据', 'MEDIUM', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000e', '-', '')), 'api_key:manage', '管理 API Key', 'HIGH', '2026-08-16 00:00:00.000000');
