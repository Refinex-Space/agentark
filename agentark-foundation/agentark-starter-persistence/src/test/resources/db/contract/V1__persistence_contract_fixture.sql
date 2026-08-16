-- 该表只存在于 Testcontainers 临时 Schema，用于验证 MyBatis-Plus 持久化语义，不属于任何业务模型。
CREATE TABLE persistence_contract_record (
    id BINARY(16) NOT NULL COMMENT '测试记录 UUIDv7 主键，用于验证二进制 UUID 往返',
    organization_id BINARY(16) NOT NULL COMMENT '测试组织 UUIDv7，用于验证租户条件与复合唯一约束',
    record_key VARCHAR(64) NOT NULL COMMENT '组织内唯一测试记录键',
    payload JSON NOT NULL COMMENT '用于验证 JSON TypeHandler 往返的测试载荷',
    observed_at TIMESTAMP(6) NOT NULL COMMENT '用于验证 UTC 与微秒精度的观测时间',
    version BIGINT NOT NULL COMMENT '用于验证 MyBatis-Plus 乐观锁行为的版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_persistence_contract_scope_key UNIQUE (organization_id, record_key)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MyBatis-Plus 持久化契约测试临时记录';
