-- Phase 10 建立可编辑 Draft、不可变 Revision/Snapshot、Deployment 指针与事务 Outbox。

CREATE TABLE agent_draft (
    agent_id BINARY(16) NOT NULL COMMENT 'Agent UUIDv7，同时作为一对一 Draft 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    spec_json JSON NOT NULL COMMENT '可编辑 Draft 强类型资产引用、Runtime Provider、能力和运行限制 JSON；不得保存 Secret 明文',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Draft 乐观锁版本号，从 0 开始且每次更新递增 1',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定标识，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定标识，不保存凭据',
    PRIMARY KEY (agent_id),
    CONSTRAINT uk_agent_draft_scope UNIQUE (agent_id, project_id, organization_id),
    CONSTRAINT fk_agent_draft_agent_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent (id, project_id, organization_id),
    CONSTRAINT ck_agent_draft_version CHECK (version >= 0),
    INDEX idx_agent_draft_scope_updated (organization_id, project_id, updated_at, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 当前唯一可编辑 Draft；Runtime 禁止读取';

CREATE TABLE agent_draft_component (
    agent_id BINARY(16) NOT NULL COMMENT '所属 Agent Draft UUIDv7',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    component_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '组件类型：MODEL=模型，PROMPT=提示词，MCP=MCP服务，SKILL=技能，KNOWLEDGE=知识，MEMORY=记忆，WORKSPACE=工作区，SANDBOX=沙箱，PERMISSION=权限策略',
    component_order INT NOT NULL COMMENT '同类型组件从 0 开始的稳定顺序',
    owner_id BINARY(16) NOT NULL COMMENT '资产稳定身份 UUIDv7；Knowledge 保存 Knowledge Base UUIDv7',
    version_id BINARY(16) NOT NULL COMMENT '不可变资产版本 UUIDv7；Knowledge 保存 READY Revision UUIDv7',
    binding_json JSON NOT NULL COMMENT '角色、Tool 白名单等非敏感绑定 JSON；不得保存资产正文或 Secret 明文',
    created_at TIMESTAMP(6) NOT NULL COMMENT '组件快照创建时间，UTC，微秒精度',
    PRIMARY KEY (agent_id, component_type, component_order),
    CONSTRAINT fk_agent_draft_component_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent_draft (agent_id, project_id, organization_id) ON DELETE CASCADE,
    CONSTRAINT ck_agent_draft_component_type CHECK (component_type IN ('MODEL', 'PROMPT', 'MCP', 'SKILL', 'KNOWLEDGE', 'MEMORY', 'WORKSPACE', 'SANDBOX', 'PERMISSION')),
    CONSTRAINT ck_agent_draft_component_order CHECK (component_order >= 0),
    INDEX idx_agent_draft_component_version (project_id, component_type, version_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Draft 资产引用的可查询投影；权威内容仍为 agent_draft.spec_json';

CREATE TABLE validation_report (
    id BINARY(16) NOT NULL COMMENT 'Validation Report UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    agent_id BINARY(16) NOT NULL COMMENT '被校验 Agent UUIDv7',
    draft_version BIGINT NOT NULL COMMENT '被校验 Draft 乐观锁版本号',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '校验结果：VALID=允许发布，INVALID=阻止发布',
    findings_json JSON NOT NULL COMMENT '仅包含路径、稳定代码、严重程度和说明的问题列表，不保存资产正文',
    created_at TIMESTAMP(6) NOT NULL COMMENT '校验时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起校验的主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT fk_validation_report_agent_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent (id, project_id, organization_id),
    CONSTRAINT ck_validation_report_draft_version CHECK (draft_version >= 0),
    CONSTRAINT ck_validation_report_status CHECK (status IN ('VALID', 'INVALID')),
    INDEX idx_validation_report_agent (organization_id, project_id, agent_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Draft 可追溯校验报告';

CREATE TABLE agent_revision (
    id BINARY(16) NOT NULL COMMENT 'Agent Revision UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    agent_id BINARY(16) NOT NULL COMMENT '所属 Agent UUIDv7',
    snapshot_id BINARY(16) NOT NULL COMMENT '一对一不可变 Snapshot UUIDv7',
    revision_number BIGINT NOT NULL COMMENT 'Agent 内从 1 开始单调递增的 Revision 序号',
    schema_version INT NOT NULL COMMENT 'Snapshot Schema 正整数版本；Phase 10 固定为 1',
    runtime_provider VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布时固定的小写 Runtime Provider 稳定标识',
    content_hash BINARY(32) NOT NULL COMMENT '排除顶层 contentHash 字段后的 Canonical Snapshot SHA-256 摘要',
    required_capabilities_json JSON NOT NULL COMMENT 'Runtime Provider 必须满足的稳定能力名列表',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Revision 状态：PUBLISHED=已发布且永久不可编辑',
    created_at TIMESTAMP(6) NOT NULL COMMENT '发布时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_revision_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_agent_revision_snapshot UNIQUE (snapshot_id),
    CONSTRAINT uk_agent_revision_number UNIQUE (agent_id, revision_number),
    CONSTRAINT uk_agent_revision_hash UNIQUE (agent_id, content_hash),
    CONSTRAINT fk_agent_revision_agent_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent (id, project_id, organization_id),
    CONSTRAINT ck_agent_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_agent_revision_schema CHECK (schema_version > 0),
    CONSTRAINT ck_agent_revision_status CHECK (status = 'PUBLISHED'),
    INDEX idx_agent_revision_scope (organization_id, project_id, agent_id, revision_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已发布且数据库级不可更新、不可删除的 Agent Revision';

CREATE TABLE agent_revision_snapshot (
    id BINARY(16) NOT NULL COMMENT 'Snapshot UUIDv7 主键，与 agent_revision.snapshot_id 一对一',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    revision_id BINARY(16) NOT NULL COMMENT '所属 Agent Revision UUIDv7',
    schema_version INT NOT NULL COMMENT 'Snapshot JSON Schema 正整数版本；Phase 10 固定为 1',
    runtime_provider VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布时固定的小写 Runtime Provider 稳定标识',
    content_hash BINARY(32) NOT NULL COMMENT 'Canonical Snapshot SHA-256 摘要，与 JSON 顶层 contentHash 一致',
    snapshot_json JSON NOT NULL COMMENT '完整 Canonical Snapshot v1；仅含 SecretRef，禁止 Secret 明文',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_revision_snapshot_revision UNIQUE (revision_id),
    CONSTRAINT uk_agent_revision_snapshot_hash UNIQUE (content_hash),
    CONSTRAINT fk_agent_revision_snapshot_scope FOREIGN KEY (revision_id, project_id, organization_id)
        REFERENCES agent_revision (id, project_id, organization_id),
    CONSTRAINT ck_agent_revision_snapshot_schema CHECK (schema_version > 0),
    INDEX idx_agent_revision_snapshot_scope (organization_id, project_id, revision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Runtime 通过 Internal API 获取的完整不可变 Snapshot';

CREATE TABLE publish_operation (
    id BINARY(16) NOT NULL COMMENT 'Publish Operation UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    agent_id BINARY(16) NOT NULL COMMENT '目标 Agent UUIDv7',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方幂等键；同项目同 Agent 永久唯一',
    draft_version BIGINT NOT NULL COMMENT '幂等键绑定的 Draft 乐观锁版本号',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布终态：SUCCEEDED=成功且有 Revision，FAILED=失败且无 Revision',
    revision_id BINARY(16) NULL COMMENT '成功发布生成的 Revision UUIDv7；FAILED 时为空',
    created_at TIMESTAMP(6) NOT NULL COMMENT '操作创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_publish_operation_idempotency UNIQUE (project_id, agent_id, idempotency_key),
    CONSTRAINT fk_publish_operation_agent_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent (id, project_id, organization_id),
    CONSTRAINT fk_publish_operation_revision_scope FOREIGN KEY (revision_id, project_id, organization_id)
        REFERENCES agent_revision (id, project_id, organization_id),
    CONSTRAINT ck_publish_operation_draft_version CHECK (draft_version >= 0),
    CONSTRAINT ck_publish_operation_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_publish_operation_revision CHECK ((status = 'SUCCEEDED' AND revision_id IS NOT NULL) OR (status = 'FAILED' AND revision_id IS NULL)),
    INDEX idx_publish_operation_scope (organization_id, project_id, agent_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent Publish 幂等操作终态';

CREATE TABLE deployment (
    id BINARY(16) NOT NULL COMMENT 'Deployment UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    environment_id BINARY(16) NOT NULL COMMENT '所属 Environment UUIDv7',
    agent_id BINARY(16) NOT NULL COMMENT '部署的 Agent UUIDv7',
    desired_revision_id BINARY(16) NOT NULL COMMENT 'Runtime 新 Session 应使用的不可变 Revision 指针',
    desired_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '期望状态：ENABLED=允许新 Session，DISABLED=拒绝新 Session',
    traffic_policy_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '流量策略：FULL=全量切换，CANARY=按百分比分流且 Phase 10 不执行',
    canary_percent INT NOT NULL DEFAULT 0 COMMENT 'CANARY 流量百分比 1 到 99；FULL 时固定为 0',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Deployment 乐观锁版本号，从 0 开始递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定标识',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后指针或状态变更时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后变更主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_deployment_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_deployment_environment_agent UNIQUE (environment_id, agent_id),
    CONSTRAINT fk_deployment_environment_scope FOREIGN KEY (environment_id, project_id, organization_id)
        REFERENCES environment (id, project_id, organization_id),
    CONSTRAINT fk_deployment_agent_scope FOREIGN KEY (agent_id, project_id, organization_id)
        REFERENCES agent (id, project_id, organization_id),
    CONSTRAINT fk_deployment_revision_scope FOREIGN KEY (desired_revision_id, project_id, organization_id)
        REFERENCES agent_revision (id, project_id, organization_id),
    CONSTRAINT ck_deployment_status CHECK (desired_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_deployment_traffic_type CHECK (traffic_policy_type IN ('FULL', 'CANARY')),
    CONSTRAINT ck_deployment_traffic_value CHECK ((traffic_policy_type = 'FULL' AND canary_percent = 0) OR (traffic_policy_type = 'CANARY' AND canary_percent BETWEEN 1 AND 99)),
    CONSTRAINT ck_deployment_version CHECK (version >= 0),
    INDEX idx_deployment_scope_status (organization_id, project_id, environment_id, desired_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Environment 内稳定 Agent Deployment 与期望 Revision 指针';

CREATE TABLE deployment_revision (
    id BINARY(16) NOT NULL COMMENT 'Deployment 历史事件 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    deployment_id BINARY(16) NOT NULL COMMENT '所属 Deployment UUIDv7',
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更动作：CREATE=创建，PROMOTE=前进，ROLLBACK=回退，ENABLE=启用，DISABLE=禁用',
    from_revision_id BINARY(16) NULL COMMENT '变更前 Revision UUIDv7；CREATE 时为空',
    to_revision_id BINARY(16) NOT NULL COMMENT '变更后 Revision UUIDv7；状态变更时与变更前相同',
    created_at TIMESTAMP(6) NOT NULL COMMENT '变更时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行变更的主体稳定标识',
    PRIMARY KEY (id),
    CONSTRAINT fk_deployment_revision_deployment_scope FOREIGN KEY (deployment_id, project_id, organization_id)
        REFERENCES deployment (id, project_id, organization_id),
    CONSTRAINT fk_deployment_revision_from_scope FOREIGN KEY (from_revision_id, project_id, organization_id)
        REFERENCES agent_revision (id, project_id, organization_id),
    CONSTRAINT fk_deployment_revision_to_scope FOREIGN KEY (to_revision_id, project_id, organization_id)
        REFERENCES agent_revision (id, project_id, organization_id),
    CONSTRAINT ck_deployment_revision_action CHECK (action IN ('CREATE', 'PROMOTE', 'ROLLBACK', 'ENABLE', 'DISABLE')),
    INDEX idx_deployment_revision_history (organization_id, project_id, deployment_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Deployment Revision 指针和状态的只追加历史';

CREATE TABLE control_outbox (
    id BINARY(16) NOT NULL COMMENT 'Outbox Event UUIDv7 主键',
    aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型：agent_revision=Agent Revision，deployment=Deployment',
    aggregate_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合 UUIDv7 规范字符串',
    event_type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定事件类型，例如 agent.revision.published 或 deployment.promote',
    payload_json JSON NOT NULL COMMENT '不含 Secret 明文的语言中立事件载荷 JSON',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '投递状态：PENDING=待投递，PUBLISHED=已投递，FAILED=达到当前重试上限',
    attempts INT NOT NULL DEFAULT 0 COMMENT '投递尝试次数，从 0 开始递增',
    available_at TIMESTAMP(6) NOT NULL COMMENT '下次允许投递时间，UTC，微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT '与聚合本地事务同一时刻创建，UTC，微秒精度',
    published_at TIMESTAMP(6) NULL COMMENT '成功投递时间，UTC，未投递时为空',
    PRIMARY KEY (id),
    CONSTRAINT ck_control_outbox_aggregate_type CHECK (aggregate_type IN ('agent_revision', 'deployment')),
    CONSTRAINT ck_control_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_control_outbox_attempts CHECK (attempts >= 0),
    INDEX idx_control_outbox_delivery (status, available_at, id),
    INDEX idx_control_outbox_aggregate (aggregate_type, aggregate_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='与 Control 聚合本地事务原子写入的事务 Outbox';

-- Published Revision 和 Snapshot 在数据库层永久不可修改或删除；Rollback 只移动 Deployment 指针。
CREATE TRIGGER trg_agent_revision_no_update
BEFORE UPDATE ON agent_revision FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'published agent revision is immutable';

CREATE TRIGGER trg_agent_revision_no_delete
BEFORE DELETE ON agent_revision FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'published agent revision cannot be deleted';

CREATE TRIGGER trg_agent_revision_snapshot_no_update
BEFORE UPDATE ON agent_revision_snapshot FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'agent revision snapshot is immutable';

CREATE TRIGGER trg_agent_revision_snapshot_no_delete
BEFORE DELETE ON agent_revision_snapshot FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'agent revision snapshot cannot be deleted';

-- Phase 10 权限使用固定 UUIDv7，并避开 Phase 07 到 Phase 09 已占用的 01 到 15。
INSERT INTO permission (id, permission_key, description, risk_level, created_at) VALUES
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000016', '-', '')), 'agent:read', '读取 Agent、Draft、Revision 和校验报告', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000017', '-', '')), 'agent:manage', '创建 Agent 并管理 Draft', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000018', '-', '')), 'agent:publish', '发布不可变 Agent Revision 和 Snapshot', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000019', '-', '')), 'deployment:read', '读取 Environment Deployment 与历史', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001a', '-', '')), 'deployment:manage', '创建、Promote、Rollback、Enable 和 Disable Deployment', 'HIGH', '2026-08-16 00:00:00.000000');

-- 组织所有者、项目管理员和开发者可以完整管理发布与部署。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-10'
FROM role
JOIN permission ON permission.permission_key IN ('agent:read', 'agent:manage', 'agent:publish', 'deployment:read', 'deployment:manage')
WHERE role.built_in = TRUE AND role.role_key IN ('organization_owner', 'project_admin', 'project_developer');

-- 项目只读角色只能读取 Agent Revision 与 Deployment 描述。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-10'
FROM role
JOIN permission ON permission.permission_key IN ('agent:read', 'deployment:read')
WHERE role.built_in = TRUE AND role.role_key = 'project_viewer';
