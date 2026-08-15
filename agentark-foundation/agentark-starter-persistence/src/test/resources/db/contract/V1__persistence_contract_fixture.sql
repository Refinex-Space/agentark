-- 该表只存在于 Testcontainers 临时 Schema，用于验证 MyBatis-Plus 持久化语义，不属于任何业务模型。
CREATE TABLE persistence_contract_record (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    record_key VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_persistence_contract_scope_key UNIQUE (organization_id, record_key)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
