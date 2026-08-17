-- Phase 19 建立 Control/Governance 所有的审计、计量、成本、配额和评估事实。

CREATE TABLE audit_event (
    id BINARY(16) NOT NULL COMMENT '审计事件 UUIDv7 主键，按时间有序生成且全局唯一',
    source_event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源平面提供的幂等事件标识，不包含凭据或正文',
    source_plane VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源平面：GATEWAY=公共入口，CONTROL=控制面，RUNTIME=运行面，SCHEDULER=调度面',
    organization_id BINARY(16) NULL COMMENT '所属组织 UUIDv7；平台级审计可为空',
    project_id BINARY(16) NULL COMMENT '所属项目 UUIDv7；组织或平台级审计可为空',
    principal_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体类型：USER=外部用户，SERVICE_ACCOUNT=服务账号，API_KEY=API Key，SERVICE=内部服务，SYSTEM=系统任务',
    principal_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作主体稳定引用，不保存 Token、API Key 明文或显示名称',
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审计作用域：PLATFORM=平台，ORGANIZATION=组织，PROJECT=项目，ENVIRONMENT=环境，RUN=运行，JOB=调度任务',
    scope_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '作用域稳定标识；平台级固定为 platform',
    action VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定动作代码，例如 agent.publish、deployment.promote 或 approval.decide',
    result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作结果：SUCCEEDED=成功，DENIED=授权拒绝，FAILED=业务失败',
    resource_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被操作资源类型，不包含动态标识',
    resource_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被操作资源稳定标识，不作为授权依据',
    diff_summary_json JSON NULL COMMENT '只包含字段名或顶层区段的差异摘要；禁止正文、Secret 和 Tool 参数',
    policy_version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '决策使用的 Permission 或 Quota Policy 版本；无策略时为空',
    role_version BIGINT NULL COMMENT '授权决策使用的角色乐观锁版本；不适用时为空',
    trace_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联 W3C Trace ID，为 32 位小写十六进制；无请求上下文时为空',
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联请求 ID；后台任务无 HTTP 请求时为空',
    archive_object_uri VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选防篡改归档 ObjectRef URI，不包含签名参数或授权凭据',
    archive_content_hash BINARY(32) NULL COMMENT '可选归档对象 SHA-256 摘要，与 ObjectRef 同时存在',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '操作实际发生时间，UTC，微秒精度',
    ingested_at TIMESTAMP(6) NOT NULL COMMENT 'Control 接收并持久化时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_audit_source UNIQUE (source_plane, source_event_id),
    CONSTRAINT fk_audit_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_audit_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_audit_source_plane CHECK (source_plane IN ('GATEWAY', 'CONTROL', 'RUNTIME', 'SCHEDULER')),
    CONSTRAINT ck_audit_principal_type CHECK (principal_type IN ('USER', 'SERVICE_ACCOUNT', 'API_KEY', 'SERVICE', 'SYSTEM')),
    CONSTRAINT ck_audit_scope_type CHECK (scope_type IN ('PLATFORM', 'ORGANIZATION', 'PROJECT', 'ENVIRONMENT', 'RUN', 'JOB')),
    CONSTRAINT ck_audit_result CHECK (result IN ('SUCCEEDED', 'DENIED', 'FAILED')),
    CONSTRAINT ck_audit_scope_presence CHECK (project_id IS NULL OR organization_id IS NOT NULL),
    CONSTRAINT ck_audit_role_version CHECK (role_version IS NULL OR role_version >= 0),
    CONSTRAINT ck_audit_trace CHECK (trace_id IS NULL OR trace_id REGEXP '^[0-9a-f]{32}$'),
    CONSTRAINT ck_audit_archive_pair CHECK
        ((archive_object_uri IS NULL AND archive_content_hash IS NULL)
            OR (archive_object_uri IS NOT NULL AND archive_content_hash IS NOT NULL)),
    INDEX idx_audit_scope_time (organization_id, project_id, occurred_at DESC, id DESC),
    INDEX idx_audit_trace (trace_id, occurred_at DESC, id DESC),
    INDEX idx_audit_action_result (action, result, occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长期保留且只追加的安全审计事实，与 Runtime Event 和技术日志分离';

CREATE TABLE price_table (
    id BINARY(16) NOT NULL COMMENT '价格表稳定身份 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    price_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一价格表 Key，采用规范化 ASCII',
    name VARCHAR(128) NOT NULL COMMENT '价格表显示名称',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '价格表状态：ACTIVE=可用于新计量，ARCHIVED=仅用于历史成本追溯',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_price_table_project_key UNIQUE (project_id, price_key),
    CONSTRAINT uk_price_table_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_price_table_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_price_table_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_price_table_version CHECK (version >= 0),
    INDEX idx_price_table_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目拥有的稳定价格表身份';

CREATE TABLE price_table_version (
    id BINARY(16) NOT NULL COMMENT '不可变价格表版本 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    price_table_id BINARY(16) NOT NULL COMMENT '所属价格表稳定身份 UUIDv7',
    version_number BIGINT NOT NULL COMMENT '价格表内从 1 开始递增的版本号',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 4217 三字符大写币种代码，例如 USD 或 CNY',
    effective_from TIMESTAMP(6) NOT NULL COMMENT '该版本起效时间，UTC，微秒精度',
    entries_json JSON NOT NULL COMMENT '按 usageType/provider/model 固定的单位价格项，不含凭据或业务正文',
    content_hash BINARY(32) NOT NULL COMMENT '规范化币种、起效时间和价格项的 SHA-256 摘要',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_price_version_number UNIQUE (price_table_id, version_number),
    CONSTRAINT uk_price_version_hash UNIQUE (project_id, content_hash),
    CONSTRAINT uk_price_version_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_price_version_table_scope FOREIGN KEY (price_table_id, project_id, organization_id)
        REFERENCES price_table (id, project_id, organization_id),
    CONSTRAINT ck_price_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_price_version_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    INDEX idx_price_version_effective (organization_id, project_id, effective_from DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='只追加且不可变的价格表版本';

CREATE TABLE usage_ledger (
    id BINARY(16) NOT NULL COMMENT '治理用量明细 UUIDv7 主键，按时间有序生成',
    source_plane VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用量来源平面：RUNTIME=Agent 执行，SCHEDULER=异步任务，CONTROL=控制面任务',
    source_record_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源记录幂等标识，不包含凭据或正文',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    agent_id BINARY(16) NULL COMMENT '可选 Agent UUIDv7；非 Agent 用量为空',
    revision_id BINARY(16) NULL COMMENT '可选不可变 Revision UUIDv7；非运行用量为空',
    deployment_id BINARY(16) NULL COMMENT '可选 Deployment UUIDv7；非部署运行用量为空',
    session_id BINARY(16) NULL COMMENT '可选 Runtime Session UUIDv7，仅作逻辑关联',
    turn_id BINARY(16) NULL COMMENT '可选 Runtime Turn UUIDv7，仅作逻辑关联',
    run_id BINARY(16) NULL COMMENT '可选 Runtime Run UUIDv7，仅作逻辑关联',
    usage_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计量类型：MODEL=模型，EMBEDDING=向量嵌入，TOOL=工具，SANDBOX=沙箱',
    provider VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider 稳定标识，不包含请求 ID 或凭据',
    model VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选模型稳定名称；Tool 或 Sandbox 用量可为空',
    tool VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选 Tool/MCP 稳定名称；不保存参数',
    input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入 Token 数，非模型或嵌入用量为 0',
    output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出 Token 数，非模型用量为 0',
    cached_tokens BIGINT NOT NULL DEFAULT 0 COMMENT 'Provider 明确返回的缓存 Token 数，未知或不适用为 0',
    embedding_tokens BIGINT NOT NULL DEFAULT 0 COMMENT 'Embedding Token 数，非嵌入用量为 0',
    tool_calls BIGINT NOT NULL DEFAULT 0 COMMENT 'Tool/MCP 调用次数，非工具用量为 0',
    sandbox_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Sandbox 执行时长，单位毫秒；非沙箱用量为 0',
    estimated BOOLEAN NOT NULL COMMENT '是否为估算值：0=Provider 精确返回，1=平台估算',
    price_table_version_id BINARY(16) NULL COMMENT '计算成本使用的不可变价格表版本；无成本时可为空',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '成本 ISO 4217 币种；无成本时为空',
    cost_amount DECIMAL(20,8) NOT NULL DEFAULT 0 COMMENT '按固定价格版本计算的非负成本金额，最多八位小数',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '用量实际发生时间，UTC，微秒精度',
    ingested_at TIMESTAMP(6) NOT NULL COMMENT 'Control 接收时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_usage_ledger_source UNIQUE (source_plane, source_record_id),
    CONSTRAINT fk_usage_ledger_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_usage_ledger_price_version FOREIGN KEY (price_table_version_id)
        REFERENCES price_table_version (id),
    CONSTRAINT ck_usage_ledger_source CHECK (source_plane IN ('RUNTIME', 'SCHEDULER', 'CONTROL')),
    CONSTRAINT ck_usage_ledger_type CHECK (usage_type IN ('MODEL', 'EMBEDDING', 'TOOL', 'SANDBOX')),
    CONSTRAINT ck_usage_ledger_non_negative CHECK
        (input_tokens >= 0 AND output_tokens >= 0 AND cached_tokens >= 0
            AND embedding_tokens >= 0 AND tool_calls >= 0 AND sandbox_duration_ms >= 0
            AND cost_amount >= 0),
    CONSTRAINT ck_usage_ledger_cost_version CHECK
        ((cost_amount = 0 AND (currency IS NULL OR currency REGEXP '^[A-Z]{3}$'))
            OR (cost_amount > 0 AND price_table_version_id IS NOT NULL
                AND currency REGEXP '^[A-Z]{3}$')),
    INDEX idx_usage_ledger_scope_time (organization_id, project_id, occurred_at DESC, id DESC),
    INDEX idx_usage_ledger_run (run_id, occurred_at, id),
    INDEX idx_usage_ledger_revision (revision_id, occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='从各平面幂等汇聚且不含业务正文的治理用量明细';

CREATE TABLE usage_aggregate (
    id BINARY(16) NOT NULL COMMENT '用量聚合 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    granularity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合粒度：HOUR=小时，DAY=自然日，MONTH=自然月，均按 UTC',
    period_start TIMESTAMP(6) NOT NULL COMMENT '聚合窗口开始时间，UTC，含边界',
    period_end TIMESTAMP(6) NOT NULL COMMENT '聚合窗口结束时间，UTC，不含边界',
    dimension_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合维度：PROJECT=项目，AGENT=Agent，REVISION=不可变 Revision，MODEL=模型',
    dimension_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '维度稳定标识；项目维度使用 Project UUIDv7，模型维度使用规范模型名',
    provider VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider 稳定标识，不使用请求或租户 ID 作为 Metric Label',
    model_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型稳定名称；不适用时固定为 none',
    input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内输入 Token 总数',
    output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内输出 Token 总数',
    cached_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内缓存 Token 总数',
    embedding_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内 Embedding Token 总数',
    tool_calls BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内 Tool/MCP 调用总数',
    sandbox_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内 Sandbox 执行总时长，单位毫秒',
    estimated_records BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内估算明细数量，用于区分成本精度',
    source_records BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内幂等明细记录数量',
    cost_amount DECIMAL(20,8) NOT NULL DEFAULT 0 COMMENT '窗口内同一币种成本合计，最多八位小数',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 4217 三字符大写币种；无成本默认 XXX',
    price_table_version_id BINARY(16) NULL COMMENT '成本使用的不可变价格版本；混合或无成本聚合可为空',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后一次增量聚合时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_usage_aggregate_dimension UNIQUE
        (project_id, granularity, period_start, dimension_type, dimension_ref, provider, model_key, currency),
    CONSTRAINT fk_usage_aggregate_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_usage_aggregate_price_version FOREIGN KEY (price_table_version_id)
        REFERENCES price_table_version (id),
    CONSTRAINT ck_usage_aggregate_granularity CHECK (granularity IN ('HOUR', 'DAY', 'MONTH')),
    CONSTRAINT ck_usage_aggregate_dimension CHECK (dimension_type IN ('PROJECT', 'AGENT', 'REVISION', 'MODEL')),
    CONSTRAINT ck_usage_aggregate_window CHECK (period_end > period_start),
    CONSTRAINT ck_usage_aggregate_non_negative CHECK
        (input_tokens >= 0 AND output_tokens >= 0 AND cached_tokens >= 0
            AND embedding_tokens >= 0 AND tool_calls >= 0 AND sandbox_duration_ms >= 0
            AND estimated_records >= 0 AND source_records >= 0 AND cost_amount >= 0),
    CONSTRAINT ck_usage_aggregate_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    INDEX idx_usage_aggregate_scope_period (organization_id, project_id, period_start DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按固定窗口和治理维度增量计算的 Usage 与 Cost 聚合';

CREATE TABLE quota_policy (
    id BINARY(16) NOT NULL COMMENT 'Quota Policy UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    scope_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '配额作用域：ORGANIZATION=组织，PROJECT=项目，DEPLOYMENT=部署，MODEL=模型',
    scope_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '作用域稳定标识；模型使用规范名称，其他作用域使用 UUIDv7',
    metric VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '配额指标：REQUEST_RATE=请求速率，INPUT_TOKEN=输入 Token，OUTPUT_TOKEN=输出 Token，COST=成本，CONCURRENT_RUN=并发 Run',
    enforcement VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行方式：SOFT=只告警，HARD=拒绝超限预留',
    limit_value DECIMAL(20,8) NOT NULL COMMENT '窗口内非负上限；并发和 Token 使用整数值，Cost 可含八位小数',
    window_seconds BIGINT NULL COMMENT '滑动/固定窗口秒数；CONCURRENT_RUN 为空，其他指标必须大于 0',
    budget_action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行中预算动作：WARN=告警，REQUIRE_APPROVAL=请求审批，STOP=明确停止',
    effective_from TIMESTAMP(6) NOT NULL COMMENT '策略起效时间，UTC，微秒精度',
    effective_until TIMESTAMP(6) NULL COMMENT '策略失效时间，UTC；为空表示持续有效',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '策略状态：ACTIVE=参与决策，DISABLED=停用但保留历史',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_quota_policy_effective UNIQUE
        (project_id, scope_type, scope_ref, metric, effective_from),
    CONSTRAINT uk_quota_policy_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_quota_policy_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_quota_policy_scope CHECK (scope_type IN ('ORGANIZATION', 'PROJECT', 'DEPLOYMENT', 'MODEL')),
    CONSTRAINT ck_quota_policy_metric CHECK (metric IN ('REQUEST_RATE', 'INPUT_TOKEN', 'OUTPUT_TOKEN', 'COST', 'CONCURRENT_RUN')),
    CONSTRAINT ck_quota_policy_enforcement CHECK (enforcement IN ('SOFT', 'HARD')),
    CONSTRAINT ck_quota_policy_action CHECK (budget_action IN ('WARN', 'REQUIRE_APPROVAL', 'STOP')),
    CONSTRAINT ck_quota_policy_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_quota_policy_limit CHECK (limit_value >= 0),
    CONSTRAINT ck_quota_policy_window CHECK
        ((metric = 'CONCURRENT_RUN' AND window_seconds IS NULL)
            OR (metric <> 'CONCURRENT_RUN' AND window_seconds > 0)),
    CONSTRAINT ck_quota_policy_effective_until CHECK
        (effective_until IS NULL OR effective_until > effective_from),
    CONSTRAINT ck_quota_policy_version CHECK (version >= 0),
    INDEX idx_quota_policy_match
        (organization_id, project_id, scope_type, scope_ref, metric, status, effective_from DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按组织项目部署或模型作用域定义的软硬配额策略';

CREATE TABLE quota_reservation (
    id BINARY(16) NOT NULL COMMENT '配额预留 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    policy_id BINARY(16) NOT NULL COMMENT '决策使用的 Quota Policy UUIDv7',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方幂等键，同一策略内唯一且不包含输入正文',
    subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被预留工作稳定引用，例如 Run 或待接收 Turn 标识',
    amount DECIMAL(20,8) NOT NULL COMMENT '本次非负预留量；并发 Run 固定为 1',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预留状态：HELD=占用，COMMITTED=已计入，RELEASED=已释放，EXPIRED=超时回收',
    expires_at TIMESTAMP(6) NOT NULL COMMENT 'HELD 自动回收时间，UTC，微秒精度',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后状态变化时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_quota_reservation_idempotency UNIQUE (policy_id, idempotency_key),
    CONSTRAINT fk_quota_reservation_policy_scope FOREIGN KEY (policy_id, project_id, organization_id)
        REFERENCES quota_policy (id, project_id, organization_id),
    CONSTRAINT ck_quota_reservation_amount CHECK (amount > 0),
    CONSTRAINT ck_quota_reservation_status CHECK (status IN ('HELD', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_quota_reservation_version CHECK (version >= 0),
    INDEX idx_quota_reservation_active (policy_id, status, expires_at, id),
    INDEX idx_quota_reservation_scope (organization_id, project_id, status, updated_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通过 Policy 行锁防止硬配额并发超卖的可回收预留';

CREATE TABLE evaluation_dataset (
    id BINARY(16) NOT NULL COMMENT '评估 Dataset 稳定身份 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    dataset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一 Dataset Key，采用规范化 ASCII',
    name VARCHAR(128) NOT NULL COMMENT 'Dataset 显示名称',
    description VARCHAR(512) NULL COMMENT 'Dataset 非敏感用途说明，不保存测试输入正文',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Dataset 状态：ACTIVE=可追加版本，ARCHIVED=仅用于历史评估',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_dataset_project_key UNIQUE (project_id, dataset_key),
    CONSTRAINT uk_evaluation_dataset_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluation_dataset_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_evaluation_dataset_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_evaluation_dataset_version CHECK (version >= 0),
    INDEX idx_evaluation_dataset_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目拥有的稳定 Evaluation Dataset 身份';

CREATE TABLE evaluation_dataset_version (
    id BINARY(16) NOT NULL COMMENT '不可变 Dataset Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    dataset_id BINARY(16) NOT NULL COMMENT '所属 Dataset 稳定身份 UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Dataset 内从 1 开始递增的版本号',
    schema_json JSON NOT NULL COMMENT 'Test Case 输入与期望结果的中立 JSON Schema，不含正文或凭据',
    content_hash BINARY(32) NOT NULL COMMENT 'Dataset Version Schema 与 Case 清单的 SHA-256 摘要',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_dataset_version UNIQUE (dataset_id, version_number),
    CONSTRAINT uk_evaluation_dataset_hash UNIQUE (project_id, content_hash),
    CONSTRAINT uk_evaluation_dataset_version_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluation_dataset_version_scope FOREIGN KEY (dataset_id, project_id, organization_id)
        REFERENCES evaluation_dataset (id, project_id, organization_id),
    CONSTRAINT ck_evaluation_dataset_version_number CHECK (version_number >= 1),
    INDEX idx_evaluation_dataset_version_scope (organization_id, project_id, dataset_id, version_number DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='只追加且不可变的 Evaluation Dataset Version';

CREATE TABLE evaluation_test_case (
    id BINARY(16) NOT NULL COMMENT 'Evaluation Test Case UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    dataset_version_id BINARY(16) NOT NULL COMMENT '固定 Dataset Version UUIDv7',
    case_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Dataset Version 内唯一 Case Key',
    input_object_uri VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '测试输入 ObjectRef URI，不包含签名参数或授权凭据',
    input_content_hash BINARY(32) NOT NULL COMMENT '测试输入对象 SHA-256 摘要',
    expected_json JSON NOT NULL COMMENT '允许公开给 Evaluator 的规范期望投影，不保存 Secret 或隐藏推理链',
    expected_content_hash BINARY(32) NOT NULL COMMENT '规范期望投影 SHA-256 摘要',
    weight DECIMAL(10,6) NOT NULL DEFAULT 1 COMMENT 'Case 非负权重，最多六位小数',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_test_case_key UNIQUE (dataset_version_id, case_key),
    CONSTRAINT uk_evaluation_test_case_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluation_test_case_dataset_scope FOREIGN KEY (dataset_version_id, project_id, organization_id)
        REFERENCES evaluation_dataset_version (id, project_id, organization_id),
    CONSTRAINT ck_evaluation_test_case_weight CHECK (weight > 0),
    INDEX idx_evaluation_test_case_dataset (organization_id, project_id, dataset_version_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定 Dataset Version 下不可变且可追溯的评估 Test Case';

CREATE TABLE evaluator (
    id BINARY(16) NOT NULL COMMENT 'Evaluator 稳定身份 UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    evaluator_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一 Evaluator Key，采用规范化 ASCII',
    name VARCHAR(128) NOT NULL COMMENT 'Evaluator 显示名称',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Evaluator 状态：ACTIVE=可追加版本，ARCHIVED=仅用于历史评估',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluator_project_key UNIQUE (project_id, evaluator_key),
    CONSTRAINT uk_evaluator_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluator_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_evaluator_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_evaluator_version CHECK (version >= 0),
    INDEX idx_evaluator_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目拥有的稳定 Evaluator 身份';

CREATE TABLE evaluator_version (
    id BINARY(16) NOT NULL COMMENT '不可变 Evaluator Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    evaluator_id BINARY(16) NOT NULL COMMENT '所属 Evaluator 稳定身份 UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Evaluator 内从 1 开始递增的版本号',
    evaluator_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Evaluator 类型：DETERMINISTIC=固定规则，MODEL=模型评分，HUMAN=人工评分',
    config_json JSON NOT NULL COMMENT 'Evaluator 中立配置；模型型只允许 SecretRef，不保存凭据值',
    content_hash BINARY(32) NOT NULL COMMENT 'Evaluator 类型与规范配置的 SHA-256 摘要',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluator_version_number UNIQUE (evaluator_id, version_number),
    CONSTRAINT uk_evaluator_version_hash UNIQUE (project_id, content_hash),
    CONSTRAINT uk_evaluator_version_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluator_version_scope FOREIGN KEY (evaluator_id, project_id, organization_id)
        REFERENCES evaluator (id, project_id, organization_id),
    CONSTRAINT ck_evaluator_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_evaluator_type CHECK (evaluator_type IN ('DETERMINISTIC', 'MODEL', 'HUMAN')),
    INDEX idx_evaluator_version_scope (organization_id, project_id, evaluator_id, version_number DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='只追加且不可变的 Evaluator Version';

CREATE TABLE evaluation_run (
    id BINARY(16) NOT NULL COMMENT 'Evaluation Run UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    candidate_revision_id BINARY(16) NOT NULL COMMENT '被评估不可变 Agent Revision UUIDv7',
    candidate_snapshot_id BINARY(16) NOT NULL COMMENT 'Revision 固定 Snapshot UUIDv7',
    dataset_version_id BINARY(16) NOT NULL COMMENT '固定 Dataset Version UUIDv7',
    evaluator_version_id BINARY(16) NOT NULL COMMENT '固定 Evaluator Version UUIDv7',
    provider VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评估执行 Provider 稳定标识；确定性评估固定为 agentark',
    model VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型型 Evaluator 的模型稳定名称；确定性或人工评估为空',
    threshold DECIMAL(10,6) NOT NULL COMMENT '本次 Run 固定通过阈值，范围 0 到 1',
    baseline_run_id BINARY(16) NULL COMMENT '可选回归比较基准 Evaluation Run UUIDv7',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Run 状态：QUEUED=待执行，RUNNING=执行中，PASSED=通过，FAILED=未通过，ERROR=执行错误，CANCELLED=取消',
    total_score DECIMAL(10,6) NULL COMMENT '终态加权总分，范围 0 到 1；非终态为空',
    regression_delta DECIMAL(10,6) NULL COMMENT '相对 Baseline 的分数差；无 Baseline 时为空',
    failure_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ERROR/CANCELLED 稳定失败代码；不得包含 Provider 原始响应',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    started_at TIMESTAMP(6) NULL COMMENT '开始时间，UTC；QUEUED 时为空',
    completed_at TIMESTAMP(6) NULL COMMENT '终态完成时间，UTC；非终态为空',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_run_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_evaluation_run_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_evaluation_run_revision FOREIGN KEY (candidate_revision_id)
        REFERENCES agent_revision (id),
    CONSTRAINT fk_evaluation_run_snapshot FOREIGN KEY (candidate_snapshot_id)
        REFERENCES agent_revision_snapshot (id),
    CONSTRAINT fk_evaluation_run_dataset FOREIGN KEY (dataset_version_id)
        REFERENCES evaluation_dataset_version (id),
    CONSTRAINT fk_evaluation_run_evaluator FOREIGN KEY (evaluator_version_id)
        REFERENCES evaluator_version (id),
    CONSTRAINT fk_evaluation_run_baseline FOREIGN KEY (baseline_run_id)
        REFERENCES evaluation_run (id),
    CONSTRAINT ck_evaluation_run_threshold CHECK (threshold >= 0 AND threshold <= 1),
    CONSTRAINT ck_evaluation_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'CANCELLED')),
    CONSTRAINT ck_evaluation_run_score CHECK (total_score IS NULL OR (total_score >= 0 AND total_score <= 1)),
    CONSTRAINT ck_evaluation_run_terminal CHECK
        ((status IN ('QUEUED', 'RUNNING') AND completed_at IS NULL)
            OR (status IN ('PASSED', 'FAILED', 'ERROR', 'CANCELLED') AND completed_at IS NOT NULL)),
    INDEX idx_evaluation_run_revision (organization_id, project_id, candidate_revision_id, created_at DESC, id DESC),
    INDEX idx_evaluation_run_status (organization_id, project_id, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定 Revision、Snapshot、Dataset 和 Evaluator Version 的评估执行';

CREATE TABLE evaluation_score (
    id BINARY(16) NOT NULL COMMENT 'Evaluation Score UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    evaluation_run_id BINARY(16) NOT NULL COMMENT '所属 Evaluation Run UUIDv7',
    test_case_id BINARY(16) NOT NULL COMMENT '被评分 Test Case UUIDv7',
    metric_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定评分指标 Key，例如 exact_match',
    score DECIMAL(10,6) NOT NULL COMMENT '单项分数，范围 0 到 1',
    passed BOOLEAN NOT NULL COMMENT '是否达到本 Evaluator 单项要求：0=未通过，1=通过',
    details_json JSON NULL COMMENT '不含输入正文、Prompt、Secret 或隐藏推理链的安全评分摘要',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_evaluation_score_metric UNIQUE (evaluation_run_id, test_case_id, metric_key),
    CONSTRAINT fk_evaluation_score_run_scope FOREIGN KEY (evaluation_run_id, project_id, organization_id)
        REFERENCES evaluation_run (id, project_id, organization_id),
    CONSTRAINT fk_evaluation_score_case_scope FOREIGN KEY (test_case_id, project_id, organization_id)
        REFERENCES evaluation_test_case (id, project_id, organization_id),
    CONSTRAINT ck_evaluation_score_range CHECK (score >= 0 AND score <= 1),
    INDEX idx_evaluation_score_run (organization_id, project_id, evaluation_run_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='只追加的 Evaluation Test Case 分数事实';

CREATE TABLE release_gate (
    id BINARY(16) NOT NULL COMMENT 'Release Gate UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    agent_id BINARY(16) NOT NULL COMMENT '受 Gate 约束的 Agent UUIDv7',
    environment_id BINARY(16) NULL COMMENT '可选受约束 Environment UUIDv7；为空表示项目内所有环境',
    environment_scope_id BINARY(16) GENERATED ALWAYS AS
        (COALESCE(environment_id, X'00000000000000000000000000000000')) STORED COMMENT '统一全环境和特定环境唯一约束的生成列；零 UUID 表示全部环境',
    dataset_version_id BINARY(16) NOT NULL COMMENT 'Gate 固定 Dataset Version UUIDv7',
    evaluator_version_id BINARY(16) NOT NULL COMMENT 'Gate 固定 Evaluator Version UUIDv7',
    threshold DECIMAL(10,6) NOT NULL COMMENT '允许 Promote 的最低通过分数，范围 0 到 1',
    enforcement VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Gate 执行方式：SOFT=只告警，HARD=阻止未通过 Revision Promote',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Gate 状态：ACTIVE=参与决策，DISABLED=停用但保留历史',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_release_gate_scope UNIQUE (project_id, agent_id, environment_scope_id),
    CONSTRAINT fk_release_gate_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT fk_release_gate_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_release_gate_environment FOREIGN KEY (environment_id) REFERENCES environment (id),
    CONSTRAINT fk_release_gate_dataset FOREIGN KEY (dataset_version_id)
        REFERENCES evaluation_dataset_version (id),
    CONSTRAINT fk_release_gate_evaluator FOREIGN KEY (evaluator_version_id)
        REFERENCES evaluator_version (id),
    CONSTRAINT ck_release_gate_threshold CHECK (threshold >= 0 AND threshold <= 1),
    CONSTRAINT ck_release_gate_enforcement CHECK (enforcement IN ('SOFT', 'HARD')),
    CONSTRAINT ck_release_gate_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_release_gate_version CHECK (version >= 0),
    INDEX idx_release_gate_lookup (organization_id, project_id, agent_id, environment_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='以固定 Dataset/Evaluator Version 约束 Revision Promote 的发布评估 Gate';

CREATE TRIGGER trg_audit_event_no_update
BEFORE UPDATE ON audit_event FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit event cannot be updated';

CREATE TRIGGER trg_audit_event_no_delete
BEFORE DELETE ON audit_event FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit event cannot be deleted';

CREATE TRIGGER trg_price_table_version_no_update
BEFORE UPDATE ON price_table_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'price table version cannot be updated';

CREATE TRIGGER trg_price_table_version_no_delete
BEFORE DELETE ON price_table_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'price table version cannot be deleted';

CREATE TRIGGER trg_evaluation_dataset_version_no_update
BEFORE UPDATE ON evaluation_dataset_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluation dataset version cannot be updated';

CREATE TRIGGER trg_evaluation_dataset_version_no_delete
BEFORE DELETE ON evaluation_dataset_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluation dataset version cannot be deleted';

CREATE TRIGGER trg_evaluator_version_no_update
BEFORE UPDATE ON evaluator_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluator version cannot be updated';

CREATE TRIGGER trg_evaluator_version_no_delete
BEFORE DELETE ON evaluator_version FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluator version cannot be deleted';

CREATE TRIGGER trg_evaluation_score_no_update
BEFORE UPDATE ON evaluation_score FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluation score cannot be updated';

CREATE TRIGGER trg_evaluation_score_no_delete
BEFORE DELETE ON evaluation_score FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evaluation score cannot be deleted';

INSERT INTO permission (id, permission_key, description, risk_level, created_at) VALUES
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001b', '-', '')), 'audit:read', '严格查询项目安全审计事实', 'HIGH', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001c', '-', '')), 'usage:read', '读取项目 Usage 与 Cost 明细和聚合', 'MEDIUM', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001d', '-', '')), 'quota:read', '读取项目 Quota Policy 与 Reservation 摘要', 'MEDIUM', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001e', '-', '')), 'quota:manage', '管理项目软硬配额和预算动作', 'HIGH', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000001f', '-', '')), 'evaluation:read', '读取 Dataset、Evaluator、Evaluation Run 与 Release Gate', 'LOW', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000020', '-', '')), 'evaluation:manage', '管理版本化 Evaluation 与 Release Gate', 'HIGH', '2026-08-17 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000021', '-', '')), 'price:manage', '管理版本化 Price Table 和计费规则', 'HIGH', '2026-08-17 00:00:00.000000');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-17 00:00:00.000000', 'flyway:phase-19'
FROM role
JOIN permission ON permission.permission_key IN
    ('audit:read', 'usage:read', 'quota:read', 'quota:manage',
     'evaluation:read', 'evaluation:manage', 'price:manage')
WHERE role.built_in = TRUE AND role.role_key IN ('organization_owner', 'project_admin');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-17 00:00:00.000000', 'flyway:phase-19'
FROM role
JOIN permission ON permission.permission_key IN ('usage:read', 'quota:read', 'evaluation:read', 'evaluation:manage')
WHERE role.built_in = TRUE AND role.role_key = 'project_developer';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-17 00:00:00.000000', 'flyway:phase-19'
FROM role
JOIN permission ON permission.permission_key IN ('usage:read', 'quota:read', 'evaluation:read')
WHERE role.built_in = TRUE AND role.role_key = 'project_viewer';
