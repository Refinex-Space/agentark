-- Phase 08 创建 Control AI 资产目录、不可变版本和 Secret 元数据；不包含 Runtime 执行逻辑。

-- Agent稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE agent (
    id BINARY(16) NOT NULL COMMENT 'Agent UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Agent显示名称',
    description VARCHAR(512) NULL COMMENT 'Agent用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Agent状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_agent_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_agent_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_agent_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_agent_version CHECK (version >= 0),
    INDEX idx_agent_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent稳定身份与可归档元数据';

-- Prompt稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE prompt (
    id BINARY(16) NOT NULL COMMENT 'Prompt UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Prompt显示名称',
    description VARCHAR(512) NULL COMMENT 'Prompt用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Prompt状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_prompt_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_prompt_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_prompt_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_prompt_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_prompt_version CHECK (version >= 0),
    INDEX idx_prompt_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Prompt稳定身份与可归档元数据';

-- Prompt Version 明确保存模板、变量 Schema、用途和规范 Hash。
CREATE TABLE prompt_version (
    id BINARY(16) NOT NULL COMMENT 'Prompt Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属 Prompt UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Prompt 内从 1 开始单调递增的不可变版本号',
    template_text MEDIUMTEXT NOT NULL COMMENT 'Prompt 模板内容，不得包含 Secret 明文',
    variable_schema JSON NOT NULL COMMENT 'Prompt 输入变量 JSON Schema',
    purpose VARCHAR(255) NOT NULL COMMENT 'Prompt 版本用途说明',
    payload_json JSON NOT NULL COMMENT '模板、变量 Schema 与用途的规范化语言中立 JSON',
    content_hash BINARY(32) NOT NULL COMMENT '模板、变量 Schema 与用途规范化内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_prompt_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_prompt_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_prompt_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES prompt (id, project_id, organization_id),
    CONSTRAINT ck_prompt_version_number CHECK (version_number > 0),
    CONSTRAINT ck_prompt_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_prompt_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Prompt 模板、变量 Schema 与用途的不可变版本';

-- Model Provider 保存平台中立 Provider Descriptor，不依赖厂商 SDK 类型。
CREATE TABLE model_provider (
    id BINARY(16) NOT NULL COMMENT 'Model Provider UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一 Provider 稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Model Provider 显示名称',
    description VARCHAR(512) NULL COMMENT 'Model Provider 用途说明，可为空且不得保存 Secret 值',
    provider_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider 类型：OPENAI_COMPATIBLE=OpenAI 兼容协议，ANTHROPIC=Anthropic 协议，GEMINI=Gemini 协议，OLLAMA=Ollama 协议，CUSTOM=自定义适配器',
    descriptor_json JSON NOT NULL COMMENT 'Provider 能力和非敏感连接描述，不得包含 API Key、Token 或 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Model Provider 状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_model_provider_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_model_provider_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_model_provider_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_model_provider_type CHECK (provider_type IN ('OPENAI_COMPATIBLE', 'ANTHROPIC', 'GEMINI', 'OLLAMA', 'CUSTOM')),
    CONSTRAINT ck_model_provider_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_model_provider_version CHECK (version >= 0),
    INDEX idx_model_provider_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台中立 Model Provider 稳定描述符';

-- Model Profile 固定模型、能力、参数和 SecretRef，只引用凭据而不保存值。
CREATE TABLE model_profile (
    id BINARY(16) NOT NULL COMMENT 'Model Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属 Model Provider UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Provider 内从 1 开始单调递增的不可变 Profile 版本号',
    model_name VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Provider 识别的模型名称，不作为厂商 SDK 类型',
    capabilities_json JSON NOT NULL COMMENT '模型能力集合：TOOL=工具调用，VISION=视觉，STRUCTURED_OUTPUT=结构化输出，STREAMING=流式输出',
    parameters_json JSON NOT NULL COMMENT '模型参数及其约束的语言中立 JSON',
    credential_secret_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '凭据 SecretRef，格式为 secret://<scope>/<name>；为空表示无需凭据',
    payload_json JSON NOT NULL COMMENT '模型名称、能力、参数与 SecretRef 的规范化语言中立 JSON',
    content_hash BINARY(32) NOT NULL COMMENT '模型名称、能力、参数和 SecretRef 规范化内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_model_profile_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_model_profile_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_model_profile_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES model_provider (id, project_id, organization_id),
    CONSTRAINT ck_model_profile_number CHECK (version_number > 0),
    CONSTRAINT ck_model_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_model_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型能力、参数和凭据引用的不可变 Profile';

-- MCP Server稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE mcp_server (
    id BINARY(16) NOT NULL COMMENT 'MCP Server UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'MCP Server显示名称',
    description VARCHAR(512) NULL COMMENT 'MCP Server用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MCP Server状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_mcp_server_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_mcp_server_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_mcp_server_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_mcp_server_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_mcp_server_version CHECK (version >= 0),
    INDEX idx_mcp_server_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP Server稳定身份与可归档元数据';

-- MCP Server Version 固定 Transport、Endpoint、TLS/Auth 引用和 SSRF 防御信息。
CREATE TABLE mcp_server_version (
    id BINARY(16) NOT NULL COMMENT 'MCP Server Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属 MCP Server UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'MCP Server 内从 1 开始单调递增的不可变版本号',
    transport VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MCP Transport：STREAMABLE_HTTP=可流式 HTTP，SSE=服务端事件流，STDIO=标准输入输出',
    endpoint_uri VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Remote Transport HTTPS Endpoint；STDIO 时为空，保存前必须通过 SSRF 信息模型校验',
    command_name VARCHAR(255) NULL COMMENT 'STDIO 可执行命令名称；Remote Transport 时为空，不在 Control 执行',
    transport_config_json JSON NOT NULL COMMENT '超时、参数、非敏感 Header 名和网络策略，不得包含 Secret 值',
    tls_secret_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'TLS 材料 SecretRef，格式为 secret://<scope>/<name>；为空表示使用平台默认信任',
    auth_secret_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '认证材料 SecretRef，格式为 secret://<scope>/<name>；为空表示无需认证',
    ssrf_policy_json JSON NOT NULL COMMENT '协议、Host、Port、DNS 重绑定、私网和云元数据地址拒绝策略',
    payload_json JSON NOT NULL COMMENT 'Transport、连接和安全策略的规范化语言中立 JSON',
    content_hash BINARY(32) NOT NULL COMMENT 'Transport、连接配置、SecretRef 和 SSRF 策略规范化内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_mcp_server_version_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_mcp_server_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_mcp_server_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_mcp_server_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES mcp_server (id, project_id, organization_id),
    CONSTRAINT ck_mcp_server_version_number CHECK (version_number > 0),
    CONSTRAINT ck_mcp_server_version_transport CHECK (transport IN ('STREAMABLE_HTTP', 'SSE', 'STDIO')),
    CONSTRAINT ck_mcp_server_version_endpoint CHECK ((transport IN ('STREAMABLE_HTTP', 'SSE') AND endpoint_uri IS NOT NULL AND command_name IS NULL) OR (transport = 'STDIO' AND endpoint_uri IS NULL AND command_name IS NOT NULL)),
    CONSTRAINT ck_mcp_server_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_mcp_server_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP Transport、连接与安全策略的不可变版本';

-- MCP Tool Descriptor 是某个 Server Version 的发现结果快照，健康状态另行采集。
CREATE TABLE mcp_tool_descriptor (
    id BINARY(16) NOT NULL COMMENT 'MCP Tool Descriptor UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    mcp_server_version_id BINARY(16) NOT NULL COMMENT '所属 MCP Server Version UUIDv7',
    tool_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Server Version 内唯一工具名称，区分大小写',
    description VARCHAR(1024) NULL COMMENT '工具用途说明，不作为授权依据',
    argument_schema JSON NOT NULL COMMENT '工具参数 JSON Schema 快照',
    access_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具访问模式：READ=只读，WRITE=写入，READ_WRITE=读写',
    risk_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具风险等级：LOW=低风险，MEDIUM=中风险，HIGH=高风险，CRITICAL=严重风险',
    idempotency VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等语义：IDEMPOTENT=幂等，NON_IDEMPOTENT=非幂等，UNKNOWN=未知',
    permission_metadata JSON NOT NULL COMMENT 'Allowlist、权限键和人工审批要求，不包含凭据',
    content_hash BINARY(32) NOT NULL COMMENT 'Descriptor 规范化内容的 SHA-256 摘要',
    created_at TIMESTAMP(6) NOT NULL COMMENT '快照创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_mcp_tool_descriptor_version_name UNIQUE (mcp_server_version_id, tool_name),
    CONSTRAINT fk_mcp_tool_descriptor_version_scope FOREIGN KEY (mcp_server_version_id, project_id, organization_id)
        REFERENCES mcp_server_version (id, project_id, organization_id),
    CONSTRAINT ck_mcp_tool_descriptor_access CHECK (access_mode IN ('READ', 'WRITE', 'READ_WRITE')),
    CONSTRAINT ck_mcp_tool_descriptor_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_mcp_tool_descriptor_idempotency CHECK (idempotency IN ('IDEMPOTENT', 'NON_IDEMPOTENT', 'UNKNOWN')),
    INDEX idx_mcp_tool_descriptor_scope (organization_id, project_id, mcp_server_version_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP Server Version 的工具能力与安全元数据快照';

-- Skill稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE skill (
    id BINARY(16) NOT NULL COMMENT 'Skill UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Skill显示名称',
    description VARCHAR(512) NULL COMMENT 'Skill用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Skill状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_skill_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_skill_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_skill_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_skill_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_skill_version CHECK (version >= 0),
    INDEX idx_skill_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Skill稳定身份与可归档元数据';

-- Skill Version 固定 ObjectRef、Hash、来源、许可证、签名和兼容要求，本阶段不执行 Artifact。
CREATE TABLE skill_version (
    id BINARY(16) NOT NULL COMMENT 'Skill Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属 Skill UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Skill 内从 1 开始单调递增的不可变版本号',
    artifact_uri VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不含临时签名参数和凭据的持久 ObjectRef URI',
    artifact_hash BINARY(32) NOT NULL COMMENT 'Skill Artifact 的 SHA-256 摘要',
    artifact_size BIGINT NOT NULL COMMENT 'Skill Artifact 非负字节数',
    media_type VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Skill Artifact 具体媒体类型',
    source_uri VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Skill 来源 URI，不包含认证信息、Query 或 Fragment',
    license_expression VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Skill SPDX 许可证表达式或受控许可证标识',
    signature_json JSON NULL COMMENT '可选签名算法、签发者和签名引用元数据，不保存私钥',
    compatibility_json JSON NOT NULL COMMENT 'AgentArk、Runtime 与平台兼容要求',
    payload_json JSON NOT NULL COMMENT 'Artifact、来源、许可证、签名与兼容要求的规范化语言中立 JSON',
    content_hash BINARY(32) NOT NULL COMMENT 'Artifact 元数据、来源、许可证与兼容要求规范化内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_skill_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_skill_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_skill_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES skill (id, project_id, organization_id),
    CONSTRAINT ck_skill_version_number CHECK (version_number > 0),
    CONSTRAINT ck_skill_version_size CHECK (artifact_size >= 0),
    CONSTRAINT ck_skill_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_skill_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Skill Artifact、来源、许可证和兼容要求的不可变版本';

-- Memory Profile稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE memory_profile (
    id BINARY(16) NOT NULL COMMENT 'Memory Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Memory Profile显示名称',
    description VARCHAR(512) NULL COMMENT 'Memory Profile用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Memory Profile状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_memory_profile_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_memory_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_memory_profile_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_memory_profile_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_memory_profile_version CHECK (version >= 0),
    INDEX idx_memory_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memory Profile稳定身份与可归档元数据';

-- Memory Profile Version按 Owner 和版本号只追加，业务层不提供更新或物理删除入口。
CREATE TABLE memory_profile_version (
    id BINARY(16) NOT NULL COMMENT 'Memory Profile Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属Memory Profile UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Owner 内从 1 开始单调递增的不可变版本号',
    payload_json JSON NOT NULL COMMENT '记忆范围、保留、压缩和容量策略的语言中立 JSON，不得包含 Secret 明文',
    content_hash BINARY(32) NOT NULL COMMENT '规范化版本内容的 SHA-256 摘要，用于完整性和去重',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_memory_profile_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_memory_profile_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_memory_profile_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES memory_profile (id, project_id, organization_id),
    CONSTRAINT ck_memory_profile_version_number CHECK (version_number > 0),
    CONSTRAINT ck_memory_profile_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_memory_profile_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memory Profile Version不可变内容版本';

-- Workspace Profile稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE workspace_profile (
    id BINARY(16) NOT NULL COMMENT 'Workspace Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Workspace Profile显示名称',
    description VARCHAR(512) NULL COMMENT 'Workspace Profile用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Workspace Profile状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_profile_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_workspace_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_workspace_profile_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_workspace_profile_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_workspace_profile_version CHECK (version >= 0),
    INDEX idx_workspace_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Workspace Profile稳定身份与可归档元数据';

-- Workspace Profile Version按 Owner 和版本号只追加，业务层不提供更新或物理删除入口。
CREATE TABLE workspace_profile_version (
    id BINARY(16) NOT NULL COMMENT 'Workspace Profile Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属Workspace Profile UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Owner 内从 1 开始单调递增的不可变版本号',
    payload_json JSON NOT NULL COMMENT '文件系统、路径、配额和隔离策略的语言中立 JSON，不得包含 Secret 明文',
    content_hash BINARY(32) NOT NULL COMMENT '规范化版本内容的 SHA-256 摘要，用于完整性和去重',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_profile_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_workspace_profile_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_workspace_profile_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES workspace_profile (id, project_id, organization_id),
    CONSTRAINT ck_workspace_profile_version_number CHECK (version_number > 0),
    CONSTRAINT ck_workspace_profile_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_workspace_profile_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Workspace Profile Version不可变内容版本';

-- Sandbox Profile稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE sandbox_profile (
    id BINARY(16) NOT NULL COMMENT 'Sandbox Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Sandbox Profile显示名称',
    description VARCHAR(512) NULL COMMENT 'Sandbox Profile用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Sandbox Profile状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_sandbox_profile_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_sandbox_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_sandbox_profile_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_sandbox_profile_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_sandbox_profile_version CHECK (version >= 0),
    INDEX idx_sandbox_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sandbox Profile稳定身份与可归档元数据';

-- Sandbox Profile Version按 Owner 和版本号只追加，业务层不提供更新或物理删除入口。
CREATE TABLE sandbox_profile_version (
    id BINARY(16) NOT NULL COMMENT 'Sandbox Profile Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属Sandbox Profile UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Owner 内从 1 开始单调递增的不可变版本号',
    payload_json JSON NOT NULL COMMENT '运行时、资源、网络、镜像和超时策略的语言中立 JSON，不得包含 Secret 明文',
    content_hash BINARY(32) NOT NULL COMMENT '规范化版本内容的 SHA-256 摘要，用于完整性和去重',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_sandbox_profile_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_sandbox_profile_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_sandbox_profile_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES sandbox_profile (id, project_id, organization_id),
    CONSTRAINT ck_sandbox_profile_version_number CHECK (version_number > 0),
    CONSTRAINT ck_sandbox_profile_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_sandbox_profile_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sandbox Profile Version不可变内容版本';

-- Permission Policy稳定身份属于单个项目，更新元数据不会覆盖任何历史版本。
CREATE TABLE permission_policy (
    id BINARY(16) NOT NULL COMMENT 'Permission Policy UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    asset_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Permission Policy显示名称',
    description VARCHAR(512) NULL COMMENT 'Permission Policy用途说明，可为空且不得保存 Secret 值',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Permission Policy状态：ACTIVE=启用，ARCHIVED=归档',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_permission_policy_project_key UNIQUE (project_id, asset_key),
    CONSTRAINT uk_permission_policy_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_permission_policy_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_permission_policy_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_permission_policy_version CHECK (version >= 0),
    INDEX idx_permission_policy_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Permission Policy稳定身份与可归档元数据';

-- Permission Policy Version按 Owner 和版本号只追加，业务层不提供更新或物理删除入口。
CREATE TABLE permission_policy_version (
    id BINARY(16) NOT NULL COMMENT 'Permission Policy Version UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    owner_id BINARY(16) NOT NULL COMMENT '所属Permission Policy UUIDv7',
    version_number BIGINT NOT NULL COMMENT 'Owner 内从 1 开始单调递增的不可变版本号',
    payload_json JSON NOT NULL COMMENT '平台、组织、项目、环境与 Agent 组合规则、默认 Decision、审批和超时策略的语言中立 JSON，不得包含 Secret 明文',
    content_hash BINARY(32) NOT NULL COMMENT '规范化版本内容的 SHA-256 摘要，用于完整性和去重',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态：DRAFT=草稿，PUBLISHED=已发布，ARCHIVED=归档',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_permission_policy_version_owner_number UNIQUE (owner_id, version_number),
    CONSTRAINT uk_permission_policy_version_owner_hash UNIQUE (owner_id, content_hash),
    CONSTRAINT fk_permission_policy_version_owner_scope FOREIGN KEY (owner_id, project_id, organization_id)
        REFERENCES permission_policy (id, project_id, organization_id),
    CONSTRAINT ck_permission_policy_version_number CHECK (version_number > 0),
    CONSTRAINT ck_permission_policy_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX idx_permission_policy_version_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Permission Policy Version不可变内容版本';

-- Secret Metadata 只定位外部 Provider 中的值，不保存、回显或记录值本身。
CREATE TABLE secret_metadata (
    id BINARY(16) NOT NULL COMMENT 'Secret Metadata UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    secret_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一 Secret 元数据标识，仅保存规范化 ASCII Key',
    name VARCHAR(128) NOT NULL COMMENT 'Secret 元数据显示名称，不包含 Secret 值',
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外部 Provider 类型：LOCAL_FILE=仅开发本地文件，VAULT=HashiCorp Vault，AWS_SECRETS_MANAGER=AWS Secrets Manager，AZURE_KEY_VAULT=Azure Key Vault，GCP_SECRET_MANAGER=GCP Secret Manager，CUSTOM=自定义 SPI',
    external_path VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外部 Provider 中的非敏感定位路径，不得包含凭据、Query 或 Fragment',
    external_version VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '外部 Provider 可选版本标识，不保存 Secret 值',
    secret_scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Secret Scope：PROJECT=项目级，ENVIRONMENT=环境绑定后可用',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Secret Metadata 状态：ENABLED=启用，DISABLED=停用，REVOKED=撤销',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_secret_metadata_project_key UNIQUE (project_id, secret_key),
    CONSTRAINT uk_secret_metadata_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_secret_metadata_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_secret_metadata_provider CHECK (provider IN ('LOCAL_FILE', 'VAULT', 'AWS_SECRETS_MANAGER', 'AZURE_KEY_VAULT', 'GCP_SECRET_MANAGER', 'CUSTOM')),
    CONSTRAINT ck_secret_metadata_scope CHECK (secret_scope IN ('PROJECT', 'ENVIRONMENT')),
    CONSTRAINT ck_secret_metadata_status CHECK (status IN ('ENABLED', 'DISABLED', 'REVOKED')),
    CONSTRAINT ck_secret_metadata_version CHECK (version >= 0),
    INDEX idx_secret_metadata_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部 Secret Provider 定位和 Scope 元数据，不保存 Secret 值';

-- Secret Binding 把环境别名绑定到同一 Project 的 Secret Metadata。
CREATE TABLE secret_binding (
    id BINARY(16) NOT NULL COMMENT 'Secret Binding UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    environment_id BINARY(16) NOT NULL COMMENT '所属环境 UUIDv7',
    secret_metadata_id BINARY(16) NOT NULL COMMENT '被绑定 Secret Metadata UUIDv7',
    binding_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '环境内唯一绑定别名，仅保存规范化 ASCII Key',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Secret Binding 状态：ACTIVE=启用，DISABLED=停用',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体的稳定标识，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体的稳定标识，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_secret_binding_environment_key UNIQUE (environment_id, binding_key),
    CONSTRAINT fk_secret_binding_environment_scope FOREIGN KEY (environment_id, project_id, organization_id)
        REFERENCES environment (id, project_id, organization_id),
    CONSTRAINT fk_secret_binding_metadata_scope FOREIGN KEY (secret_metadata_id, project_id, organization_id)
        REFERENCES secret_metadata (id, project_id, organization_id),
    CONSTRAINT ck_secret_binding_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_secret_binding_version CHECK (version >= 0),
    INDEX idx_secret_binding_scope_status (organization_id, project_id, environment_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='环境与同项目 Secret Metadata 的受控绑定';

-- Phase 08 权限通过前向迁移注册，固定 UUIDv7 不与 Phase 07 冲突。
INSERT INTO permission (id, permission_key, description, risk_level, created_at) VALUES
    (UNHEX(REPLACE('019c0000-0000-7000-8000-00000000000f', '-', '')), 'catalog:read', '读取 AI 资产目录', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000010', '-', '')), 'catalog:manage', '管理 AI 资产目录及不可变版本', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000011', '-', '')), 'secret:read', '读取 Secret 非敏感元数据', 'MEDIUM', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000012', '-', '')), 'secret:manage', '管理 Secret 元数据与环境绑定', 'HIGH', '2026-08-16 00:00:00.000000');

-- 内置角色按最小权限补齐 Phase 08 权限；自定义角色保持管理员显式配置。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-08'
FROM role
JOIN permission ON permission.permission_key IN ('catalog:read', 'catalog:manage', 'secret:read', 'secret:manage')
WHERE role.built_in = TRUE AND role.role_key IN ('organization_owner', 'project_admin');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-08'
FROM role
JOIN permission ON permission.permission_key IN ('catalog:read', 'catalog:manage')
WHERE role.built_in = TRUE AND role.role_key = 'project_developer';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-08'
FROM role
JOIN permission ON permission.permission_key = 'catalog:read'
WHERE role.built_in = TRUE AND role.role_key = 'project_viewer';
