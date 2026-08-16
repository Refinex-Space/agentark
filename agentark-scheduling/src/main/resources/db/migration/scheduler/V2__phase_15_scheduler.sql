-- Phase 15 建立 Scheduler 独占的 Trigger、Job、Attempt、Lease、Delivery、Dead Letter、幂等与 Outbox 权威事实。

CREATE TABLE trigger_definition (
    id BINARY(16) NOT NULL COMMENT 'Trigger UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于显式租户隔离',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，用于显式租户隔离',
    trigger_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内稳定 Trigger Key，不含 Secret',
    type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Trigger 类型：CRON=按表达式点火，WEBHOOK=经签名外部事件点火',
    schedule_expression VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CRON 的 Spring 六段表达式；WEBHOOK 时为空',
    time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CRON 的 IANA 时区；WEBHOOK 时为空',
    config_json JSON NOT NULL COMMENT '不含 Secret 值的版本化 Trigger 配置 JSON',
    secret_ref VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'WEBHOOK 验签外部 SecretRef；CRON 时为空且永不保存密钥值',
    target_contract VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标 Job Payload Contract 稳定版本，例如 knowledge-ingestion/v1',
    target_job_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标 Job 类型：KNOWLEDGE_INGESTION=知识摄取，RUNTIME_TURN=创建 Turn，OUTBOUND_WEBHOOK=外发 Webhook，CHANNEL_MESSAGE=渠道消息',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Trigger 状态：ENABLED=允许点火，DISABLED=暂停点火，ARCHIVED=永久归档',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Trigger 创建时间，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Trigger 最近更新时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_trigger_definition_scope_key UNIQUE (organization_id, project_id, trigger_key),
    CONSTRAINT ck_trigger_definition_type CHECK (type IN ('CRON', 'WEBHOOK')),
    CONSTRAINT ck_trigger_definition_job_type CHECK (target_job_type IN (
        'KNOWLEDGE_INGESTION', 'RUNTIME_TURN', 'OUTBOUND_WEBHOOK', 'CHANNEL_MESSAGE')),
    CONSTRAINT ck_trigger_definition_status CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_trigger_definition_shape CHECK (
        (type = 'CRON' AND schedule_expression IS NOT NULL AND time_zone IS NOT NULL AND secret_ref IS NULL)
        OR (type = 'WEBHOOK' AND schedule_expression IS NULL AND time_zone IS NULL AND secret_ref IS NOT NULL)),
    CONSTRAINT ck_trigger_definition_config CHECK (JSON_VALID(config_json)),
    INDEX idx_trigger_definition_scope_status (organization_id, project_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Scheduler 管理的 Cron 或 Webhook Trigger 定义；不保存凭据值';

CREATE TABLE trigger_cursor (
    trigger_id BINARY(16) NOT NULL COMMENT '所属 Trigger UUIDv7，同时作为一对一主键',
    next_fire_at TIMESTAMP(6) NOT NULL COMMENT '下一次计划点火时间，UTC 微秒精度',
    last_fire_at TIMESTAMP(6) NULL COMMENT '上一次成功创建幂等 Job 的计划时间；从未点火时为空',
    last_token VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上一次点火 Token；从未点火时为空',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Cursor 乐观锁版本，每次成功点火递增',
    PRIMARY KEY (trigger_id),
    CONSTRAINT fk_trigger_cursor_definition FOREIGN KEY (trigger_id) REFERENCES trigger_definition (id),
    CONSTRAINT ck_trigger_cursor_last CHECK (
        (last_fire_at IS NULL AND last_token IS NULL)
        OR (last_fire_at IS NOT NULL AND last_token IS NOT NULL)),
    INDEX idx_trigger_cursor_due (next_fire_at, trigger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Cron 计算与执行分离的持久 Cursor；推进 Cursor 只创建 Job，不执行 Handler';

CREATE TABLE job (
    id BINARY(16) NOT NULL COMMENT 'Durable Job UUIDv7 主键',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于显式租户隔离',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，用于显式租户隔离',
    type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Job 类型：KNOWLEDGE_INGESTION=知识摄取，RUNTIME_TURN=创建 Turn，OUTBOUND_WEBHOOK=外发 Webhook，CHANNEL_MESSAGE=渠道消息',
    business_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '类型内稳定幂等业务键；相同键只能绑定相同 Payload Hash',
    payload_json JSON NOT NULL COMMENT '不含 Secret、Credential 或授权 URL 的规范 Job Payload JSON',
    payload_object_uri VARCHAR(1024) NULL COMMENT '超大 Payload 的受控 Object URI；当前 v1 内联时为空，URI 不是授权凭据',
    payload_hash BINARY(32) NOT NULL COMMENT '规范 Payload 的 SHA-256 原始 32 字节摘要',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Job 状态：READY=可领取，CLAIMED=执行中，RETRY_WAIT=退避等待，SUCCEEDED=成功，DEAD_LETTERED=进入死信，CANCELLED=取消，TIMED_OUT=超时终态',
    priority INT NOT NULL DEFAULT 0 COMMENT '调度优先级，范围 -1000 至 1000，数值越大越先 Claim',
    available_at TIMESTAMP(6) NOT NULL COMMENT '最早可 Claim 时间，UTC 微秒精度',
    retry_policy_json JSON NOT NULL COMMENT '固定 Retry Budget、初始/最大退避、倍率、Jitter 与 Timeout JSON',
    idempotency_capability VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Handler 幂等能力：INHERENT=天然幂等，PROVIDER_KEY=下游幂等键，NONE=无声明且禁止自动重试',
    current_attempt INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已经创建的 Attempt 数，从 0 开始',
    current_fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次 Claim 单调递增的当前 Fencing Token；0=从未 Claim',
    claimed_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CLAIMED 时的 Worker 实例 Key；其他状态为空',
    claim_until TIMESTAMP(6) NULL COMMENT 'CLAIMED Lease 到期时间；其他状态为空，UTC 微秒精度',
    result_ref VARCHAR(1024) NULL COMMENT '成功结果的受控引用，不得包含签名 URL 或 Credential',
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败、超时或死信时稳定错误码；成功与未执行状态为空',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Job 创建时间，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Job 最近状态变化时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_job_type_business_key UNIQUE (type, business_key),
    CONSTRAINT ck_job_type CHECK (type IN (
        'KNOWLEDGE_INGESTION', 'RUNTIME_TURN', 'OUTBOUND_WEBHOOK', 'CHANNEL_MESSAGE')),
    CONSTRAINT ck_job_status CHECK (status IN (
        'READY', 'CLAIMED', 'RETRY_WAIT', 'SUCCEEDED', 'DEAD_LETTERED', 'CANCELLED', 'TIMED_OUT')),
    CONSTRAINT ck_job_priority CHECK (priority BETWEEN -1000 AND 1000),
    CONSTRAINT ck_job_retry_policy CHECK (JSON_VALID(retry_policy_json)),
    CONSTRAINT ck_job_idempotency CHECK (idempotency_capability IN ('INHERENT', 'PROVIDER_KEY', 'NONE')),
    CONSTRAINT ck_job_claim CHECK (
        (status = 'CLAIMED' AND claimed_by IS NOT NULL AND claim_until IS NOT NULL AND current_fencing_token > 0)
        OR (status <> 'CLAIMED' AND claimed_by IS NULL AND claim_until IS NULL)),
    INDEX idx_job_claim (type, status, available_at, priority DESC, id),
    INDEX idx_job_expired_claim (status, claim_until, id),
    INDEX idx_job_scope_status (organization_id, project_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Scheduler 至少一次派发的 MySQL 权威 Durable Job；Redis 不保存唯一事实';

CREATE TABLE job_attempt (
    id BINARY(16) NOT NULL COMMENT 'Job Attempt UUIDv7 主键',
    job_id BINARY(16) NOT NULL COMMENT '所属 Durable Job UUIDv7',
    attempt_number INT UNSIGNED NOT NULL COMMENT 'Job 内从 1 开始的单调 Attempt 序号',
    owner VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行该 Attempt 的 Worker 实例 Key',
    fencing_token BIGINT UNSIGNED NOT NULL COMMENT 'Claim 时分配的单调 Fencing Token，必须大于 0',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Attempt 状态：RUNNING=执行中，SUCCEEDED=成功，FAILED=失败，TIMED_OUT=超时，ABANDONED=Lease 过期后被接管',
    started_at TIMESTAMP(6) NOT NULL COMMENT 'Attempt 开始时间，UTC 微秒精度',
    ended_at TIMESTAMP(6) NULL COMMENT 'Attempt 终态时间；RUNNING 时为空，UTC 微秒精度',
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'FAILED、TIMED_OUT 或 ABANDONED 的稳定错误码；RUNNING 或 SUCCEEDED 时为空',
    result_ref VARCHAR(1024) NULL COMMENT '成功结果的受控引用，不含 Credential 或签名 URL',
    PRIMARY KEY (id),
    CONSTRAINT fk_job_attempt_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT uk_job_attempt_number UNIQUE (job_id, attempt_number),
    CONSTRAINT ck_job_attempt_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_job_attempt_token CHECK (fencing_token >= 1),
    CONSTRAINT ck_job_attempt_status CHECK (status IN (
        'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'ABANDONED')),
    CONSTRAINT ck_job_attempt_terminal CHECK (
        (status = 'RUNNING' AND ended_at IS NULL AND error_code IS NULL)
        OR (status = 'SUCCEEDED' AND ended_at IS NOT NULL AND error_code IS NULL)
        OR (status IN ('FAILED', 'TIMED_OUT', 'ABANDONED') AND ended_at IS NOT NULL AND error_code IS NOT NULL)),
    INDEX idx_job_attempt_job_time (job_id, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='按执行尝试追加的 Job 历史；单个 Attempt 只允许 RUNNING 转终态，重试必须新建行';

CREATE TABLE job_lease (
    job_id BINARY(16) NOT NULL COMMENT '所属 Job UUIDv7，同时作为一对一主键',
    owner VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前 Worker 实例 Key',
    fencing_token BIGINT UNSIGNED NOT NULL COMMENT '每次成功 Claim 单调递增的 Fencing Token',
    lease_until TIMESTAMP(6) NOT NULL COMMENT 'Lease 到期时间，UTC 微秒精度',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '续租乐观锁版本，从 0 开始递增',
    PRIMARY KEY (job_id),
    CONSTRAINT fk_job_lease_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT ck_job_lease_token CHECK (fencing_token >= 1),
    INDEX idx_job_lease_expiry (lease_until, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Job 当前 Owner 与单调 Fencing Token；任何完成或 Delivery 更新都必须校验';

CREATE TABLE delivery (
    id BINARY(16) NOT NULL COMMENT 'Outbound Webhook 或 Channel Delivery UUIDv7 主键',
    job_id BINARY(16) NOT NULL COMMENT '所属 Job UUIDv7',
    channel_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Delivery 渠道类型：WEBHOOK=HTTPS Webhook，AGENTSCOPE=AgentScope Channel Bridge',
    endpoint_identity VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '不含 Credential 的固定目标身份，不直接保存任意授权 URL',
    provider_idempotency_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Provider 幂等键；Provider 无能力时为空且 Job 默认禁止自动重试',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Delivery 状态：PENDING=待投递，SENDING=发送中，SUCCEEDED=成功，RETRY_WAIT=退避等待，FAILED=失败，CANCELLED=取消',
    provider_message_id VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'Provider 返回的消息标识；未成功时可为空',
    response_summary VARCHAR(4096) NULL COMMENT '有界脱敏响应摘要，不保存消息正文、Token 或 Credential',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Delivery 创建时间，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Delivery 最近状态变化时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT uk_delivery_provider_key UNIQUE (provider_idempotency_key),
    CONSTRAINT ck_delivery_channel_type CHECK (channel_type IN ('WEBHOOK', 'AGENTSCOPE')),
    CONSTRAINT ck_delivery_status CHECK (status IN (
        'PENDING', 'SENDING', 'SUCCEEDED', 'RETRY_WAIT', 'FAILED', 'CANCELLED')),
    INDEX idx_delivery_job_status (job_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='外部副作用的持久 Delivery 与 Provider 幂等回执，不保存 Credential';

CREATE TABLE dead_letter (
    id BINARY(16) NOT NULL COMMENT 'Dead Letter UUIDv7 主键',
    job_id BINARY(16) NOT NULL COMMENT 'Retry Budget 耗尽的 Job UUIDv7，每个 Job 最多一个 Dead Letter',
    final_attempt_id BINARY(16) NOT NULL COMMENT '进入 Dead Letter 的最终 Job Attempt UUIDv7',
    reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不含敏感数据的稳定失败原因代码',
    redrive_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已授权 Redrive 次数，从 0 开始递增',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Dead Letter 状态：OPEN=待处理，REDRIVEN=已重新入队，RESOLVED=确认不再执行',
    redriven_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'REDRIVEN 时的已认证操作者稳定引用；其他状态为空',
    redrive_reason VARCHAR(255) NULL COMMENT 'REDRIVEN 时的人工原因；其他状态为空且不得含 Payload 或 Secret',
    redriven_at TIMESTAMP(6) NULL COMMENT 'REDRIVEN 时间；其他状态为空，UTC 微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Dead Letter 创建时间，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT 'Dead Letter 最近更新时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT fk_dead_letter_job FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT fk_dead_letter_attempt FOREIGN KEY (final_attempt_id) REFERENCES job_attempt (id),
    CONSTRAINT uk_dead_letter_job UNIQUE (job_id),
    CONSTRAINT ck_dead_letter_status CHECK (status IN ('OPEN', 'REDRIVEN', 'RESOLVED')),
    CONSTRAINT ck_dead_letter_redrive CHECK (
        (status = 'REDRIVEN' AND redriven_by IS NOT NULL AND redrive_reason IS NOT NULL AND redriven_at IS NOT NULL)
        OR (status <> 'REDRIVEN' AND redriven_by IS NULL AND redrive_reason IS NULL AND redriven_at IS NULL)),
    INDEX idx_dead_letter_scope_status (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Retry Budget 耗尽后的可授权 Redrive 事实；Redrive 保留原 Job 与 Attempt 历史';

CREATE TABLE scheduler_idempotency_record (
    id BINARY(16) NOT NULL COMMENT 'Scheduler 幂等或 Webhook Nonce 记录 UUIDv7 主键',
    scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等范围类型：WEBHOOK_NONCE=Webhook 防重放，COMMAND=管理命令，HANDLER=Handler 结果',
    scope_id BINARY(16) NOT NULL COMMENT '所属 Trigger、Job 或其他 Scheduler 聚合 UUIDv7',
    idempotency_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '范围内幂等键或 Webhook Nonce，不包含 Credential',
    request_hash BINARY(32) NOT NULL COMMENT '规范请求 SHA-256 原始 32 字节摘要，用于冲突检测',
    result_ref VARCHAR(1024) NULL COMMENT '可选结果引用，不含签名 URL 或 Credential',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等状态：PROCESSING=处理中，COMPLETED=已完成，FAILED=明确失败',
    expires_at TIMESTAMP(6) NOT NULL COMMENT '记录允许清理的最早时间，UTC 微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT '记录创建时间，UTC 微秒精度',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '记录最近更新时间，UTC 微秒精度',
    PRIMARY KEY (id),
    CONSTRAINT uk_scheduler_idempotency_scope_key UNIQUE (scope_type, scope_id, idempotency_key),
    CONSTRAINT ck_scheduler_idempotency_scope CHECK (scope_type IN ('WEBHOOK_NONCE', 'COMMAND', 'HANDLER')),
    CONSTRAINT ck_scheduler_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    INDEX idx_scheduler_idempotency_expiry (expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Scheduler 命令、Handler 与 Webhook Nonce 的持久幂等和 Replay Protection 记录';

CREATE TABLE scheduler_outbox (
    event_id BINARY(16) NOT NULL COMMENT 'Scheduler Outbox Event UUIDv7 主键',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型：job=Durable Job，dead_letter=Dead Letter，audit=管理审计',
    aggregate_id BINARY(16) NOT NULL COMMENT '聚合 UUIDv7，仅作 Scheduler Schema 内逻辑关联',
    type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定小写点分隔事件类型，例如 job.succeeded',
    payload_json JSON NOT NULL COMMENT '不含 Secret、Credential、消息正文或任意响应正文的事件 JSON',
    payload_object_uri VARCHAR(1024) NULL COMMENT '超大事件 Payload 的受控 Object URI；当前 v1 内联时为空',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Outbox 状态：PENDING=待投递，CLAIMED=已领取，PUBLISHED=已发布，FAILED=本次失败',
    available_at TIMESTAMP(6) NOT NULL COMMENT '最早可投递时间，UTC 微秒精度',
    attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计投递次数，从 0 开始',
    claimed_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CLAIMED 时的 Publisher 实例 Key；其他状态为空',
    claim_until TIMESTAMP(6) NULL COMMENT 'CLAIMED 时的 Lease 到期时间；其他状态为空，UTC 微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Outbox 事件创建时间，UTC 微秒精度',
    PRIMARY KEY (event_id),
    CONSTRAINT ck_scheduler_outbox_aggregate CHECK (aggregate_type IN ('job', 'dead_letter', 'audit')),
    CONSTRAINT ck_scheduler_outbox_status CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_scheduler_outbox_claim CHECK (
        (status = 'CLAIMED' AND claimed_by IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'CLAIMED' AND claimed_by IS NULL AND claim_until IS NULL)),
    INDEX idx_scheduler_outbox_claim (status, available_at, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Scheduler 本地事务 Outbox；Job 状态和事件在同一 Schema 事务内原子提交';
