-- 先以旧本地 Keycloak 管理员的非敏感字段补建 Gateway Built-in Identity 固定投影。
INSERT IGNORE INTO user_identity
    (id, issuer, subject, display_name, email, status, last_seen_at,
     version, created_at, updated_at)
SELECT
    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001', '-', '')),
    'https://identity.agentark.local',
    legacy.subject,
    COALESCE(legacy.display_name, 'AgentArk Administrator'),
    legacy.email,
    legacy.status,
    legacy.last_seen_at,
    0,
    legacy.created_at,
    UTC_TIMESTAMP(6)
FROM user_identity legacy
WHERE legacy.issuer = 'http://localhost:8180/realms/agentark'
  AND legacy.subject = '019d0000-0000-7000-8000-000000000001';

-- 新投影先持久化，避免 Gateway Outbox 尚未投递时出现授权引用悬空窗口。
UPDATE membership m
JOIN user_identity legacy
  ON legacy.id = m.principal_id
 AND m.principal_type = 'USER'
SET m.principal_id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001', '-', '')),
    m.version = m.version + 1,
    m.updated_at = UTC_TIMESTAMP(6),
    m.updated_by = 'identity-cutover-v8'
WHERE legacy.issuer = 'http://localhost:8180/realms/agentark'
  AND legacy.subject = '019d0000-0000-7000-8000-000000000001';

-- Role Binding 使用多态主体标识，不存在跨类型外键；只迁移精确旧 Issuer/Subject。
UPDATE role_binding rb
JOIN user_identity legacy
  ON legacy.id = rb.principal_id
 AND rb.principal_type = 'USER'
SET rb.principal_id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001', '-', '')),
    rb.version = rb.version + 1,
    rb.updated_at = UTC_TIMESTAMP(6),
    rb.updated_by = 'identity-cutover-v8'
WHERE legacy.issuer = 'http://localhost:8180/realms/agentark'
  AND legacy.subject = '019d0000-0000-7000-8000-000000000001';

-- 新内置投影已由 Outbox 幂等创建后，旧 Issuer 行不再属于有效认证来源。
DELETE FROM user_identity
WHERE issuer = 'http://localhost:8180/realms/agentark'
  AND subject = '019d0000-0000-7000-8000-000000000001';
