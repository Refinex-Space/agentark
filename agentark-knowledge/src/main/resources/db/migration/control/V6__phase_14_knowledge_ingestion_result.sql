-- Phase 14 只在 Control Schema 记录 Worker 提交的不可变摄取结果，并扩展既有 Outbox 聚合类型。

-- 为 Knowledge Revision 增加合法 Outbox 聚合类型；历史迁移不可修改，因此使用增量约束变更。
ALTER TABLE control_outbox DROP CHECK ck_control_outbox_aggregate_type;
ALTER TABLE control_outbox
    MODIFY aggregate_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '聚合类型：agent_revision=Agent Revision，deployment=Deployment，knowledge_revision=Knowledge Revision';
ALTER TABLE control_outbox
    ADD CONSTRAINT ck_control_outbox_aggregate_type
        CHECK (aggregate_type IN ('agent_revision', 'deployment', 'knowledge_revision'));
ALTER TABLE control_outbox
    COMMENT = '与 Agent Revision、Deployment 或 Knowledge Revision 聚合本地事务原子写入的事务 Outbox';

-- 每个 Scheduler Attempt 只追加一个结果；Worker 无 Control Schema 权限，只能通过 Internal Command 提交。
CREATE TABLE knowledge_ingestion_result (
    id BINARY(16) NOT NULL COMMENT '摄取结果 UUIDv7 主键，由 Worker 为当前 Attempt 生成',
    request_id BINARY(16) NOT NULL COMMENT 'Control 摄取请求 UUIDv7，与固定 Revision 和租户范围联合校验',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7，用于项目内幂等和授权隔离',
    knowledge_revision_id BINARY(16) NOT NULL COMMENT '固定 Knowledge Revision UUIDv7，成功校验后才允许转换为 READY',
    scheduler_job_id BINARY(16) NOT NULL COMMENT 'Scheduler Job UUIDv7，仅用于跨平面关联，不建立跨 Schema 外键',
    attempt_id BINARY(16) NOT NULL COMMENT '当前摄取 Attempt UUIDv7；失败重试必须使用新 Attempt',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内 Internal Command 幂等键，8 至 128 个受限字符',
    document_count INT NOT NULL COMMENT 'Worker 实际处理文档修订数；成功时必须大于 0 并等于 Revision 绑定数',
    chunk_count INT NOT NULL COMMENT '完成 Qdrant 数量与 Checksum 校验的 Chunk 数；成功时必须大于 0',
    checksum BINARY(32) NOT NULL COMMENT 'Chunk 身份、文本摘要和向量位模式形成的 SHA-256 原始 32 字节',
    artifact_refs_json JSON NOT NULL COMMENT 'Chunk 或 Vector Manifest 不可变 ObjectRef 数组，不含签名 URL、Secret 或文档正文',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '摄取结果状态：SUCCEEDED=解析、Embedding、写入和校验成功，FAILED=当前 Attempt 明确失败',
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败稳定代码；SUCCEEDED 时必须为空，FAILED 时必须为大写下划线代码',
    completed_at TIMESTAMP(6) NOT NULL COMMENT 'Worker 完成当前 Attempt 的时间，UTC，微秒精度',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'Control 接受结果的时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交结果的内部服务主体稳定引用，不保存 Token 或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_ingestion_result_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_knowledge_ingestion_result_attempt UNIQUE (knowledge_revision_id, attempt_id),
    CONSTRAINT uk_knowledge_ingestion_result_key UNIQUE (project_id, idempotency_key),
    CONSTRAINT fk_knowledge_ingestion_result_request_scope
        FOREIGN KEY (request_id, project_id, organization_id)
        REFERENCES knowledge_ingestion_request (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_ingestion_result_revision_scope
        FOREIGN KEY (knowledge_revision_id, project_id, organization_id)
        REFERENCES knowledge_revision (id, project_id, organization_id),
    CONSTRAINT ck_knowledge_ingestion_result_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_knowledge_ingestion_result_counts CHECK (document_count >= 0 AND chunk_count >= 0),
    CONSTRAINT ck_knowledge_ingestion_result_failure CHECK (
        (status = 'SUCCEEDED' AND document_count > 0 AND chunk_count > 0 AND failure_code IS NULL)
        OR (status = 'FAILED' AND failure_code IS NOT NULL)),
    INDEX idx_knowledge_ingestion_result_revision
        (organization_id, project_id, knowledge_revision_id, completed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
    COMMENT='Scheduler Worker 通过幂等 Internal Command 提交的不可变 Knowledge 摄取 Attempt 结果';
