CREATE TABLE session (
    id BINARY(16) NOT NULL COMMENT 'Session 的 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于显式租户隔离',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，用于显式租户隔离',
    deployment_id BINARY(16) NOT NULL COMMENT '创建 Session 时从 Control 内部契约解析并固定的 Deployment UUIDv7，仅作逻辑引用',
    revision_id BINARY(16) NOT NULL COMMENT '创建 Session 时固定的不可变 Revision UUIDv7，仅作逻辑引用',
    snapshot_id BINARY(16) NOT NULL COMMENT '创建 Session 时固定的不可变 Snapshot UUIDv7，仅作逻辑引用',
    snapshot_hash BINARY(32) NOT NULL COMMENT '固定 Canonical Snapshot 的 SHA-256 原始 32 字节摘要',
    participant_metadata JSON NOT NULL COMMENT '不含秘密的参与者元数据 JSON，不作为授权依据',
    channel_metadata JSON NOT NULL COMMENT '不含秘密的渠道元数据 JSON，不作为授权依据',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Session 状态：ACTIVE=可接收 Turn，CLOSED=正常关闭，FAILED=不可恢复失败',
    event_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已分配的最大 Session Event 序号，从 0 开始并在行锁内递增',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Session 创建时刻，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Session 最近更新时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT ck_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'FAILED')),
    CONSTRAINT ck_session_participant_json CHECK (JSON_VALID(participant_metadata)),
    CONSTRAINT ck_session_channel_json CHECK (JSON_VALID(channel_metadata)),
    INDEX idx_session_scope_status (organization_id, project_id, status, updated_at, id),
    INDEX idx_session_deployment (organization_id, project_id, deployment_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Runtime 权威 Session；创建后固定 Deployment、Revision、Snapshot 与 Hash';

CREATE TABLE turn (
    id BINARY(16) NOT NULL COMMENT 'Turn 的 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，冗余保存用于租户查询',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，冗余保存用于租户查询',
    session_id BINARY(16) NOT NULL COMMENT '所属 Session UUIDv7',
    sequence BIGINT UNSIGNED NOT NULL COMMENT 'Session 内从 1 开始的单调 Turn 序号',
    input_storage VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '输入保存方式：INLINE=数据库内联 JSON，OBJECT=Object Storage 引用',
    input_json JSON NULL COMMENT 'INLINE 时保存不含秘密和隐藏推理过程的输入 JSON；OBJECT 时为空',
    input_object_uri VARCHAR(1024) NULL COMMENT 'OBJECT 时保存受控对象 URI；INLINE 时为空，URI 不是授权凭据',
    input_object_size BIGINT UNSIGNED NULL COMMENT 'OBJECT 时保存对象字节数；INLINE 时为空',
    input_media_type VARCHAR(255) NULL COMMENT 'OBJECT 时保存具体媒体类型；INLINE 时为空',
    input_hash BINARY(32) NOT NULL COMMENT '规范输入载荷的 SHA-256 原始 32 字节摘要',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Turn 状态：ACCEPTED=已接收，QUEUED=已入队，RUNNING=执行中，WAITING_APPROVAL=等待审批，COMPLETED=成功，FAILED=失败，CANCELLED=取消，TIMED_OUT=超时',
    current_run_id BINARY(16) NULL COMMENT '当前 Run Attempt UUIDv7；接收后必须存在，重试时只移动该指针',
    fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前有效栅栏令牌；0=尚未 Claim，大于 0=最近一次 Claim 令牌',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Turn 创建时刻，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Turn 最近状态变化时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_turn_session FOREIGN KEY (session_id) REFERENCES session (id),
    CONSTRAINT uk_turn_session_sequence UNIQUE (session_id, sequence),
    CONSTRAINT ck_turn_input_storage CHECK (input_storage IN ('INLINE', 'OBJECT')),
    CONSTRAINT ck_turn_input_shape CHECK (
        (input_storage = 'INLINE' AND input_json IS NOT NULL AND input_object_uri IS NULL
            AND input_object_size IS NULL AND input_media_type IS NULL)
        OR
        (input_storage = 'OBJECT' AND input_json IS NULL AND input_object_uri IS NOT NULL
            AND input_object_size IS NOT NULL AND input_media_type IS NOT NULL)
    ),
    CONSTRAINT ck_turn_status CHECK (status IN (
        'ACCEPTED', 'QUEUED', 'RUNNING', 'WAITING_APPROVAL',
        'COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    INDEX idx_turn_scope_status (organization_id, project_id, status, updated_at, id),
    INDEX idx_turn_session_current (session_id, current_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Session 内用户输入与生命周期；重试只追加 Run 并移动 current_run_id';

CREATE TABLE run (
    id BINARY(16) NOT NULL COMMENT 'Run Attempt 的 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，冗余保存用于租户查询',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，冗余保存用于租户查询',
    session_id BINARY(16) NOT NULL COMMENT '所属 Session UUIDv7',
    turn_id BINARY(16) NOT NULL COMMENT '所属 Turn UUIDv7',
    attempt_number INT UNSIGNED NOT NULL COMMENT 'Turn 内从 1 开始递增的 Run Attempt 序号',
    runtime_provider VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Snapshot 指定的 Runtime Provider 稳定标识',
    compiler_version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider Adapter 编译器稳定版本',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Run 状态：CREATED=已创建，CLAIMED=已领取，RUNNING=执行中，PAUSED=暂停，SUCCEEDED=成功，FAILED=失败，CANCELLED=取消，ABANDONED=Owner 失联放弃',
    event_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已分配的最大 Run Event 序号，从 0 开始并在行锁内递增',
    fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前有效栅栏令牌；0=尚未 Claim，大于 0=最近一次 Claim 令牌',
    started_at TIMESTAMP(6) NULL COMMENT '首次进入 RUNNING 的时刻，未开始时为空，UTC 微秒精度',
    ended_at TIMESTAMP(6) NULL COMMENT '进入 SUCCEEDED、FAILED、CANCELLED 或 ABANDONED 终态的时刻，非终态为空',
    error_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败、取消或放弃时的稳定错误码；不得包含秘密或用户载荷',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Run 创建时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_run_session FOREIGN KEY (session_id) REFERENCES session (id),
    CONSTRAINT fk_run_turn FOREIGN KEY (turn_id) REFERENCES turn (id),
    CONSTRAINT uk_run_turn_attempt UNIQUE (turn_id, attempt_number),
    CONSTRAINT ck_run_attempt CHECK (attempt_number >= 1),
    CONSTRAINT ck_run_status CHECK (status IN (
        'CREATED', 'CLAIMED', 'RUNNING', 'PAUSED',
        'SUCCEEDED', 'FAILED', 'CANCELLED', 'ABANDONED')),
    CONSTRAINT ck_run_terminal_time CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'ABANDONED') AND ended_at IS NOT NULL)
        OR
        (status IN ('CREATED', 'CLAIMED', 'RUNNING', 'PAUSED') AND ended_at IS NULL)
    ),
    INDEX idx_run_scope_status (organization_id, project_id, status, created_at, id),
    INDEX idx_run_session_status (session_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='每个 Turn 的不可覆盖执行尝试；重试必须追加新 Run';

CREATE TABLE runtime_work_item (
    id BINARY(16) NOT NULL COMMENT '持久 Work Item 的 UUIDv7 主键',
    run_id BINARY(16) NOT NULL COMMENT '唯一关联的 Run UUIDv7',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '队列状态：READY=可领取，CLAIMED=已领取，COMPLETED=成功完成，FAILED=失败完成，CANCELLED=取消',
    priority INT NOT NULL DEFAULT 0 COMMENT '调度优先级，数值越大越先 Claim',
    available_at TIMESTAMP(6) NOT NULL COMMENT '最早可 Claim 时刻，UTC 微秒精度',
    claimed_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CLAIMED 时的 Runtime Instance Key；其他状态为空',
    claim_until TIMESTAMP(6) NULL COMMENT 'CLAIMED Lease 到期时刻；其他状态为空，UTC 微秒精度',
    fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次成功 Claim 原子递增的栅栏令牌；0=从未 Claim',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计 Claim 次数，从 0 开始',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Work Item 创建时刻，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最近 Claim、续约或完成时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_runtime_work_item_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT uk_runtime_work_item_run UNIQUE (run_id),
    CONSTRAINT ck_runtime_work_item_status CHECK (status IN (
        'READY', 'CLAIMED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_runtime_work_item_claim CHECK (
        (status = 'CLAIMED' AND claimed_by IS NOT NULL AND claim_until IS NOT NULL)
        OR
        (status <> 'CLAIMED' AND claimed_by IS NULL AND claim_until IS NULL)
    ),
    INDEX idx_runtime_work_item_claim (status, available_at, priority DESC, id),
    INDEX idx_runtime_work_item_expiry (status, claim_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='MySQL 权威持久工作队列；支持过期 Lease 重领和递增 Fencing Token';

CREATE TABLE runtime_instance (
    id BINARY(16) NOT NULL COMMENT 'Runtime Instance 的 UUIDv7 主键',
    instance_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部署范围内稳定且区分大小写的实例 Key',
    started_at TIMESTAMP(6) NOT NULL COMMENT '实例启动时刻，UTC 微秒精度',
    heartbeat_at TIMESTAMP(6) NOT NULL COMMENT '实例最近心跳时刻，UTC 微秒精度',
    capabilities JSON NOT NULL COMMENT '低基数 Runtime Provider、Schema 与执行能力 JSON，不含秘密',
    drain_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '排空状态：ACTIVE=接收任务，DRAINING=停止领取并收尾，DRAINED=已完成排空',
    PRIMARY KEY (id),
    CONSTRAINT uk_runtime_instance_key UNIQUE (instance_key),
    CONSTRAINT ck_runtime_instance_capabilities CHECK (JSON_VALID(capabilities)),
    CONSTRAINT ck_runtime_instance_drain CHECK (drain_status IN ('ACTIVE', 'DRAINING', 'DRAINED')),
    INDEX idx_runtime_instance_heartbeat (drain_status, heartbeat_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Runtime Worker 实例心跳和能力目录，不是 Session 或 Run 权威状态';

CREATE TABLE runtime_event (
    id BINARY(16) NOT NULL COMMENT '全局唯一 Runtime Event UUIDv7，同时作为 eventId',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，显式租户隔离字段',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，显式租户隔离字段',
    session_id BINARY(16) NOT NULL COMMENT '所属 Session UUIDv7',
    turn_id BINARY(16) NOT NULL COMMENT '所属 Turn UUIDv7',
    run_id BINARY(16) NOT NULL COMMENT '所属 Run UUIDv7',
    session_sequence BIGINT UNSIGNED NOT NULL COMMENT 'Session 内从 1 开始的单调 Event 序号',
    run_sequence BIGINT UNSIGNED NOT NULL COMMENT 'Run 内从 1 开始的单调 Event 序号',
    type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定小写点分隔事件类型，例如 run.succeeded',
    schema_version INT UNSIGNED NOT NULL COMMENT 'Runtime Event Schema 正整数版本，从 1 开始',
    trace_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一 Run 使用的 16 字节小写十六进制 W3C Trace ID',
    payload_storage VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '载荷保存方式：INLINE=数据库内联 JSON，OBJECT=runtime_event_payload_ref 对象引用',
    payload_json JSON NULL COMMENT 'INLINE 时保存不含秘密和隐藏推理过程的 Event JSON；OBJECT 时为空',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT 'Event 事实发生时刻，UTC 微秒精度',
    fencing_token BIGINT UNSIGNED NOT NULL COMMENT '写入时的 Run 当前栅栏令牌；0 只允许用于尚未 Claim 的接受事件',
    PRIMARY KEY (id),
    CONSTRAINT fk_runtime_event_session FOREIGN KEY (session_id) REFERENCES session (id),
    CONSTRAINT fk_runtime_event_turn FOREIGN KEY (turn_id) REFERENCES turn (id),
    CONSTRAINT fk_runtime_event_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT uk_runtime_event_session_sequence UNIQUE (session_id, session_sequence),
    CONSTRAINT uk_runtime_event_run_sequence UNIQUE (run_id, run_sequence),
    CONSTRAINT ck_runtime_event_sequence CHECK (session_sequence >= 1 AND run_sequence >= 1),
    CONSTRAINT ck_runtime_event_schema CHECK (schema_version >= 1),
    CONSTRAINT ck_runtime_event_trace CHECK (trace_id REGEXP '^[0-9a-f]{32}$'),
    CONSTRAINT ck_runtime_event_payload_storage CHECK (payload_storage IN ('INLINE', 'OBJECT')),
    CONSTRAINT ck_runtime_event_payload_shape CHECK (
        (payload_storage = 'INLINE' AND payload_json IS NOT NULL)
        OR
        (payload_storage = 'OBJECT' AND payload_json IS NULL)
    ),
    INDEX idx_runtime_event_session_replay (session_id, session_sequence, id),
    INDEX idx_runtime_event_scope_time (organization_id, project_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='只追加 Runtime Event Log；SSE 和恢复均消费该权威事实';

CREATE TABLE runtime_event_payload_ref (
    event_id BINARY(16) NOT NULL COMMENT '所属 Runtime Event UUIDv7，同时作为一对一主键',
    object_uri VARCHAR(1024) NOT NULL COMMENT '受控 Object Storage URI；不是授权凭据',
    content_hash BINARY(32) NOT NULL COMMENT '对象内容 SHA-256 原始 32 字节摘要',
    object_size BIGINT UNSIGNED NOT NULL COMMENT '对象大小，单位字节',
    media_type VARCHAR(255) NOT NULL COMMENT '对象具体媒体类型，例如 application/json',
    encryption_metadata JSON NULL COMMENT '可选非敏感加密元数据 JSON，不保存密钥材料',
    PRIMARY KEY (event_id),
    CONSTRAINT fk_runtime_event_payload_ref_event FOREIGN KEY (event_id) REFERENCES runtime_event (id),
    CONSTRAINT ck_runtime_event_payload_ref_metadata CHECK (
        encryption_metadata IS NULL OR JSON_VALID(encryption_metadata))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='大 Event Payload 的 ObjectRef 元数据；Event 本身保持追加且不可修改';

CREATE TABLE approval (
    id BINARY(16) NOT NULL COMMENT 'Approval 的 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，显式租户隔离字段',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，显式租户隔离字段',
    run_id BINARY(16) NOT NULL COMMENT '所属 Run UUIDv7',
    tool_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '待执行 Tool 的稳定名称',
    action_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '待审批动作稳定代码',
    argument_hash BINARY(32) NOT NULL COMMENT '原始 Tool 参数 SHA-256 原始 32 字节摘要，决策后不可替换',
    policy_version VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建审批时固定的 Permission Policy 版本引用',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审批状态：PENDING=待决策，APPROVED=同意，REJECTED=拒绝，EXPIRED=过期，CANCELLED=所属 Run 取消',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '决策乐观锁版本，从 0 开始递增',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '审批到期时刻，UTC 微秒精度',
    decision_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '非 PENDING 状态的决策主体引用；PENDING 时为空',
    decision_at TIMESTAMP(6) NULL COMMENT '非 PENDING 状态的决策时刻；PENDING 时为空，UTC 微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Approval 创建时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT ck_approval_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_approval_decision CHECK (
        (status = 'PENDING' AND decision_by IS NULL AND decision_at IS NULL)
        OR
        (status <> 'PENDING' AND decision_by IS NOT NULL AND decision_at IS NOT NULL)
    ),
    INDEX idx_approval_scope_pending (organization_id, project_id, status, expires_at, id),
    INDEX idx_approval_run_status (run_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='参数 Hash 固定、乐观锁决策的 HITL Approval 权威记录';

CREATE TABLE runtime_agent_state (
    id BINARY(16) NOT NULL COMMENT 'Agent State Version 的 UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，显式租户隔离字段',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，显式租户隔离字段',
    session_id BINARY(16) NOT NULL COMMENT '所属 Session UUIDv7',
    run_id BINARY(16) NOT NULL COMMENT '产生该 State Version 的 Run UUIDv7',
    agent_key VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Snapshot 内 Agent 稳定 Key',
    state_key VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Provider 中立状态 Key',
    item_index INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '列表状态元素下标；标量状态使用 0',
    state_version BIGINT UNSIGNED NOT NULL COMMENT '相同 Session、Agent、State Key 与下标内从 1 开始的版本',
    state_storage VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态保存方式：INLINE=数据库内联 JSON，OBJECT=Object Storage 引用',
    state_json JSON NULL COMMENT 'INLINE 时保存 Provider 中立状态 JSON；OBJECT 时为空，不含秘密和隐藏推理过程',
    object_uri VARCHAR(1024) NULL COMMENT 'OBJECT 时保存受控对象 URI；INLINE 时为空，URI 不是授权凭据',
    object_size BIGINT UNSIGNED NULL COMMENT 'OBJECT 时保存对象大小，单位字节；INLINE 时为空',
    media_type VARCHAR(255) NULL COMMENT 'OBJECT 时保存具体媒体类型；INLINE 时为空',
    content_hash BINARY(32) NOT NULL COMMENT '状态内容 SHA-256 原始 32 字节摘要',
    committed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Checkpoint 可见性：0=尚未提交不可恢复，1=已提交可被引用',
    fencing_token BIGINT UNSIGNED NOT NULL COMMENT '写入 State Version 时的 Run 当前栅栏令牌',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'State Version 创建时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_runtime_agent_state_session FOREIGN KEY (session_id) REFERENCES session (id),
    CONSTRAINT fk_runtime_agent_state_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT uk_runtime_agent_state_version UNIQUE (
        session_id, agent_key, state_key, item_index, state_version),
    CONSTRAINT ck_runtime_agent_state_version CHECK (state_version >= 1),
    CONSTRAINT ck_runtime_agent_state_storage CHECK (state_storage IN ('INLINE', 'OBJECT')),
    CONSTRAINT ck_runtime_agent_state_shape CHECK (
        (state_storage = 'INLINE' AND state_json IS NOT NULL AND object_uri IS NULL
            AND object_size IS NULL AND media_type IS NULL)
        OR
        (state_storage = 'OBJECT' AND state_json IS NULL AND object_uri IS NOT NULL
            AND object_size IS NOT NULL AND media_type IS NOT NULL)
    ),
    CONSTRAINT ck_runtime_agent_state_committed CHECK (committed IN (0, 1)),
    INDEX idx_runtime_agent_state_latest (
        session_id, agent_key, state_key, item_index, committed, state_version DESC, id),
    INDEX idx_runtime_agent_state_run (run_id, fencing_token, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Provider 中立追加式 Agent State Version；不使用 AgentScope 自动建表';

CREATE TABLE runtime_checkpoint (
    id BINARY(16) NOT NULL COMMENT 'Runtime Checkpoint 的 UUIDv7 主键',
    run_id BINARY(16) NOT NULL COMMENT '所属 Run UUIDv7',
    sequence BIGINT UNSIGNED NOT NULL COMMENT 'Run 内从 1 开始的 Checkpoint 序号',
    agent_state_id BINARY(16) NOT NULL COMMENT '被引用且已提交的 Agent State Version 行 UUIDv7',
    agent_state_version BIGINT UNSIGNED NOT NULL COMMENT '被引用 State Version 的业务版本号，从 1 开始',
    event_sequence BIGINT UNSIGNED NOT NULL COMMENT '恢复后继续消费的 Run Event 序号，从 1 开始',
    content_hash BINARY(32) NOT NULL COMMENT 'Checkpoint 描述 SHA-256 原始 32 字节摘要',
    recoverable BOOLEAN NOT NULL COMMENT '恢复可见性：0=完整性未确认不可恢复，1=已确认可恢复',
    fencing_token BIGINT UNSIGNED NOT NULL COMMENT '写入 Checkpoint 时的 Run 当前栅栏令牌',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Checkpoint 创建时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_runtime_checkpoint_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT fk_runtime_checkpoint_state FOREIGN KEY (agent_state_id) REFERENCES runtime_agent_state (id),
    CONSTRAINT uk_runtime_checkpoint_run_sequence UNIQUE (run_id, sequence),
    CONSTRAINT ck_runtime_checkpoint_sequence CHECK (
        sequence >= 1 AND agent_state_version >= 1 AND event_sequence >= 1),
    CONSTRAINT ck_runtime_checkpoint_recoverable CHECK (recoverable IN (0, 1)),
    INDEX idx_runtime_checkpoint_recovery (run_id, recoverable, sequence DESC, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='只引用已提交 State Version 的追加式恢复 Checkpoint';

CREATE TABLE usage_record (
    id BINARY(16) NOT NULL COMMENT 'Usage Record 的 UUIDv7 主键',
    run_id BINARY(16) NOT NULL COMMENT '所属 Run UUIDv7',
    event_id BINARY(16) NOT NULL COMMENT '证明本次用量的 Runtime Event UUIDv7',
    provider VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider 稳定标识',
    model VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '可选模型稳定标识',
    tool VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '可选 Tool 稳定标识',
    provider_request_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '可选 Provider 请求去重标识，不得保存认证信息',
    input_units BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输入计量单位数，例如 Token 或字符，具体维度由 Provider 定义',
    output_units BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输出计量单位数，例如 Token 或字符，具体维度由 Provider 定义',
    duration_millis BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '调用持续时间，单位毫秒',
    estimated BOOLEAN NOT NULL DEFAULT FALSE COMMENT '计量性质：0=Provider 返回实测值，1=平台估算值',
    price_version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选价格表版本；本阶段不计算账单',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '用量发生时刻，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_usage_record_run FOREIGN KEY (run_id) REFERENCES run (id),
    CONSTRAINT fk_usage_record_event FOREIGN KEY (event_id) REFERENCES runtime_event (id),
    CONSTRAINT uk_usage_record_provider_request UNIQUE (provider, provider_request_id),
    CONSTRAINT ck_usage_record_estimated CHECK (estimated IN (0, 1)),
    INDEX idx_usage_record_run_time (run_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='追加式 Runtime Usage 事实；支持 Provider Request ID 幂等去重';

CREATE TABLE runtime_idempotency_record (
    scope_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等作用域类型，例如 SESSION_CREATE 或 TURN_ACCEPT',
    scope_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等作用域的规范 UUIDv7 字符串',
    idempotency_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '调用方提供且在作用域内唯一的幂等键',
    request_hash BINARY(32) NOT NULL COMMENT '规范请求 SHA-256 原始 32 字节摘要；同 Key 不同 Hash 冲突',
    result_ref VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'COMPLETED 时保存类型前缀和 UUIDv7 的结果引用；其他状态为空',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等状态：IN_PROGRESS=首次事务执行中，COMPLETED=成功可复用，FAILED=失败不可复用',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '记录可清理时刻，UTC 微秒精度；清理不删除已创建资源',
    created_at TIMESTAMP(6) NOT NULL COMMENT '幂等记录创建时刻，UTC 微秒精度',
    PRIMARY KEY (scope_type, scope_id, idempotency_key),
    CONSTRAINT ck_runtime_idempotency_status CHECK (status IN (
        'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_runtime_idempotency_result CHECK (
        (status = 'COMPLETED' AND result_ref IS NOT NULL)
        OR
        (status <> 'COMPLETED' AND result_ref IS NULL)
    ),
    INDEX idx_runtime_idempotency_expiry (status, expires_at, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Runtime 命令幂等权威记录；同 Key 不同 Request Hash 必须冲突';

CREATE TABLE runtime_outbox (
    event_id BINARY(16) NOT NULL COMMENT '全局唯一 Outbox Event UUIDv7 主键',
    aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型，例如 session、turn 或 run',
    aggregate_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合的规范 UUIDv7 字符串',
    event_type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定事件类型，例如 run.succeeded',
    payload_storage VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '载荷保存方式：INLINE=数据库内联 JSON，OBJECT=Object Storage 引用',
    payload_json JSON NULL COMMENT 'INLINE 时保存不含秘密的 Outbox JSON；OBJECT 时为空',
    object_uri VARCHAR(1024) NULL COMMENT 'OBJECT 时保存受控对象 URI；INLINE 时为空，URI 不是授权凭据',
    object_hash BINARY(32) NULL COMMENT 'OBJECT 时保存 SHA-256 原始 32 字节摘要；INLINE 时为空',
    object_size BIGINT UNSIGNED NULL COMMENT 'OBJECT 时保存对象大小，单位字节；INLINE 时为空',
    media_type VARCHAR(255) NULL COMMENT 'OBJECT 时保存具体媒体类型；INLINE 时为空',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '投递状态：PENDING=待投递，PUBLISHED=已确认，FAILED=达到重试终态',
    available_at TIMESTAMP(6) NOT NULL COMMENT '最早可 Claim 投递的时刻，UTC 微秒精度',
    attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '投递尝试次数，从 0 开始',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Outbox 创建时刻，UTC 微秒精度',
    published_at TIMESTAMP(6) NULL COMMENT 'PUBLISHED 状态的确认时刻；其他状态为空，UTC 微秒精度',
    PRIMARY KEY (event_id),
    CONSTRAINT ck_runtime_outbox_payload_storage CHECK (payload_storage IN ('INLINE', 'OBJECT')),
    CONSTRAINT ck_runtime_outbox_payload_shape CHECK (
        (payload_storage = 'INLINE' AND payload_json IS NOT NULL AND object_uri IS NULL
            AND object_hash IS NULL AND object_size IS NULL AND media_type IS NULL)
        OR
        (payload_storage = 'OBJECT' AND payload_json IS NULL AND object_uri IS NOT NULL
            AND object_hash IS NOT NULL AND object_size IS NOT NULL AND media_type IS NOT NULL)
    ),
    CONSTRAINT ck_runtime_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_runtime_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR
        (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    INDEX idx_runtime_outbox_claim (status, available_at, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='与 Runtime 聚合事务同库提交的可靠 Outbox；本阶段不宣称已投递';

DELIMITER $$

CREATE TRIGGER trg_session_immutable_snapshot
BEFORE UPDATE ON session
FOR EACH ROW
BEGIN
    IF NEW.deployment_id <> OLD.deployment_id
        OR NEW.revision_id <> OLD.revision_id
        OR NEW.snapshot_id <> OLD.snapshot_id
        OR NEW.snapshot_hash <> OLD.snapshot_hash THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'session revision and snapshot are immutable';
    END IF;
END$$

CREATE TRIGGER trg_turn_fencing_monotonic
BEFORE UPDATE ON turn
FOR EACH ROW
BEGIN
    IF NEW.fencing_token < OLD.fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale turn fencing token';
    END IF;
END$$

CREATE TRIGGER trg_run_fencing_monotonic
BEFORE UPDATE ON run
FOR EACH ROW
BEGIN
    IF NEW.fencing_token < OLD.fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale run fencing token';
    END IF;
END$$

CREATE TRIGGER trg_runtime_work_item_fencing_monotonic
BEFORE UPDATE ON runtime_work_item
FOR EACH ROW
BEGIN
    IF NEW.fencing_token < OLD.fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale work item fencing token';
    END IF;
END$$

CREATE TRIGGER trg_runtime_event_validate_insert
BEFORE INSERT ON runtime_event
FOR EACH ROW
BEGIN
    DECLARE current_session_sequence BIGINT UNSIGNED;
    DECLARE current_run_sequence BIGINT UNSIGNED;
    DECLARE current_fencing_token BIGINT UNSIGNED;

    SELECT event_sequence INTO current_session_sequence FROM session WHERE id = NEW.session_id;
    SELECT event_sequence, fencing_token INTO current_run_sequence, current_fencing_token
        FROM run WHERE id = NEW.run_id;
    IF NEW.session_sequence <> current_session_sequence
        OR NEW.run_sequence <> current_run_sequence THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runtime event sequence was not atomically allocated';
    END IF;
    IF NEW.fencing_token <> current_fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale runtime event fencing token';
    END IF;
END$$

CREATE TRIGGER trg_runtime_event_reject_update
BEFORE UPDATE ON runtime_event
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runtime event is append-only';
END$$

CREATE TRIGGER trg_runtime_event_reject_delete
BEFORE DELETE ON runtime_event
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runtime event cannot be deleted';
END$$

CREATE TRIGGER trg_runtime_event_payload_ref_reject_update
BEFORE UPDATE ON runtime_event_payload_ref
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runtime event payload reference is immutable';
END$$

CREATE TRIGGER trg_runtime_event_payload_ref_reject_delete
BEFORE DELETE ON runtime_event_payload_ref
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runtime event payload reference cannot be deleted';
END$$

CREATE TRIGGER trg_runtime_agent_state_validate_insert
BEFORE INSERT ON runtime_agent_state
FOR EACH ROW
BEGIN
    DECLARE current_fencing_token BIGINT UNSIGNED;
    SELECT fencing_token INTO current_fencing_token FROM run WHERE id = NEW.run_id;
    IF NEW.fencing_token <> current_fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale agent state fencing token';
    END IF;
END$$

CREATE TRIGGER trg_runtime_agent_state_validate_update
BEFORE UPDATE ON runtime_agent_state
FOR EACH ROW
BEGIN
    DECLARE current_fencing_token BIGINT UNSIGNED;
    SELECT fencing_token INTO current_fencing_token FROM run WHERE id = NEW.run_id;
    IF OLD.committed = TRUE OR NEW.committed <> TRUE
        OR NEW.fencing_token <> OLD.fencing_token
        OR NEW.fencing_token <> current_fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'agent state update is not a current one-time commit';
    END IF;
END$$

CREATE TRIGGER trg_runtime_checkpoint_validate_insert
BEFORE INSERT ON runtime_checkpoint
FOR EACH ROW
BEGIN
    DECLARE current_fencing_token BIGINT UNSIGNED;
    DECLARE state_committed BOOLEAN;
    DECLARE stored_state_version BIGINT UNSIGNED;
    DECLARE state_run_id BINARY(16);

    SELECT fencing_token INTO current_fencing_token FROM run WHERE id = NEW.run_id;
    SELECT committed, state_version, run_id
        INTO state_committed, stored_state_version, state_run_id
        FROM runtime_agent_state WHERE id = NEW.agent_state_id;
    IF NEW.fencing_token <> current_fencing_token THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stale checkpoint fencing token';
    END IF;
    IF state_committed <> TRUE OR NEW.agent_state_version <> stored_state_version
        OR state_run_id <> NEW.run_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'checkpoint state version is not committed';
    END IF;
END$$

DELIMITER ;
