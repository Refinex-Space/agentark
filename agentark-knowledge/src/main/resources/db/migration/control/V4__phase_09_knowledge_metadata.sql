-- Phase 09 创建 Knowledge 元数据、不可变版本、文档 ACL 和摄取请求描述；不执行真实向量摄取。

-- Knowledge Base 是项目内稳定身份，内容变化只能通过 Knowledge Revision 表达。
CREATE TABLE knowledge_base (
    id BINARY(16) NOT NULL COMMENT 'Knowledge Base UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内唯一稳定 Key，仅允许小写字母、数字和连字符',
    name VARCHAR(128) NOT NULL COMMENT 'Knowledge Base 显示名称',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Knowledge Base 用途说明，不得保存 Secret、Prompt 或文档原文',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Knowledge Base 状态：ACTIVE=允许追加内容，ARCHIVED=只保留历史且禁止追加',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '稳定身份元数据乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_project_key UNIQUE (project_id, knowledge_key),
    CONSTRAINT uk_knowledge_base_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_base_project_scope FOREIGN KEY (project_id, organization_id)
        REFERENCES project (id, organization_id),
    CONSTRAINT ck_knowledge_base_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_knowledge_base_version CHECK (version >= 0),
    INDEX idx_knowledge_base_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目内 Knowledge Base 稳定身份与可归档元数据';

-- Data Source 只保存来源描述，不保存连接器凭据或执行结果。
CREATE TABLE data_source (
    id BINARY(16) NOT NULL COMMENT 'Data Source UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_base_id BINARY(16) NOT NULL COMMENT '所属 Knowledge Base UUIDv7',
    source_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据源类型：UPLOAD=API 受控上传，URI=后续受控 URI 拉取，CONNECTOR=后续 Provider 连接器同步',
    name VARCHAR(128) NOT NULL COMMENT '数据源显示名称',
    descriptor_json JSON NOT NULL COMMENT '不含 Secret 的来源描述；本阶段不执行 URI 或连接器',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_data_source_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_data_source_base_scope FOREIGN KEY (knowledge_base_id, project_id, organization_id)
        REFERENCES knowledge_base (id, project_id, organization_id),
    CONSTRAINT ck_data_source_type CHECK (source_type IN ('UPLOAD', 'URI', 'CONNECTOR')),
    INDEX idx_data_source_scope_base (organization_id, project_id, knowledge_base_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge Base 的非敏感文档来源描述';

-- Document 是原文件修订的稳定身份，ACL 与生命周期独立维护。
CREATE TABLE document (
    id BINARY(16) NOT NULL COMMENT 'Document UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_base_id BINARY(16) NOT NULL COMMENT '所属 Knowledge Base UUIDv7',
    data_source_id BINARY(16) NOT NULL COMMENT '来源 Data Source UUIDv7',
    title VARCHAR(255) NOT NULL COMMENT '文档显示标题，不作为 Object Store 路径',
    metadata_json JSON NOT NULL COMMENT '最多 64 项非敏感检索元数据，不保存原文、Prompt 或 Secret',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Document 状态：ACTIVE=允许追加修订，DELETING=等待原文件与派生数据清理，DELETED=清理完成',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'ACL 与生命周期乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_document_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT fk_document_base_scope FOREIGN KEY (knowledge_base_id, project_id, organization_id)
        REFERENCES knowledge_base (id, project_id, organization_id),
    CONSTRAINT fk_document_source_scope FOREIGN KEY (data_source_id, project_id, organization_id)
        REFERENCES data_source (id, project_id, organization_id),
    CONSTRAINT ck_document_status CHECK (status IN ('ACTIVE', 'DELETING', 'DELETED')),
    CONSTRAINT ck_document_version CHECK (version >= 0),
    INDEX idx_document_scope_base_status (organization_id, project_id, knowledge_base_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档稳定身份、非敏感元数据和清理生命周期';

-- Document ACL 是项目授权后的第二层文档访问约束，不能替代 IAM 租户校验。
CREATE TABLE document_acl (
    document_id BINARY(16) NOT NULL COMMENT '所属 Document UUIDv7',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    subject_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ACL 主体类型：PROJECT=整个项目，USER=外部用户映射，SERVICE_ACCOUNT=服务账号，ROLE=角色',
    subject_id BINARY(16) NOT NULL COMMENT 'ACL 主体 UUIDv7；PROJECT 类型时等于 project_id',
    access_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ACL 访问级别：READ=读取，WRITE=追加文档修订，MANAGE=变更 ACL 和发起删除',
    created_at TIMESTAMP(6) NOT NULL COMMENT 'ACL 创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (document_id, subject_type, subject_id),
    CONSTRAINT fk_document_acl_document_scope FOREIGN KEY (document_id, project_id, organization_id)
        REFERENCES document (id, project_id, organization_id),
    CONSTRAINT ck_document_acl_subject CHECK (subject_type IN ('PROJECT', 'USER', 'SERVICE_ACCOUNT', 'ROLE')),
    CONSTRAINT ck_document_acl_access CHECK (access_level IN ('READ', 'WRITE', 'MANAGE')),
    CONSTRAINT ck_document_acl_project_subject CHECK (subject_type <> 'PROJECT' OR subject_id = project_id),
    INDEX idx_document_acl_scope_subject (organization_id, project_id, subject_type, subject_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目 IAM 授权之后生效的文档显式访问控制条目';

-- Document Revision 保存不可变原文件引用与完整性元数据，不保存授权 URL。
CREATE TABLE document_revision (
    id BINARY(16) NOT NULL COMMENT 'Document Revision UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_base_id BINARY(16) NOT NULL COMMENT '所属 Knowledge Base UUIDv7',
    document_id BINARY(16) NOT NULL COMMENT '所属 Document UUIDv7',
    revision_number BIGINT NOT NULL COMMENT 'Document 内从 1 开始单调递增的不可变版本号',
    original_file_name VARCHAR(255) NOT NULL COMMENT '原始文件名，仅作展示和追踪，不参与对象路径生成',
    object_uri VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不含凭据、Query 或 Fragment 的 object、s3、oss 或 cos URI',
    content_hash BINARY(32) NOT NULL COMMENT '原文件 SHA-256 摘要，用于上传完整性和追踪',
    content_size BIGINT NOT NULL COMMENT '原文件字节数，必须大于等于零',
    content_type VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原文件具体媒体类型，不允许通配类型',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_document_revision_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_document_revision_number UNIQUE (document_id, revision_number),
    CONSTRAINT uk_document_revision_hash UNIQUE (document_id, content_hash),
    CONSTRAINT fk_document_revision_document_scope FOREIGN KEY (document_id, project_id, organization_id)
        REFERENCES document (id, project_id, organization_id),
    CONSTRAINT fk_document_revision_base_scope FOREIGN KEY (knowledge_base_id, project_id, organization_id)
        REFERENCES knowledge_base (id, project_id, organization_id),
    CONSTRAINT ck_document_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_document_revision_size CHECK (content_size >= 0),
    INDEX idx_document_revision_scope_base (organization_id, project_id, knowledge_base_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变原文件提交及 Object Store 完整性引用';

-- Parser Profile 固定文档解析器选择和参数，不包含 Provider 实现类型。
CREATE TABLE parser_profile (
    id BINARY(16) NOT NULL COMMENT 'Parser Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    profile_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内 Parser Profile 稳定 Key',
    version_number BIGINT NOT NULL COMMENT '同 Key 下从 1 开始单调递增的不可变版本号',
    config_json JSON NOT NULL COMMENT '解析器能力和参数的语言中立规范化 JSON',
    content_hash BINARY(32) NOT NULL COMMENT 'config_json 规范内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Profile 状态：DRAFT=草稿，PUBLISHED=允许 Knowledge Revision 引用，DEPRECATED=停止新增引用',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_parser_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_parser_profile_key_version UNIQUE (project_id, profile_key, version_number),
    CONSTRAINT uk_parser_profile_key_hash UNIQUE (project_id, profile_key, content_hash),
    CONSTRAINT fk_parser_profile_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_parser_profile_version CHECK (version_number > 0),
    CONSTRAINT ck_parser_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    INDEX idx_parser_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档解析器选择和参数的不可变 Profile 版本';

-- Chunk Profile 固定切分策略和参数，不包含 Provider 实现类型。
CREATE TABLE chunk_profile (
    id BINARY(16) NOT NULL COMMENT 'Chunk Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    profile_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内 Chunk Profile 稳定 Key',
    version_number BIGINT NOT NULL COMMENT '同 Key 下从 1 开始单调递增的不可变版本号',
    config_json JSON NOT NULL COMMENT '切分策略和参数的语言中立规范化 JSON',
    content_hash BINARY(32) NOT NULL COMMENT 'config_json 规范内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Profile 状态：DRAFT=草稿，PUBLISHED=允许 Knowledge Revision 引用，DEPRECATED=停止新增引用',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_chunk_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_chunk_profile_key_version UNIQUE (project_id, profile_key, version_number),
    CONSTRAINT uk_chunk_profile_key_hash UNIQUE (project_id, profile_key, content_hash),
    CONSTRAINT fk_chunk_profile_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_chunk_profile_version CHECK (version_number > 0),
    CONSTRAINT ck_chunk_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    INDEX idx_chunk_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档切分策略和参数的不可变 Profile 版本';

-- Embedding Profile 固定 Provider 描述、模型参数和 SecretRef，不保存凭据值。
CREATE TABLE embedding_profile (
    id BINARY(16) NOT NULL COMMENT 'Embedding Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    profile_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内 Embedding Profile 稳定 Key',
    version_number BIGINT NOT NULL COMMENT '同 Key 下从 1 开始单调递增的不可变版本号',
    config_json JSON NOT NULL COMMENT 'Provider 描述、模型和参数的语言中立规范化 JSON，不含凭据值',
    credential_secret_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选凭据 SecretRef，格式为 secret://<scope>/<name>，绝不保存 Secret 值',
    content_hash BINARY(32) NOT NULL COMMENT 'config_json 与 SecretRef 规范内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Profile 状态：DRAFT=草稿，PUBLISHED=允许 Knowledge Revision 引用，DEPRECATED=停止新增引用',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_embedding_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_embedding_profile_key_version UNIQUE (project_id, profile_key, version_number),
    CONSTRAINT uk_embedding_profile_key_hash UNIQUE (project_id, profile_key, content_hash),
    CONSTRAINT fk_embedding_profile_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_embedding_profile_version CHECK (version_number > 0),
    CONSTRAINT ck_embedding_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    INDEX idx_embedding_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Embedding Provider 描述、参数和凭据引用的不可变 Profile 版本';

-- Retrieval Profile 固定召回、过滤与 Rerank 参数，不绑定具体向量数据库。
CREATE TABLE retrieval_profile (
    id BINARY(16) NOT NULL COMMENT 'Retrieval Profile UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    profile_key VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内 Retrieval Profile 稳定 Key',
    version_number BIGINT NOT NULL COMMENT '同 Key 下从 1 开始单调递增的不可变版本号',
    config_json JSON NOT NULL COMMENT '召回、过滤和 Rerank 参数的语言中立规范化 JSON',
    content_hash BINARY(32) NOT NULL COMMENT 'config_json 规范内容的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Profile 状态：DRAFT=草稿，PUBLISHED=允许 Knowledge Revision 引用，DEPRECATED=停止新增引用',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_retrieval_profile_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_retrieval_profile_key_version UNIQUE (project_id, profile_key, version_number),
    CONSTRAINT uk_retrieval_profile_key_hash UNIQUE (project_id, profile_key, content_hash),
    CONSTRAINT fk_retrieval_profile_project_scope FOREIGN KEY (project_id, organization_id) REFERENCES project (id, organization_id),
    CONSTRAINT ck_retrieval_profile_version CHECK (version_number > 0),
    CONSTRAINT ck_retrieval_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    INDEX idx_retrieval_profile_scope_status (organization_id, project_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='召回、过滤和 Rerank 参数的不可变 Profile 版本';

-- Knowledge Revision 固定文档集合和四类 Profile，仅状态字段可按状态机更新。
CREATE TABLE knowledge_revision (
    id BINARY(16) NOT NULL COMMENT 'Knowledge Revision UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_base_id BINARY(16) NOT NULL COMMENT '所属 Knowledge Base UUIDv7',
    revision_number BIGINT NOT NULL COMMENT 'Knowledge Base 内从 1 开始单调递增的不可变版本号',
    parser_profile_id BINARY(16) NOT NULL COMMENT '固定 Parser Profile UUIDv7',
    chunk_profile_id BINARY(16) NOT NULL COMMENT '固定 Chunk Profile UUIDv7',
    embedding_profile_id BINARY(16) NOT NULL COMMENT '固定 Embedding Profile UUIDv7',
    retrieval_profile_id BINARY(16) NOT NULL COMMENT '固定 Retrieval Profile UUIDv7',
    content_hash BINARY(32) NOT NULL COMMENT '文档修订集合和四类 Profile 绑定的 SHA-256 摘要',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Revision 状态：CREATED=已创建，INGESTING=已描述摄取，VERIFYING=等待验证，READY=可被 Agent Revision 引用，FAILED=摄取或验证失败，DEPRECATED=停止新增引用，DELETING=清理中，DELETED=清理完成',
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'FAILED 状态的稳定失败代码；其他状态必须为空',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '仅状态更新使用的乐观锁版本号，从 0 开始且只允许递增',
    created_at TIMESTAMP(6) NOT NULL COMMENT '创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    updated_at TIMESTAMP(6) NOT NULL COMMENT '状态最后更新时间，UTC，微秒精度',
    updated_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最后更新主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_revision_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_knowledge_revision_number UNIQUE (knowledge_base_id, revision_number),
    CONSTRAINT uk_knowledge_revision_hash UNIQUE (knowledge_base_id, content_hash),
    CONSTRAINT fk_knowledge_revision_base_scope FOREIGN KEY (knowledge_base_id, project_id, organization_id) REFERENCES knowledge_base (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_revision_parser_scope FOREIGN KEY (parser_profile_id, project_id, organization_id) REFERENCES parser_profile (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_revision_chunk_scope FOREIGN KEY (chunk_profile_id, project_id, organization_id) REFERENCES chunk_profile (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_revision_embedding_scope FOREIGN KEY (embedding_profile_id, project_id, organization_id) REFERENCES embedding_profile (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_revision_retrieval_scope FOREIGN KEY (retrieval_profile_id, project_id, organization_id) REFERENCES retrieval_profile (id, project_id, organization_id),
    CONSTRAINT ck_knowledge_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_knowledge_revision_status CHECK (status IN ('CREATED', 'INGESTING', 'VERIFYING', 'READY', 'FAILED', 'DEPRECATED', 'DELETING', 'DELETED')),
    CONSTRAINT ck_knowledge_revision_failure CHECK ((status = 'FAILED' AND failure_code IS NOT NULL) OR (status <> 'FAILED' AND failure_code IS NULL)),
    CONSTRAINT ck_knowledge_revision_version CHECK (version >= 0),
    INDEX idx_knowledge_revision_scope_status (organization_id, project_id, knowledge_base_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档修订集合与四类 Profile 绑定形成的不可变 Knowledge Revision';

-- Knowledge Revision Document 固定 Revision 使用的有序文档修订集合。
CREATE TABLE knowledge_revision_document (
    knowledge_revision_id BINARY(16) NOT NULL COMMENT '所属 Knowledge Revision UUIDv7',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    document_revision_id BINARY(16) NOT NULL COMMENT '被固定引用的 Document Revision UUIDv7',
    ordinal_value INT NOT NULL COMMENT 'Revision 内从 0 开始的稳定文档顺序',
    created_at TIMESTAMP(6) NOT NULL COMMENT '绑定创建时间，UTC，微秒精度',
    created_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (knowledge_revision_id, document_revision_id),
    CONSTRAINT uk_knowledge_revision_document_ordinal UNIQUE (knowledge_revision_id, ordinal_value),
    CONSTRAINT fk_knowledge_revision_document_revision_scope FOREIGN KEY (knowledge_revision_id, project_id, organization_id) REFERENCES knowledge_revision (id, project_id, organization_id),
    CONSTRAINT fk_knowledge_revision_document_source_scope FOREIGN KEY (document_revision_id, project_id, organization_id) REFERENCES document_revision (id, project_id, organization_id),
    CONSTRAINT ck_knowledge_revision_document_ordinal CHECK (ordinal_value >= 0),
    INDEX idx_knowledge_revision_document_scope (organization_id, project_id, document_revision_id, knowledge_revision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Knowledge Revision 固定引用的有序 Document Revision 集合';

-- 摄取请求只描述异步意图，不创建 Scheduler Job 或向量摄取结果。
CREATE TABLE knowledge_ingestion_request (
    id BINARY(16) NOT NULL COMMENT 'Knowledge Ingestion Request UUIDv7 主键，按时间有序生成',
    organization_id BINARY(16) NOT NULL COMMENT '所属组织 UUIDv7，用于验证完整租户链',
    project_id BINARY(16) NOT NULL COMMENT '所属项目 UUIDv7',
    knowledge_revision_id BINARY(16) NOT NULL COMMENT '目标 Knowledge Revision UUIDv7',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目内请求幂等键，重复请求不得创建新描述',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求状态：DESCRIBED=已描述但未调度，CANCELLED=调度前已取消',
    requested_at TIMESTAMP(6) NOT NULL COMMENT '请求时间，UTC，微秒精度',
    requested_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求主体稳定引用，不保存显示名称或凭据',
    PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_ingestion_id_scope UNIQUE (id, project_id, organization_id),
    CONSTRAINT uk_knowledge_ingestion_idempotency UNIQUE (project_id, idempotency_key),
    CONSTRAINT fk_knowledge_ingestion_revision_scope FOREIGN KEY (knowledge_revision_id, project_id, organization_id) REFERENCES knowledge_revision (id, project_id, organization_id),
    CONSTRAINT ck_knowledge_ingestion_status CHECK (status IN ('DESCRIBED', 'CANCELLED')),
    INDEX idx_knowledge_ingestion_scope_revision (organization_id, project_id, knowledge_revision_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='尚未调度执行的异步 Knowledge 摄取请求描述';

-- Phase 09 权限通过前向迁移注册，固定 UUIDv7 不与 Phase 07 和 Phase 08 冲突。
INSERT INTO permission (id, permission_key, description, risk_level, created_at) VALUES
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000013', '-', '')), 'knowledge:read', '读取 Knowledge 元数据、文档 ACL 和可用 Revision', 'LOW', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000014', '-', '')), 'knowledge:manage', '管理 Knowledge Base、文档、Profile 和 Revision', 'HIGH', '2026-08-16 00:00:00.000000'),
    (UNHEX(REPLACE('019c0000-0000-7000-8000-000000000015', '-', '')), 'knowledge:ingest', '描述 Knowledge 摄取请求并推进受控状态', 'HIGH', '2026-08-16 00:00:00.000000');

-- 组织所有者和项目管理员拥有 Phase 09 全部权限。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-09'
FROM role
JOIN permission ON permission.permission_key IN ('knowledge:read', 'knowledge:manage', 'knowledge:ingest')
WHERE role.built_in = TRUE AND role.role_key IN ('organization_owner', 'project_admin');

-- 项目开发者可以读取、管理元数据并描述摄取请求。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-09'
FROM role
JOIN permission ON permission.permission_key IN ('knowledge:read', 'knowledge:manage', 'knowledge:ingest')
WHERE role.built_in = TRUE AND role.role_key = 'project_developer';

-- 项目只读角色只能读取 Knowledge 元数据和 READY Revision。
INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, '2026-08-16 00:00:00.000000', 'flyway:phase-09'
FROM role
JOIN permission ON permission.permission_key = 'knowledge:read'
WHERE role.built_in = TRUE AND role.role_key = 'project_viewer';
