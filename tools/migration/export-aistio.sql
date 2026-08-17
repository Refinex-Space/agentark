-- Aistio PostgreSQL 只读导出；输出 NDJSON，严禁包含 password_hash、ciphertext、webhook_token 或 Secret 值。
-- 用法：psql "$AISTIO_DSN" -v backup_uri='object://.../backup.dump' \
--   -v backup_checksum='sha256:<64-hex>' -f tools/migration/export-aistio.sql > export.ndjson

\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- 第一行固定来源 Commit、UTC 和切换前只读备份证据。
SELECT jsonb_build_object(
    'recordType', 'header',
    'source', jsonb_build_object(
        'system', 'AISTIO',
        'commit', '0c61e7494197ded54eefdeaf9bdeb51807beb752',
        'exportedAt', to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        'timezone', 'UTC',
        'readOnlyBackup', jsonb_build_object(
            'uri', :'backup_uri',
            'checksum', :'backup_checksum'
        )
    )
)::text;

-- 本地用户只导出外部身份映射所需字段；密码摘要不进入导出。
SELECT jsonb_build_object(
    'type', 'user_identity',
    'sourceId', user_id,
    'ownerId', user_id,
    'status', 'ACTIVE',
    'updatedAt', to_char(to_timestamp(created_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'issuer', 'urn:agentscope:aistio',
        'subject', user_id,
        'displayName', username,
        'legacyRoles', string_to_array(roles_csv, ',')
    ),
    'references', '[]'::jsonb
)::text
FROM cp.users
ORDER BY user_id;

-- Environment 映射到目标 Project；旧 api_key_hash 不导出。
SELECT jsonb_build_object(
    'type', 'environment',
    'sourceId', environment_id,
    'ownerId', owner_id,
    'status', CASE WHEN archived_at IS NULL THEN 'ACTIVE' ELSE 'ARCHIVED' END,
    'updatedAt', to_char(to_timestamp(updated_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'key', lower(environment_id),
        'name', name,
        'type', type,
        'migrationState', 'REQUIRES_PROFILE_MAPPING'
    ),
    'references', jsonb_build_array('user_identity:' || owner_id)
)::text
FROM cp.environments
ORDER BY environment_id;

-- Agent 与全部历史版本一起导出；每个 snapshot_json 在工具中重新计算 Canonical Hash。
SELECT jsonb_build_object(
    'type', 'agent',
    'sourceId', a.owner_id || '/' || a.agent_id,
    'ownerId', a.owner_id,
    'sourceVersion', a.head_version,
    'status', CASE WHEN a.archived_at IS NULL THEN 'ACTIVE' ELSE 'ARCHIVED' END,
    'updatedAt', to_char(to_timestamp(a.updated_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'key', lower(a.agent_id),
        'name', a.name,
        'description', COALESCE(a.description, ''),
        'workspacePath', a.workspace_path,
        'workspaceId', a.workspace_id,
        'defaultEnvironmentId', a.default_environment_id,
        'versions', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'version', av.version,
                'createdAt', to_char(to_timestamp(av.created_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                'snapshot', av.snapshot_json::jsonb
            ) ORDER BY av.version)
            FROM cp.agent_versions av
            WHERE av.owner_id = a.owner_id AND av.agent_id = a.agent_id
        ), '[]'::jsonb)
    ),
    'references', to_jsonb(array_remove(ARRAY[
        'user_identity:' || a.owner_id,
        CASE WHEN a.default_environment_id IS NULL THEN NULL ELSE 'environment:' || a.default_environment_id END
    ], NULL))
)::text
FROM cp.agents a
ORDER BY a.owner_id, a.agent_id;

-- Vault Credential 只转换为非敏感 Provider 引用；ciphertext 永不导出。
SELECT jsonb_build_object(
    'type', 'secret_metadata',
    'sourceId', vc.credential_id,
    'ownerId', v.owner_id,
    'status', CASE WHEN v.archived_at IS NULL THEN 'ENABLED' ELSE 'DISABLED' END,
    'updatedAt', to_char(to_timestamp(v.updated_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'key', lower(vc.credential_id),
        'name', vc.label,
        'migrationState', 'REQUIRES_EXTERNAL_SECRET_REBINDING',
        'legacyType', vc.type,
        'legacyTarget', vc.target
    ),
    'references', jsonb_build_array('user_identity:' || v.owner_id)
)::text
FROM cp.vault_credentials vc
JOIN cp.vaults v ON v.vault_id = vc.vault_id
ORDER BY vc.credential_id;

-- Deployment 只导出 Revision 指针与触发器描述；Webhook Token 不导出。
SELECT jsonb_build_object(
    'type', 'deployment',
    'sourceId', deployment_id,
    'ownerId', owner_id,
    'status', CASE WHEN enabled THEN 'ENABLED' ELSE 'DISABLED' END,
    'updatedAt', to_char(to_timestamp(updated_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'name', name,
        'agentRef', 'agent:' || owner_id || '/' || agent_id,
        'agentVersion', COALESCE(agent_version, 1),
        'environmentRef', 'environment:' || environment_id,
        'triggerType', upper(trigger_type),
        'cronExpression', cron_expression,
        'legacyLastRunAt', CASE WHEN last_run_at IS NULL THEN NULL ELSE
            to_char(to_timestamp(last_run_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') END,
        'legacyLastStatus', last_status
    ),
    'references', jsonb_build_array(
        'user_identity:' || owner_id,
        'agent:' || owner_id || '/' || agent_id,
        'environment:' || environment_id
    )
)::text
FROM cp.deployments
ORDER BY deployment_id;

-- Product Session 元数据只用于活动 Owner Pin 或终态归档，不重放 Runtime 副作用。
SELECT jsonb_build_object(
    'type', 'session',
    'sourceId', session_id,
    'ownerId', owner_id,
    'sourceVersion', version,
    'status', upper(status),
    'updatedAt', to_char(to_timestamp(updated_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'agentRef', 'agent:' || COALESCE(agent_owner_id, owner_id) || '/' || agent_id,
        'agentVersion', agent_version,
        'environmentRef', 'environment:' || environment_id,
        'externalKey', external_key,
        'legacyStopReason', COALESCE(stop_reason_json::jsonb, '{}'::jsonb)
    ),
    'references', jsonb_build_array(
        'user_identity:' || owner_id,
        'agent:' || COALESCE(agent_owner_id, owner_id) || '/' || agent_id,
        'environment:' || environment_id
    )
)::text
FROM cp.sessions
ORDER BY session_id;

-- Runtime Instance 必须在 Java Runtime 重新注册；旧心跳只保留审计来源。
SELECT jsonb_build_object(
    'type', 'runtime_instance',
    'sourceId', instance_id,
    'ownerId', agent_name,
    'status', CASE WHEN healthy THEN 'ACTIVE' ELSE 'OFFLINE' END,
    'updatedAt', to_char(last_seen_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'agentName', agent_name,
        'namespace', namespace,
        'runtime', runtime,
        'framework', framework,
        'contractLevel', contract_level,
        'capabilities', COALESCE(capabilities, '[]'::jsonb),
        'source', source
    ),
    'references', '[]'::jsonb
)::text
FROM rt.data_planes
ORDER BY instance_id;

-- Runtime Command 仅归档审计，不向 Java Runtime 重放。
SELECT jsonb_build_object(
    'type', 'runtime_command',
    'sourceId', id::text,
    'ownerId', operator,
    'status', upper(status),
    'updatedAt', to_char(COALESCE(completed_at, requested_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'sessionId', session_id,
        'command', command,
        'source', source,
        'forced', forced,
        'commandId', command_id,
        'code', code
    ),
    'references', '[]'::jsonb
)::text
FROM rt.session_commands
ORDER BY requested_at, id;

-- Team/Task/Message 当前明确 DEFER，只导出 Team 主资源用于审计和 Backlog。
SELECT jsonb_build_object(
    'type', 'team',
    'sourceId', namespace || '/' || name,
    'ownerId', lead_ref,
    'status', upper(phase),
    'updatedAt', to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'name', name,
        'namespace', namespace,
        'objective', objective,
        'leadRef', lead_ref,
        'config', COALESCE(config, '{}'::jsonb)
    ),
    'references', '[]'::jsonb
)::text
FROM rt.teams
ORDER BY namespace, name;

-- 文件正文不进入 NDJSON；使用 PostgreSQL 只读定位和 SHA-256 生成 Object Store 搬运清单。
SELECT jsonb_build_object(
    'type', 'large_object',
    'sourceId', file_id,
    'ownerId', owner_id,
    'status', 'ACTIVE',
    'updatedAt', to_char(to_timestamp(created_at / 1000.0) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'payload', jsonb_build_object(
        'sourceUri', 'postgres://cp.files/' || file_id,
        'checksum', 'sha256:' || encode(rt.digest(convert_to(content, 'UTF8'), 'sha256'), 'hex'),
        'size', octet_length(convert_to(content, 'UTF8')),
        'mediaType', content_type,
        'targetNamespace', 'migration-files'
    ),
    'references', jsonb_build_array('user_identity:' || owner_id)
)::text
FROM cp.files
ORDER BY file_id;

COMMIT;
