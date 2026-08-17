-- Phase 19 扩展 Runtime 原始 Usage 的治理维度和异步汇聚状态，不访问 Control Schema。

ALTER TABLE usage_record
    ADD COLUMN organization_id BINARY(16) NULL COMMENT '所属组织 UUIDv7；迁移回填后为必填，不建立跨 Schema 外键' AFTER id,
    ADD COLUMN project_id BINARY(16) NULL COMMENT '所属项目 UUIDv7；迁移回填后为必填，不建立跨 Schema 外键' AFTER organization_id,
    ADD COLUMN session_id BINARY(16) NULL COMMENT '所属 Session UUIDv7；迁移回填后为必填' AFTER project_id,
    ADD COLUMN turn_id BINARY(16) NULL COMMENT '所属 Turn UUIDv7；迁移回填后为必填' AFTER session_id,
    ADD COLUMN agent_id BINARY(16) NULL COMMENT '可选 Agent UUIDv7；旧 Snapshot 未提供 Agent 维度时为空' AFTER turn_id,
    ADD COLUMN revision_id BINARY(16) NULL COMMENT '所属不可变 Revision UUIDv7；迁移回填后为必填且只作逻辑引用' AFTER agent_id,
    ADD COLUMN deployment_id BINARY(16) NULL COMMENT '所属 Deployment UUIDv7；迁移回填后为必填且只作逻辑引用' AFTER revision_id,
    ADD COLUMN usage_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MODEL' COMMENT '计量类型：MODEL=模型，EMBEDDING=向量嵌入，TOOL=工具，SANDBOX=沙箱' AFTER event_id,
    ADD COLUMN cached_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Provider 明确返回的缓存 Token 数，未知或不适用为 0' AFTER output_units,
    ADD COLUMN embedding_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Embedding Token 数，非嵌入用量为 0' AFTER cached_tokens,
    ADD COLUMN tool_calls BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Tool/MCP 调用次数，非工具用量为 0' AFTER embedding_tokens,
    ADD COLUMN sandbox_duration_millis BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Sandbox 执行时长，单位毫秒；非沙箱用量为 0' AFTER tool_calls,
    ADD COLUMN currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '成本 ISO 4217 三字符大写币种；未计算成本时为空' AFTER price_version,
    ADD COLUMN cost_amount DECIMAL(20,8) NOT NULL DEFAULT 0 COMMENT '按固定价格版本计算的非负成本金额，最多八位小数' AFTER currency,
    ADD COLUMN governance_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT '治理汇聚状态：PENDING=待提交，RETRY=可重试，EXPORTED=已确认，FAILED=达到重试终态' AFTER cost_amount,
    ADD COLUMN governance_attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '治理汇聚尝试次数，从 0 开始' AFTER governance_status,
    ADD COLUMN governance_available_at TIMESTAMP(6) NULL COMMENT '下一次允许治理汇聚的时间，UTC；EXPORTED/FAILED 可为空' AFTER governance_attempts,
    ADD COLUMN governance_exported_at TIMESTAMP(6) NULL COMMENT 'Control 已幂等接收时间，UTC；未成功时为空' AFTER governance_available_at;

UPDATE usage_record usage_row
JOIN run runtime_run ON runtime_run.id = usage_row.run_id
JOIN turn runtime_turn ON runtime_turn.id = runtime_run.turn_id
JOIN session runtime_session ON runtime_session.id = runtime_run.session_id
SET usage_row.organization_id = runtime_run.organization_id,
    usage_row.project_id = runtime_run.project_id,
    usage_row.session_id = runtime_run.session_id,
    usage_row.turn_id = runtime_run.turn_id,
    usage_row.revision_id = runtime_session.revision_id,
    usage_row.deployment_id = runtime_session.deployment_id,
    usage_row.governance_available_at = usage_row.occurred_at;

ALTER TABLE usage_record
    MODIFY organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，不建立跨 Schema 外键',
    MODIFY project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，不建立跨 Schema 外键',
    MODIFY session_id BINARY(16) NOT NULL COMMENT '所属 Session UUIDv7',
    MODIFY turn_id BINARY(16) NOT NULL COMMENT '所属 Turn UUIDv7',
    MODIFY revision_id BINARY(16) NOT NULL COMMENT '所属不可变 Revision UUIDv7，只作逻辑引用',
    MODIFY deployment_id BINARY(16) NOT NULL COMMENT '所属 Deployment UUIDv7，只作逻辑引用',
    ADD CONSTRAINT fk_usage_record_session FOREIGN KEY (session_id) REFERENCES session (id),
    ADD CONSTRAINT fk_usage_record_turn FOREIGN KEY (turn_id) REFERENCES turn (id),
    ADD CONSTRAINT ck_usage_record_type CHECK (usage_type IN ('MODEL', 'EMBEDDING', 'TOOL', 'SANDBOX')),
    ADD CONSTRAINT ck_usage_record_currency CHECK (currency IS NULL OR currency REGEXP '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_usage_record_cost CHECK (cost_amount >= 0),
    ADD CONSTRAINT ck_usage_record_governance_status CHECK (governance_status IN ('PENDING', 'RETRY', 'EXPORTED', 'FAILED')),
    ADD CONSTRAINT ck_usage_record_governance_exported CHECK
        ((governance_status = 'EXPORTED' AND governance_exported_at IS NOT NULL)
            OR (governance_status <> 'EXPORTED' AND governance_exported_at IS NULL)),
    ADD INDEX idx_usage_record_governance_claim
        (governance_status, governance_available_at, occurred_at, id),
    ADD INDEX idx_usage_record_scope_time
        (organization_id, project_id, occurred_at DESC, id DESC),
    ADD INDEX idx_usage_record_revision_time
        (revision_id, occurred_at DESC, id DESC);

ALTER TABLE run
    ADD COLUMN quota_reservation_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Control Quota Reservation UUIDv7 逻辑引用；无适用硬配额时为空' AFTER fencing_token,
    ADD INDEX idx_run_quota_reservation (quota_reservation_ref, status, id);
