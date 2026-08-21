CREATE TABLE identity_account (
    id BINARY(16) NOT NULL COMMENT '内置身份账号 UUIDv7 主键，同时作为本地 JWT Subject',
    username VARCHAR(64) NOT NULL COMMENT '用户展示与登录使用的原始用户名，不区分大小写',
    username_normalized VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '小写规范化用户名，全局唯一且只允许字母数字点横线下划线',
    email VARCHAR(320) NULL COMMENT '用户展示邮箱，可为空且不作为授权事实',
    email_normalized VARCHAR(320) NULL COMMENT '小写规范化邮箱，可为空且非空时全局唯一',
    display_name VARCHAR(128) NOT NULL COMMENT '控制台展示名称，不参与认证或授权判断',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账号状态：ACTIVE=启用，SUSPENDED=暂停，DISABLED=禁用',
    password_change_required BOOLEAN NOT NULL COMMENT '是否必须在取得完整会话前修改临时密码：0=否，1=是',
    auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '认证版本；改密、重置、暂停或禁用时递增以使旧会话失效',
    last_login_at TIMESTAMP(6) NULL COMMENT '最近一次成功取得完整会话的 UTC 时间，未登录时为空',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '账号管理乐观锁版本，从 0 开始且只允许递增',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定标识；Bootstrap 使用 agentark-bootstrap',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近更新主体稳定标识，不保存展示名或凭据',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建 UTC 时间，微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最近更新 UTC 时间，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_account_username UNIQUE (username_normalized),
    CONSTRAINT uk_identity_account_email UNIQUE (email_normalized),
    CONSTRAINT ck_identity_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_identity_account_auth_version CHECK (auth_version >= 0),
    CONSTRAINT ck_identity_account_version CHECK (version >= 0),
    INDEX idx_identity_account_status (status, id),
    INDEX idx_identity_account_updated (updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Gateway 独占的内置用户账号，不保存业务租户角色';

CREATE TABLE identity_password_credential (
    account_id BINARY(16) NOT NULL COMMENT '所属账号 UUIDv7，同时为一对一主键',
    password_hash VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Argon2id PHC 格式加盐摘要，禁止存放明文或可逆密文',
    hash_algorithm VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密码摘要算法：ARGON2ID=Argon2id',
    hash_version INT NOT NULL COMMENT '摘要参数版本，用于登录成功后的受控 Rehash',
    pepper_version INT NOT NULL COMMENT '部署 Secret 中 Pepper 的版本号；0 表示首版且不在数据库存 Pepper',
    temporary BOOLEAN NOT NULL COMMENT '是否为一次性临时密码：0=正式密码，1=首次登录或重置临时密码',
    changed_at TIMESTAMP(6) NOT NULL COMMENT '密码最近设置 UTC 时间，微秒精度',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '凭据乐观锁版本，从 0 开始且只允许递增',
    PRIMARY KEY (account_id),
    CONSTRAINT fk_identity_password_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_password_algorithm CHECK (hash_algorithm = 'ARGON2ID'),
    CONSTRAINT ck_identity_password_hash_version CHECK (hash_version >= 1),
    CONSTRAINT ck_identity_password_pepper_version CHECK (pepper_version >= 0),
    CONSTRAINT ck_identity_password_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内置账号当前密码摘要，一账号一行且永不保存明文';

CREATE TABLE identity_password_history (
    account_id BINARY(16) NOT NULL COMMENT '所属账号 UUIDv7',
    history_sequence BIGINT NOT NULL COMMENT '账号内单调历史序号，从 1 开始',
    password_hash VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '历史 Argon2id PHC 摘要，仅用于阻止近期重复密码',
    hash_version INT NOT NULL COMMENT '生成该历史摘要时的参数版本',
    pepper_version INT NOT NULL COMMENT '校验该历史摘要需要的 Pepper 版本引用',
    changed_at TIMESTAMP(6) NOT NULL COMMENT '该密码被替换的 UTC 时间',
    PRIMARY KEY (account_id, history_sequence),
    CONSTRAINT fk_identity_password_history_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_password_history_sequence CHECK (history_sequence >= 1),
    CONSTRAINT ck_identity_password_history_hash_version CHECK (hash_version >= 1),
    INDEX idx_identity_password_history_recent (account_id, changed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有限密码历史摘要，不允许恢复或展示旧密码';

CREATE TABLE identity_permission (
    permission_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台身份权限稳定键，由 Flyway 固定注册且业务请求不可扩展',
    description VARCHAR(255) NOT NULL COMMENT '权限中文职责说明',
    risk_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '风险等级：LOW=低风险，MEDIUM=中风险，HIGH=高风险',
    created_at TIMESTAMP(6) NOT NULL COMMENT '权限注册 UTC 时间',
    PRIMARY KEY (permission_key),
    CONSTRAINT ck_identity_permission_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内置身份平台权限注册表，不包含租户业务权限';

CREATE TABLE identity_role (
    id BINARY(16) NOT NULL COMMENT '平台身份角色 UUIDv7 主键',
    role_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台内唯一角色键',
    name VARCHAR(128) NOT NULL COMMENT '角色展示名称',
    built_in BOOLEAN NOT NULL COMMENT '是否为 Flyway 管理的内置角色：0=自定义，1=内置',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色状态：ACTIVE=启用，DISABLED=禁用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '角色乐观锁版本',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建 UTC 时间',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最近更新 UTC 时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_role_key UNIQUE (role_key),
    CONSTRAINT ck_identity_role_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_identity_role_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内置身份平台角色，不代替 Control 租户角色';

CREATE TABLE identity_role_permission (
    role_id BINARY(16) NOT NULL COMMENT '平台身份角色 UUIDv7',
    permission_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已注册平台身份权限键',
    created_at TIMESTAMP(6) NOT NULL COMMENT '绑定创建 UTC 时间',
    PRIMARY KEY (role_id, permission_key),
    CONSTRAINT fk_identity_role_permission_role FOREIGN KEY (role_id) REFERENCES identity_role (id),
    CONSTRAINT fk_identity_role_permission_permission FOREIGN KEY (permission_key) REFERENCES identity_permission (permission_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台身份角色与权限的多对多绑定';

CREATE TABLE identity_account_role (
    account_id BINARY(16) NOT NULL COMMENT '内置身份账号 UUIDv7',
    role_id BINARY(16) NOT NULL COMMENT '平台身份角色 UUIDv7',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授予者稳定 Subject',
    created_at TIMESTAMP(6) NOT NULL COMMENT '授予 UTC 时间',
    PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_identity_account_role_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT fk_identity_account_role_role FOREIGN KEY (role_id) REFERENCES identity_role (id),
    INDEX idx_identity_account_role_role (role_id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号与平台身份角色绑定，不包含 Organization 或 Project Scope';

CREATE TABLE identity_bootstrap_state (
    singleton_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '单例键，合法值固定为 built-in-identity',
    state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '初始化状态：UNINITIALIZED=未初始化，INITIALIZING=执行中，INITIALIZED=完成，FAILED=失败',
    admin_account_id BINARY(16) NULL COMMENT '初始化完成后的平台管理员账号 UUIDv7，未完成时为空',
    initialized_at TIMESTAMP(6) NULL COMMENT '初始化完成 UTC 时间，未完成时为空',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '单例状态乐观锁版本',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最近状态更新时间',
    PRIMARY KEY (singleton_key),
    CONSTRAINT fk_identity_bootstrap_admin FOREIGN KEY (admin_account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_bootstrap_key CHECK (singleton_key = 'built-in-identity'),
    CONSTRAINT ck_identity_bootstrap_state CHECK (state IN ('UNINITIALIZED', 'INITIALIZING', 'INITIALIZED', 'FAILED')),
    CONSTRAINT ck_identity_bootstrap_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次性内置身份初始化状态，完成后不允许重新抢占';

CREATE TABLE identity_login_guard (
    account_id BINARY(16) NOT NULL COMMENT '受保护账号 UUIDv7',
    failure_count INT NOT NULL DEFAULT 0 COMMENT '当前连续失败次数，成功登录或人工解锁后清零',
    window_started_at TIMESTAMP(6) NULL COMMENT '当前失败统计窗口起始 UTC 时间，无失败时为空',
    last_failure_at TIMESTAMP(6) NULL COMMENT '最近失败 UTC 时间，无失败时为空',
    locked_until TIMESTAMP(6) NULL COMMENT '自动锁定截止 UTC 时间，未锁定时为空',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '并发原子更新版本',
    PRIMARY KEY (account_id),
    CONSTRAINT fk_identity_login_guard_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_login_guard_failures CHECK (failure_count >= 0),
    CONSTRAINT ck_identity_login_guard_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MySQL 权威账号失败次数与锁定状态，Redis 只做快速限流';

CREATE TABLE identity_security_event (
    id BINARY(16) NOT NULL COMMENT '安全事件 UUIDv7 主键，按时间有序生成',
    account_id BINARY(16) NULL COMMENT '目标账号 UUIDv7；未知用户名登录失败时为空',
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型：LOGIN_SUCCEEDED、LOGIN_FAILED、ACCOUNT_LOCKED、ACCOUNT_UNLOCKED、PASSWORD_CHANGED、PASSWORD_RESET、ACCOUNT_CREATED、ACCOUNT_SUSPENDED、ACCOUNT_DISABLED、ROLE_GRANTED、ROLE_REVOKED',
    result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件结果：SUCCESS=成功，FAILURE=失败，DENIED=拒绝',
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '操作者稳定 Subject；匿名登录失败时为空',
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联请求标识，可为空且不得包含凭据',
    remote_address_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户端地址不可逆 SHA-256 摘要，可为空且不保存原始地址',
    user_agent_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'User-Agent 不可逆 SHA-256 摘要，可为空且不保存原文',
    detail_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '稳定脱敏原因码，不保存密码、Cookie、Token 或响应正文',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '事件发生 UTC 时间，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_identity_security_event_account FOREIGN KEY (account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_security_event_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    INDEX idx_identity_security_event_account (account_id, occurred_at DESC, id),
    INDEX idx_identity_security_event_type (event_type, occurred_at DESC, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Gateway 独占的追加式身份安全事件，不保存敏感正文';

CREATE TABLE identity_idempotency (
    id BINARY(16) NOT NULL COMMENT '身份管理幂等记录 UUIDv7 主键',
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起操作的稳定 Subject',
    operation_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型：CREATE_ACCOUNT、RESET_PASSWORD、CHANGE_STATUS、UNLOCK_ACCOUNT',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方提供的幂等键，按 Actor 与操作类型隔离',
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范请求 SHA-256，用于拒绝同键异参',
    target_account_id BINARY(16) NULL COMMENT '成功操作目标账号 UUIDv7，执行前可为空',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等状态：STARTED=已开始，SUCCEEDED=成功，FAILED=失败',
    created_at TIMESTAMP(6) NOT NULL COMMENT '记录创建 UTC 时间',
    completed_at TIMESTAMP(6) NULL COMMENT '操作完成 UTC 时间，执行中为空',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '幂等记录最早可清理时间，不代表业务事实失效',
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_idempotency_actor_key UNIQUE (actor_subject, operation_type, idempotency_key),
    CONSTRAINT fk_identity_idempotency_account FOREIGN KEY (target_account_id) REFERENCES identity_account (id),
    CONSTRAINT ck_identity_idempotency_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED')),
    INDEX idx_identity_idempotency_expiry (expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='身份管理写操作幂等事实，不缓存或重放明文临时密码';

CREATE TABLE identity_outbox (
    id BINARY(16) NOT NULL COMMENT 'Identity Outbox UUIDv7 主键，按时间有序生成',
    aggregate_id BINARY(16) NOT NULL COMMENT '账号聚合 UUIDv7',
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型：IDENTITY_ACCOUNT_CREATED、IDENTITY_ACCOUNT_UPDATED、IDENTITY_ACCOUNT_DISABLED',
    payload_json JSON NOT NULL COMMENT '只含 Issuer、Subject、展示名、邮箱和状态的非敏感投影',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '投递状态：PENDING=待投递，CLAIMED=已领取，PUBLISHED=已发布，FAILED=终态失败',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已执行投递尝试次数，从 0 开始',
    available_at TIMESTAMP(6) NOT NULL COMMENT '下次允许领取 UTC 时间',
    claimed_by VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前领取者实例标识，未领取时为空',
    claimed_until TIMESTAMP(6) NULL COMMENT '领取租约截止 UTC 时间，未领取时为空',
    published_at TIMESTAMP(6) NULL COMMENT '成功投递 UTC 时间，未完成时为空',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Outbox 创建 UTC 时间',
    PRIMARY KEY (id),
    CONSTRAINT ck_identity_outbox_type CHECK (event_type IN ('IDENTITY_ACCOUNT_CREATED', 'IDENTITY_ACCOUNT_UPDATED', 'IDENTITY_ACCOUNT_DISABLED')),
    CONSTRAINT ck_identity_outbox_status CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_identity_outbox_attempts CHECK (attempts >= 0),
    INDEX idx_identity_outbox_claim (status, available_at, id),
    INDEX idx_identity_outbox_aggregate (aggregate_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号与 Control 用户投影之间的持久 Outbox，不保存凭据';

CREATE TABLE identity_signing_key (
    kid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'JWT Key ID，必须与公开 JWK 和部署私钥一致',
    algorithm VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '签名算法：RS256=RSA SHA-256',
    public_jwk_json JSON NOT NULL COMMENT '可公开 RSA JWK，不包含私钥参数',
    private_key_ref VARCHAR(512) NOT NULL COMMENT '部署 SecretRef，不保存私钥正文',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密钥状态：ACTIVE=签发中，RETIRING=仅验证，RETIRED=停用',
    not_before TIMESTAMP(6) NOT NULL COMMENT '最早生效 UTC 时间',
    not_after TIMESTAMP(6) NULL COMMENT '停止验证 UTC 时间，活动密钥可为空',
    created_at TIMESTAMP(6) NOT NULL COMMENT '元数据创建 UTC 时间',
    PRIMARY KEY (kid),
    CONSTRAINT ck_identity_signing_key_algorithm CHECK (algorithm = 'RS256'),
    CONSTRAINT ck_identity_signing_key_status CHECK (status IN ('ACTIVE', 'RETIRING', 'RETIRED')),
    INDEX idx_identity_signing_key_status (status, not_before, kid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内部 JWT 签名密钥公开元数据，私钥只通过 SecretRef 解析';

INSERT INTO identity_permission (permission_key, description, risk_level, created_at) VALUES
    ('identity.account.read', '读取内置身份账号非敏感元数据', 'MEDIUM', '2026-08-21 00:00:00.000000'),
    ('identity.account.manage', '创建、暂停、启用和禁用内置身份账号', 'HIGH', '2026-08-21 00:00:00.000000'),
    ('identity.credential.reset', '重置其他用户密码并强制首次改密', 'HIGH', '2026-08-21 00:00:00.000000'),
    ('identity.account.unlock', '解除账号登录失败锁定', 'HIGH', '2026-08-21 00:00:00.000000'),
    ('identity.security_event.read', '读取身份安全事件', 'HIGH', '2026-08-21 00:00:00.000000'),
    ('organization:create', '创建 AgentArk Organization 根资源', 'HIGH', '2026-08-21 00:00:00.000000');

INSERT INTO identity_role (id, role_key, name, built_in, status, version, created_at, updated_at) VALUES
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101', '-', '')), 'platform-admin', '平台管理员', TRUE, 'ACTIVE', 0, '2026-08-21 00:00:00.000000', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity-admin', '身份管理员', TRUE, 'ACTIVE', 0, '2026-08-21 00:00:00.000000', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000103', '-', '')), 'identity-viewer', '身份审计员', TRUE, 'ACTIVE', 0, '2026-08-21 00:00:00.000000', '2026-08-21 00:00:00.000000');

INSERT INTO identity_role_permission (role_id, permission_key, created_at)
SELECT UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101', '-', '')), permission_key, '2026-08-21 00:00:00.000000'
FROM identity_permission;

INSERT INTO identity_role_permission (role_id, permission_key, created_at) VALUES
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity.account.read', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity.account.manage', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity.credential.reset', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity.account.unlock', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000102', '-', '')), 'identity.security_event.read', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000103', '-', '')), 'identity.account.read', '2026-08-21 00:00:00.000000'),
    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000103', '-', '')), 'identity.security_event.read', '2026-08-21 00:00:00.000000');

INSERT INTO identity_bootstrap_state (singleton_key, state, admin_account_id, initialized_at, version, updated_at)
VALUES ('built-in-identity', 'UNINITIALIZED', NULL, NULL, 0, '2026-08-21 00:00:00.000000');
