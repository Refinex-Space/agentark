#!/usr/bin/env python3
"""把只读 Aistio 导出转换为可恢复、幂等且经 API 写入的 AgentArk 迁移计划。"""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
import urllib.parse
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from aistio_common import (
    JsonHttpClient,
    MigrationError,
    SCHEMA_VERSION,
    SOURCE_COMMIT,
    atomic_write_json,
    canonical_hash,
    canonical_json,
    load_json,
    load_json_or_ndjson,
    reject_secret_fields,
    require_sha256,
    utc_instant,
)


RESOURCE_TYPES = {
    "user_identity",
    "environment",
    "catalog_asset",
    "secret_metadata",
    "knowledge_metadata",
    "agent",
    "deployment",
    "session",
    "runtime_instance",
    "runtime_command",
    "team",
    "registration",
    "large_object",
}
OWNER_SCOPED_RESOURCE_TYPES = {
    "environment",
    "catalog_asset",
    "secret_metadata",
    "knowledge_metadata",
    "agent",
    "deployment",
    "session",
    "large_object",
}
DIRECT_STATUS = {
    "ACTIVE": "ACTIVE",
    "ARCHIVED": "ARCHIVED",
    "ENABLED": "ENABLED",
    "DISABLED": "DISABLED",
    "READY": "READY",
    "FAILED": "FAILED",
    "SUCCEEDED": "SUCCEEDED",
    "ACCEPTED": "ACCEPTED",
    "QUEUED": "QUEUED",
    "COMPLETED": "COMPLETED",
    "CANCELLED": "CANCELLED",
    "TERMINATED": "TERMINATED",
}
ACTIVE_SESSION_STATES = {"ACTIVE", "IDLE", "RUNNING", "BUSY", "WAITING", "PAUSED"}
PATH_MAPPING = re.compile(r"\{\{([^{}]+)\.([A-Za-z][A-Za-z0-9]*)}}")
UUID_V7 = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)


def now_utc() -> str:
    """返回当前 UTC 微秒时刻。"""
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def validate_export(bundle: dict[str, Any]) -> dict[str, Any]:
    """验证来源、主键、引用、时区、状态、Hash 和 Secret 最小化。"""
    if bundle.get("schemaVersion") != SCHEMA_VERSION:
        raise MigrationError("unsupported Aistio export schemaVersion")
    source = bundle.get("source")
    if not isinstance(source, dict):
        raise MigrationError("export source must be an object")
    if source.get("system") != "AISTIO" or source.get("commit") != SOURCE_COMMIT:
        raise MigrationError("export source system or commit does not match the frozen baseline")
    if source.get("timezone") != "UTC":
        raise MigrationError("export timezone must be UTC")
    source["exportedAt"] = utc_instant(source.get("exportedAt"), "source.exportedAt")
    backup = source.get("readOnlyBackup")
    if not isinstance(backup, dict):
        raise MigrationError("source.readOnlyBackup must be an object")
    require_sha256(backup.get("checksum"), "source.readOnlyBackup.checksum")
    if not isinstance(backup.get("uri"), str) or not backup["uri"].startswith(
        ("object://", "s3://", "oss://", "cos://")
    ):
        raise MigrationError("source.readOnlyBackup.uri must be an ObjectRef URI")

    resources = bundle.get("resources")
    if not isinstance(resources, list):
        raise MigrationError("export resources must be an array")
    indexed: dict[str, dict[str, Any]] = {}
    for index, resource in enumerate(resources):
        if not isinstance(resource, dict):
            raise MigrationError(f"resource at index {index} must be an object")
        resource_type = resource.get("type")
        source_id = resource.get("sourceId")
        if resource_type not in RESOURCE_TYPES:
            raise MigrationError(f"unsupported resource type at index {index}")
        if not isinstance(source_id, str) or not source_id.strip() or len(source_id) > 255:
            raise MigrationError(f"resource sourceId is invalid at index {index}")
        key = source_key(resource)
        if key in indexed:
            raise MigrationError(f"duplicate resource primary key: {key}")
        payload = resource.get("payload")
        if not isinstance(payload, dict):
            raise MigrationError(f"resource payload must be an object: {key}")
        reject_secret_fields(payload, f"$.resources[{index}].payload")
        if "updatedAt" in resource:
            resource["updatedAt"] = utc_instant(resource["updatedAt"], f"{key}.updatedAt")
        status = resource.get("status", "ACTIVE")
        if not isinstance(status, str) or not status.strip():
            raise MigrationError(f"resource status is invalid: {key}")
        resource["status"] = normalize_status(resource_type, status)
        references = resource.get("references", [])
        if not isinstance(references, list) or any(
            not isinstance(reference, str) or not reference for reference in references
        ):
            raise MigrationError(f"resource references are invalid: {key}")
        actual_hash = canonical_hash(payload)
        declared_hash = resource.get("canonicalHash")
        if declared_hash is not None and declared_hash != actual_hash:
            raise MigrationError(f"canonicalHash mismatch: {key}")
        resource["canonicalHash"] = actual_hash
        if resource_type == "agent":
            validate_agent_versions(resource, key)
        indexed[key] = resource
    for key, resource in indexed.items():
        for reference in resource.get("references", []):
            if reference not in indexed:
                raise MigrationError(f"missing foreign reference {reference} from {key}")
        if resource["type"] in OWNER_SCOPED_RESOURCE_TYPES:
            owner_id = resource.get("ownerId")
            if not isinstance(owner_id, str) or not owner_id:
                raise MigrationError(f"ownerId is required for tenant-scoped resource: {key}")
            owner_reference = f"user_identity:{owner_id}"
            if owner_reference not in resource.get("references", []):
                raise MigrationError(
                    f"tenant-scoped resource must reference its owner: {key}"
                )
    return bundle


def validate_agent_versions(resource: dict[str, Any], key: str) -> None:
    """验证旧 Agent 每个版本的 Snapshot Hash 与版本单调性。"""
    versions = resource.get("payload", {}).get("versions", [])
    if not isinstance(versions, list) or not versions:
        raise MigrationError(f"agent must contain at least one frozen version: {key}")
    previous = 0
    for version in versions:
        if not isinstance(version, dict) or not isinstance(version.get("version"), int):
            raise MigrationError(f"agent version is invalid: {key}")
        number = version["version"]
        if number <= previous:
            raise MigrationError(f"agent versions must be strictly increasing: {key}")
        previous = number
        version["createdAt"] = utc_instant(
            version.get("createdAt"), f"{key}.versions[{number}].createdAt"
        )
        snapshot = version.get("snapshot")
        if not isinstance(snapshot, dict):
            raise MigrationError(f"agent version snapshot must be an object: {key}")
        reject_secret_fields(snapshot, f"{key}.versions[{number}].snapshot")
        expected = canonical_hash(snapshot)
        declared = version.get("snapshotHash")
        if declared is not None and declared != expected:
            raise MigrationError(f"agent snapshotHash mismatch: {key}:v{number}")
        version["snapshotHash"] = expected


def normalize_status(resource_type: str, status: str) -> str:
    """把 Aistio 状态映射到迁移语义，拒绝无法解释的状态。"""
    normalized = status.strip().upper().replace("-", "_")
    if resource_type == "session":
        if normalized in ACTIVE_SESSION_STATES:
            return "GO_UNTIL_TERMINAL"
        if normalized in {"COMPLETED", "FAILED", "CANCELLED", "TERMINATED", "ARCHIVED"}:
            return "ARCHIVE_ONLY"
    if resource_type == "runtime_command" and normalized in {
        "ACCEPTED",
        "QUEUED",
        "SUCCEEDED",
        "FAILED",
        "REJECTED",
    }:
        return normalized
    if resource_type == "team" and normalized in {
        "PENDING",
        "RUNNING",
        "IDLE",
        "COMPLETED",
        "FAILED",
    }:
        return normalized
    if normalized in DIRECT_STATUS:
        return DIRECT_STATUS[normalized]
    if normalized in {"PENDING", "JOINING", "DEGRADED", "OFFLINE", "HISTORICAL"}:
        return normalized
    raise MigrationError(f"unsupported status mapping for {resource_type}")


def source_key(resource: dict[str, Any]) -> str:
    """返回跨导出文件稳定的来源主键。"""
    return f"{resource.get('type')}:{resource.get('sourceId')}"


class Planner:
    """把规范化来源资源转换为有序、可恢复的 AgentArk API 操作。"""

    def __init__(self, config: dict[str, Any]):
        reject_secret_fields(config, "$.config")
        self.config = config
        self.target = require_object(config, "target")
        self.defaults = require_object(config, "defaults")
        self.model_mappings = require_object(config, "modelMappings")
        self.principal_mappings = require_object(config, "principalMappings")
        self.secret_mappings = require_object(config, "secretMappings")
        self.webhook_secret_mappings = require_object(config, "webhookSecretMappings")
        for key in ("organizationId", "projectId", "defaultEnvironmentId"):
            require_uuid_v7(self.target, key)
        for key in (
            "memoryId",
            "memoryVersionId",
            "workspaceId",
            "workspaceVersionId",
            "sandboxId",
            "sandboxVersionId",
            "permissionPolicyId",
            "permissionPolicyVersionId",
        ):
            require_uuid_v7(self.defaults, key)
        threshold = config.get("largeObjectThresholdBytes", 65536)
        if not isinstance(threshold, int) or threshold < 1024 or threshold > 64 * 1024 * 1024:
            raise MigrationError("largeObjectThresholdBytes is invalid")
        self.large_object_threshold = threshold
        self.operations: list[dict[str, Any]] = []
        self.terminals: dict[str, str] = {}
        self.warnings: list[dict[str, str]] = []
        self.object_migrations: list[dict[str, Any]] = []
        self.target_keys: set[tuple[str, str]] = set()

    def build(self, bundle: dict[str, Any]) -> dict[str, Any]:
        """按 IAM/Catalog/Agent/Deployment/Session 顺序构建迁移计划。"""
        resources = bundle["resources"]
        priority = {
            "user_identity": 0,
            "environment": 1,
            "secret_metadata": 2,
            "knowledge_metadata": 3,
            "catalog_asset": 4,
            "agent": 5,
            "deployment": 6,
            "session": 7,
            "runtime_instance": 8,
            "runtime_command": 9,
            "registration": 10,
            "team": 11,
            "large_object": 12,
        }
        for resource in sorted(resources, key=lambda item: (priority[item["type"]], item["sourceId"])):
            getattr(self, f"_plan_{resource['type']}")(resource)
        plan = {
            "schemaVersion": SCHEMA_VERSION,
            "source": copy.deepcopy(bundle["source"]),
            "target": copy.deepcopy(self.target),
            "generatedAt": now_utc(),
            "operations": self.operations,
            "sourceCounts": {
                "total": len(resources),
                "byResourceType": dict(sorted(Counter(item["type"] for item in resources).items())),
            },
            "objectMigrations": self.object_migrations,
            "warnings": self.warnings,
        }
        plan["planHash"] = canonical_hash(
            {key: value for key, value in plan.items() if key not in {"generatedAt", "planHash"}}
        )
        return plan

    def _plan_user_identity(self, resource: dict[str, Any]) -> None:
        """外部 UserIdentity 只记录映射，不迁移密码或本地认证。"""
        issuer = require_text(resource["payload"], "issuer")
        subject = require_text(resource["payload"], "subject")
        target = self.principal_mappings.get(resource["sourceId"])
        if not isinstance(target, dict):
            raise MigrationError(
                f"principal mapping is missing: {source_key(resource)}"
            )
        self._record_operation(
            resource,
            "MAP_EXTERNAL_IDENTITY",
            mapping={
                "issuer": issuer,
                "subject": subject,
                "targetPrincipalId": require_uuid_v7(target, "targetPrincipalId"),
                "mode": "REFERENCE_ONLY",
            },
        )

    def _plan_environment(self, resource: dict[str, Any]) -> None:
        """迁移 Environment 稳定 Key 与名称，归属由目标 Project 决定。"""
        payload = resource["payload"]
        key = self._target_key("environment", require_text(payload, "key"))
        body = {"key": key, "name": require_text(payload, "name")}
        operation = self._http_operation(
            resource,
            "CREATE_ENVIRONMENT",
            "control",
            "POST",
            f"/api/v1/projects/{self.target['projectId']}/environments",
            body,
            [201],
            {"id": "/id"},
        )
        operation["reconcile"] = self._list_reconcile(
            f"/api/v1/projects/{self.target['projectId']}/environments", "key", key
        )
        self.terminals[source_key(resource)] = operation["operationId"]

    def _plan_secret_metadata(self, resource: dict[str, Any]) -> None:
        """只迁移 Secret Provider 引用，禁止值、密文或旧 Vault Master Key。"""
        payload = resource["payload"]
        secret_mapping = self.secret_mappings.get(resource["sourceId"])
        if not isinstance(secret_mapping, dict):
            raise MigrationError(
                f"external SecretRef mapping is missing: {source_key(resource)}"
            )
        body = {
            "key": self._target_key("secret", require_text(payload, "key")),
            "name": require_text(payload, "name"),
            "provider": require_text(secret_mapping, "provider"),
            "externalPath": require_text(secret_mapping, "externalPath"),
            "externalVersion": secret_mapping.get("externalVersion", ""),
            "scope": secret_mapping.get("scope", "PROJECT"),
        }
        operation = self._http_operation(
            resource,
            "CREATE_SECRET_METADATA",
            "control",
            "POST",
            f"/api/v1/projects/{self.target['projectId']}/secrets",
            body,
            [201],
            {"id": "/id"},
        )
        operation["reconcile"] = self._list_reconcile(
            f"/api/v1/projects/{self.target['projectId']}/secrets", "key", body["key"]
        )
        self.terminals[source_key(resource)] = operation["operationId"]

    def _plan_knowledge_metadata(self, resource: dict[str, Any]) -> None:
        """迁移 Knowledge Base 元数据；文档正文由 Object Store 清单单独处理。"""
        payload = resource["payload"]
        body = {
            "key": self._target_key("knowledge", require_text(payload, "key")),
            "name": require_text(payload, "name"),
            "description": payload.get("description", ""),
        }
        operation = self._http_operation(
            resource,
            "CREATE_KNOWLEDGE_BASE",
            "control",
            "POST",
            f"/api/v1/projects/{self.target['projectId']}/knowledge-bases",
            body,
            [201],
            {"id": "/id"},
        )
        operation["reconcile"] = self._list_reconcile(
            f"/api/v1/projects/{self.target['projectId']}/knowledge-bases", "key", body["key"]
        )
        self.terminals[source_key(resource)] = operation["operationId"]

    def _plan_catalog_asset(self, resource: dict[str, Any]) -> None:
        """迁移已规范化的 Prompt/Model/MCP/Skill/Profile/Policy 资产及不可变版本。"""
        payload = resource["payload"]
        kind = require_text(payload, "kind")
        key = self._target_key(f"catalog:{kind}", require_text(payload, "key"))
        path = f"/api/v1/projects/{self.target['projectId']}/catalog/{kind}"
        create = self._http_operation(
            resource,
            "CREATE_CATALOG_ASSET",
            "control",
            "POST",
            path,
            {
                "key": key,
                "name": require_text(payload, "name"),
                "description": payload.get("description", ""),
                "metadata": payload.get("metadata", {}),
            },
            [201],
            {"id": "/id"},
        )
        create["reconcile"] = self._list_reconcile(path, "key", key)
        version_payload = payload.get("versionPayload")
        if version_payload is not None:
            if not isinstance(version_payload, dict):
                raise MigrationError(f"catalog versionPayload must be an object: {source_key(resource)}")
            self._http_operation(
                resource,
                "CREATE_CATALOG_VERSION",
                "control",
                "POST",
                f"{path}/{{{{{source_key(resource)}.id}}}}/versions",
                {"payload": version_payload, "status": payload.get("versionStatus", "PUBLISHED")},
                [201],
                {"versionId": "/id", "contentHash": "/contentHash"},
                depends_on=[create["operationId"]],
            )
        self.terminals[source_key(resource)] = self.operations[-1]["operationId"]

    def _plan_agent(self, resource: dict[str, Any]) -> None:
        """逐个旧版本更新 Draft 并发布，使 Java Control 生成不可变 Snapshot。"""
        payload = resource["payload"]
        versions = payload["versions"]
        prompt_asset_key = f"prompt:{resource['sourceId']}"
        prompt_resource = {
            "type": "catalog_asset",
            "sourceId": prompt_asset_key,
            "canonicalHash": resource["canonicalHash"],
        }
        prompt_create = self._http_operation(
            prompt_resource,
            "CREATE_AGENT_PROMPT",
            "control",
            "POST",
            f"/api/v1/projects/{self.target['projectId']}/catalog/prompt",
            {
                "key": self._target_key(
                    "catalog:prompt", f"prompt-{payload.get('key', resource['sourceId'])}"
                ),
                "name": f"{require_text(payload, 'name')} Prompt",
                "description": "Aistio 迁移来源的版本化 System Prompt",
                "metadata": {},
            },
            [201],
            {"id": "/id"},
        )
        prompt_create["reconcile"] = self._list_reconcile(
            f"/api/v1/projects/{self.target['projectId']}/catalog/prompt",
            "key",
            prompt_create["body"]["key"],
        )

        prompt_version_operations: dict[int, dict[str, Any]] = {}
        for version in versions:
            number = version["version"]
            snapshot = version["snapshot"]
            prompt = snapshot.get("sysPrompt") or snapshot.get("system")
            if not isinstance(prompt, str) or not prompt:
                raise MigrationError(
                    f"agent version requires a non-empty system prompt or a manual mapping: {source_key(resource)}:v{number}"
                )
            prompt_version_key = f"prompt_version:{resource['sourceId']}:v{number}"
            prompt_version_operations[number] = self._http_operation(
                {
                    "type": "catalog_asset",
                    "sourceId": prompt_version_key,
                    "canonicalHash": version["snapshotHash"],
                },
                "CREATE_AGENT_PROMPT_VERSION",
                "control",
                "POST",
                f"/api/v1/projects/{self.target['projectId']}/catalog/prompt/"
                f"{{{{catalog_asset:{prompt_asset_key}.id}}}}/versions",
                {
                    "payload": {
                        "template": prompt,
                        "variableSchema": {"type": "object", "additionalProperties": False},
                        "purpose": "Aistio Agent Version 迁移",
                    },
                    "status": "PUBLISHED",
                },
                [201],
                {"versionId": "/id", "contentHash": "/contentHash"},
                depends_on=[prompt_create["operationId"]],
            )

        previous_operation: str | None = None
        draft_version = 0
        for index, version in enumerate(versions):
            number = version["version"]
            snapshot = version["snapshot"]
            draft = self._agent_draft(resource, snapshot, number, prompt_asset_key)
            if index == 0:
                create = self._http_operation(
                    resource,
                    "CREATE_AGENT",
                    "control",
                    "POST",
                    f"/api/v1/projects/{self.target['projectId']}/agents",
                    {
                        "key": self._target_key(
                            "agent", payload.get("key", resource["sourceId"])
                        ),
                        "name": require_text(payload, "name"),
                        "description": payload.get("description", ""),
                        "draft": draft,
                    },
                    [201],
                    {"id": "/id"},
                    depends_on=[prompt_version_operations[number]["operationId"]],
                )
                create["reconcile"] = self._list_reconcile(
                    f"/api/v1/projects/{self.target['projectId']}/agents",
                    "key",
                    create["body"]["key"],
                )
                previous_operation = create["operationId"]
            else:
                update = self._http_operation(
                    resource,
                    f"UPDATE_AGENT_DRAFT_V{number}",
                    "control",
                    "PUT",
                    f"/api/v1/projects/{self.target['projectId']}/agents/"
                    f"{{{{{source_key(resource)}.id}}}}/draft",
                    {"expectedVersion": draft_version, "draft": draft},
                    [200],
                    {"draftVersion": "/version"},
                    depends_on=[previous_operation, prompt_version_operations[number]["operationId"]],
                )
                previous_operation = update["operationId"]
                draft_version += 1
            publish = self._http_operation(
                resource,
                f"PUBLISH_AGENT_V{number}",
                "control",
                "POST",
                f"/api/v1/projects/{self.target['projectId']}/agents/"
                f"{{{{{source_key(resource)}.id}}}}/publish",
                {
                    "idempotencyKey": migration_idempotency_key(source_key(resource), f"publish-v{number}"),
                    "expectedDraftVersion": draft_version,
                },
                [201],
                {
                    "revisionId": "/id",
                    f"revisionIdV{number}": "/id",
                    "snapshotId": "/snapshotId",
                    f"snapshotIdV{number}": "/snapshotId",
                    "snapshotHash": "/contentHash",
                    f"snapshotHashV{number}": "/contentHash",
                    "sourceVersion": number,
                    "sourceSnapshotHash": version["snapshotHash"],
                },
                depends_on=[previous_operation],
            )
            previous_operation = publish["operationId"]
        if previous_operation is not None:
            self.terminals[source_key(resource)] = previous_operation

    def _agent_draft(
        self,
        resource: dict[str, Any],
        snapshot: dict[str, Any],
        version: int,
        prompt_asset_key: str,
    ) -> dict[str, Any]:
        """把 Aistio Agent Snapshot 映射为 AgentArk 强类型 Draft 引用。"""
        runtime_provider = self.defaults.get("runtimeProvider", "agentscope-java-2")
        if (
            not isinstance(runtime_provider, str)
            or re.fullmatch(r"[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*", runtime_provider) is None
            or len(runtime_provider) > 64
        ):
            raise MigrationError("runtimeProvider is invalid")
        required_capabilities = snapshot.get(
            "requiredCapabilities", ["tool-calling", "streaming"]
        )
        if (
            not isinstance(required_capabilities, list)
            or any(
                not isinstance(capability, str)
                or not capability.strip()
                or len(capability) > 64
                for capability in required_capabilities
            )
            or len(set(required_capabilities)) != len(required_capabilities)
        ):
            raise MigrationError("requiredCapabilities must be a unique string array")
        turn_timeout_seconds = int(self.defaults.get("turnTimeoutSeconds", 300))
        if turn_timeout_seconds < 1 or turn_timeout_seconds > 86400:
            raise MigrationError("turnTimeoutSeconds must be between 1 and 86400")
        model_name = snapshot.get("model")
        mapping = self.model_mappings.get(model_name)
        if not isinstance(mapping, dict):
            raise MigrationError(
                f"model mapping is missing for agent version: {source_key(resource)}:v{version}"
            )
        mcp_bindings = mapped_bindings(
            snapshot.get("mcpServers", []), require_object(self.config, "mcpMappings"), "mcp"
        )
        skill_bindings = mapped_bindings(
            snapshot.get("skills", []), require_object(self.config, "skillMappings"), "skill"
        )
        return {
            "runtimeProvider": runtime_provider,
            "requiredCapabilities": required_capabilities,
            "model": {
                "providerId": require_uuid_v7(mapping, "providerId"),
                "profileId": require_uuid_v7(mapping, "profileId"),
            },
            "prompts": [
                {
                    "promptId": {"$mapping": f"catalog_asset:{prompt_asset_key}", "field": "id"},
                    "versionId": {
                        "$mapping": f"catalog_asset:prompt_version:{resource['sourceId']}:v{version}",
                        "field": "versionId",
                    },
                    "role": "SYSTEM",
                }
            ],
            "mcpServers": mcp_bindings,
            "skills": skill_bindings,
            "knowledge": [],
            "profiles": {
                "memoryId": require_uuid_v7(self.defaults, "memoryId"),
                "memoryVersionId": require_uuid_v7(self.defaults, "memoryVersionId"),
                "workspaceId": require_uuid_v7(self.defaults, "workspaceId"),
                "workspaceVersionId": require_uuid_v7(self.defaults, "workspaceVersionId"),
                "sandboxId": require_uuid_v7(self.defaults, "sandboxId"),
                "sandboxVersionId": require_uuid_v7(self.defaults, "sandboxVersionId"),
            },
            "permissionPolicy": {
                "policyId": require_uuid_v7(self.defaults, "permissionPolicyId"),
                "versionId": require_uuid_v7(self.defaults, "permissionPolicyVersionId"),
            },
            "limits": {
                "turnTimeoutSeconds": turn_timeout_seconds,
                "maxToolCalls": max(1, min(int(snapshot.get("maxIters", 20)), 1000)),
                "maxSubAgents": max(0, min(int(snapshot.get("maxSubAgents", 0)), 100)),
            },
        }

    def _plan_deployment(self, resource: dict[str, Any]) -> None:
        """把 Environment/Agent Version 映射为 Deployment，并把触发器交给 Scheduler。"""
        payload = resource["payload"]
        agent_key = require_text(payload, "agentRef")
        environment_key = require_text(payload, "environmentRef")
        source_version = int(payload.get("agentVersion", 0))
        if source_version < 1:
            raise MigrationError(f"deployment agentVersion is invalid: {source_key(resource)}")
        create = self._http_operation(
            resource,
            "CREATE_DEPLOYMENT",
            "control",
            "POST",
            f"/api/v1/projects/{self.target['projectId']}/environments/"
            f"{{{{{environment_key}.id}}}}/deployments",
            {
                "agentId": {"$mapping": agent_key, "field": "id"},
                "revisionId": {"$mapping": agent_key, "field": f"revisionIdV{source_version}"},
                "trafficPolicy": "FULL",
                "canaryPercent": 0,
            },
            [201],
            {"id": "/id", "version": "/version"},
            depends_on=[
                self.terminals.get(agent_key),
                self.terminals.get(environment_key),
            ],
        )
        self.terminals[source_key(resource)] = create["operationId"]
        if resource["status"] == "DISABLED":
            self._http_operation(
                resource,
                "DISABLE_DEPLOYMENT",
                "control",
                "POST",
                f"/api/v1/projects/{self.target['projectId']}/environments/"
                f"{{{{{environment_key}.id}}}}/deployments/{{{{{source_key(resource)}.id}}}}/disable",
                {"expectedVersion": {"$mapping": source_key(resource), "field": "version"}},
                [200],
                {"version": "/version"},
                depends_on=[create["operationId"]],
            )
        trigger = payload.get("triggerType", "MANUAL").upper()
        if trigger in {"CRON", "WEBHOOK"}:
            trigger_config: dict[str, Any] = {
                "deploymentId": {"$mapping": source_key(resource), "field": "id"}
            }
            cron_expression = (
                require_text(payload, "cronExpression") if trigger == "CRON" else None
            )
            webhook_secret_ref = None
            if trigger == "WEBHOOK":
                webhook_mapping = self.webhook_secret_mappings.get(resource["sourceId"])
                if not isinstance(webhook_mapping, dict):
                    raise MigrationError(
                        f"webhook SecretRef mapping is missing: {source_key(resource)}"
                    )
                webhook_secret_ref = require_text(webhook_mapping, "secretRef")
            trigger_key = self._target_key(
                "scheduler-trigger", f"aistio-{trigger.lower()}-{resource['sourceId']}"
            )
            trigger_operation = self._http_operation(
                resource,
                f"CREATE_{trigger}_TRIGGER",
                "scheduler",
                "POST",
                "/api/v1/scheduler/triggers",
                {
                    "organizationId": self.target["organizationId"],
                    "projectId": self.target["projectId"],
                    "key": trigger_key,
                    "type": trigger,
                    "cronExpression": cron_expression,
                    "zoneId": self.defaults.get("schedulerZoneId", "UTC")
                    if trigger == "CRON"
                    else None,
                    "secretRef": webhook_secret_ref,
                    "targetContract": "runtime-turn-v1",
                    "targetJobType": "RUNTIME_TURN",
                    "config": trigger_config,
                },
                [201],
                {"triggerId": "/id"},
                depends_on=[create["operationId"]],
            )
            trigger_operation["reconcile"] = self._list_reconcile(
                "/api/v1/scheduler/triggers"
                f"?organizationId={self.target['organizationId']}"
                f"&projectId={self.target['projectId']}&limit=100",
                "key",
                trigger_key,
                plane="scheduler",
                append_limit=False,
            )

    def _plan_session(self, resource: dict[str, Any]) -> None:
        """活动 Session 固定在 Go Owner 排空；终态只归档映射，禁止重建伪历史。"""
        mode = resource["status"]
        mapping = {
            "owner": mode,
            "sourceSessionId": resource["sourceId"],
            "deploymentRef": resource["payload"].get("deploymentRef"),
            "sourceCanonicalHash": resource["canonicalHash"],
        }
        self._record_operation(resource, "PIN_SESSION_OWNER", mapping=mapping)

    def _plan_runtime_instance(self, resource: dict[str, Any]) -> None:
        """旧实例只用于排空审计，新 Java Runtime 必须自行注册心跳。"""
        self._record_operation(
            resource,
            "ARCHIVE_RUNTIME_INSTANCE",
            mapping={"owner": "GO_UNTIL_DRAINED", "imported": False},
        )

    def _plan_runtime_command(self, resource: dict[str, Any]) -> None:
        """命令历史归档为迁移审计，不向活动 Runtime 重放副作用。"""
        self._record_operation(
            resource,
            "ARCHIVE_RUNTIME_COMMAND",
            mapping={"replayed": False, "sourceCanonicalHash": resource["canonicalHash"]},
        )

    def _plan_team(self, resource: dict[str, Any]) -> None:
        """Team/Task 属于 DEFER，不阻塞核心 Agent/Deployment/Session 切换。"""
        self.warnings.append(
            {"sourceKey": source_key(resource), "classification": "DEFER", "reason": "TEAM_V1_OUT_OF_SCOPE"}
        )
        self._record_operation(resource, "DEFER_TEAM", mapping={"classification": "DEFER"})

    def _plan_registration(self, resource: dict[str, Any]) -> None:
        """旧 Data Plane Registration 只保留来源，Java Runtime 必须重新注册。"""
        self._record_operation(
            resource,
            "READAPT_REGISTRATION",
            mapping={"classification": "ADAPT", "requiresReregistration": True},
        )

    def _plan_large_object(self, resource: dict[str, Any]) -> None:
        """大对象只生成 Object Store 搬运清单，不把正文放入计划、报告或 Checkpoint。"""
        payload = resource["payload"]
        checksum = require_sha256(payload.get("checksum"), f"{source_key(resource)}.checksum")
        size = payload.get("size")
        if not isinstance(size, int) or size < 0:
            raise MigrationError(f"large object size is invalid: {source_key(resource)}")
        self.object_migrations.append(
            {
                "sourceKey": source_key(resource),
                "sourceUri": require_text(payload, "sourceUri"),
                "checksum": checksum,
                "size": size,
                "mediaType": require_text(payload, "mediaType"),
                "targetNamespace": payload.get("targetNamespace", "migration-artifacts"),
            }
        )
        self._record_operation(
            resource, "MIGRATE_OBJECT", mapping={"checksum": checksum, "size": size}
        )

    def _record_operation(
        self, resource: dict[str, Any], action: str, mapping: dict[str, Any]
    ) -> dict[str, Any]:
        """添加无外部副作用的本地映射操作。"""
        operation = {
            "operationId": operation_id(source_key(resource), action),
            "sourceKey": source_key(resource),
            "sourceHash": resource["canonicalHash"],
            "action": action,
            "plane": "local",
            "method": "RECORD",
            "mapping": mapping,
            "dependsOn": [],
        }
        self.operations.append(operation)
        return operation

    def _http_operation(
        self,
        resource: dict[str, Any],
        action: str,
        plane: str,
        method: str,
        path: str,
        body: Any,
        expected_statuses: list[int],
        mapping_extract: dict[str, Any],
        depends_on: list[str | None] | None = None,
    ) -> dict[str, Any]:
        """添加只通过版本化 API 执行的迁移操作。"""
        operation = {
            "operationId": operation_id(source_key(resource), action),
            "sourceKey": source_key(resource),
            "sourceHash": resource["canonicalHash"],
            "action": action,
            "plane": plane,
            "method": method,
            "path": path,
            "body": body,
            "headers": migration_headers(source_key(resource), action),
            "expectedStatuses": expected_statuses,
            "mappingExtract": mapping_extract,
            "dependsOn": [value for value in (depends_on or []) if value],
        }
        self.operations.append(operation)
        return operation

    def _list_reconcile(
        self,
        path: str,
        match_field: str,
        match_value: str,
        plane: str = "control",
        append_limit: bool = True,
    ) -> dict[str, Any]:
        """定义 Crash 后按稳定 Key 读取并恢复 Checkpoint 的策略。"""
        return {
            "plane": plane,
            "path": f"{path}?limit=100" if append_limit else path,
            "collectionPointer": "/items",
            "matchField": match_field,
            "matchValue": match_value,
        }

    def _target_key(self, namespace: str, source_value: Any) -> str:
        """生成目标稳定 Key，并在同一业务命名空间拒绝规范化碰撞。"""
        key = migrated_key(source_value)
        scoped = (namespace, key)
        if scoped in self.target_keys:
            raise MigrationError(f"target key collision: {namespace}:{key}")
        self.target_keys.add(scoped)
        return key


def mapped_bindings(values: Any, mappings: dict[str, Any], kind: str) -> list[dict[str, Any]]:
    """把 Aistio MCP/Skill 名称映射为 AgentArk 不可变版本引用。"""
    if values is None:
        return []
    if not isinstance(values, list):
        raise MigrationError(f"{kind} bindings must be an array")
    result = []
    for value in values:
        name = value if isinstance(value, str) else value.get("name") if isinstance(value, dict) else None
        mapping = mappings.get(name)
        if not isinstance(mapping, dict):
            raise MigrationError(f"{kind} mapping is missing")
        if kind == "mcp":
            allowed_tools = mapping.get("allowedTools")
            if (
                not isinstance(allowed_tools, list)
                or not allowed_tools
                or any(not isinstance(tool, str) or not tool.strip() for tool in allowed_tools)
                or len(set(allowed_tools)) != len(allowed_tools)
            ):
                raise MigrationError("mcp allowedTools must be a non-empty unique string array")
            result.append(
                {
                    "serverId": require_uuid_v7(mapping, "serverId"),
                    "versionId": require_uuid_v7(mapping, "versionId"),
                    "allowedTools": allowed_tools,
                }
            )
        else:
            result.append(
                {
                    "skillId": require_uuid_v7(mapping, "skillId"),
                    "versionId": require_uuid_v7(mapping, "versionId"),
                }
            )
    return result


def operation_id(source: str, action: str) -> str:
    """生成可重跑且不泄漏正文的稳定操作标识。"""
    return canonical_hash({"source": source, "action": action})[7:31]


def migration_idempotency_key(source: str, action: str) -> str:
    """生成适合 AgentArk Idempotency-Key 的稳定值。"""
    return f"migration-{operation_id(source, action)}"


def migration_headers(source: str, action: str) -> dict[str, str]:
    """仅对拥有幂等请求头语义的 API 附加稳定键。"""
    if action.startswith("CREATE_RUNTIME_"):
        return {"Idempotency-Key": migration_idempotency_key(source, action)}
    return {}


def migrated_key(value: Any) -> str:
    """把旧标识规范为 AgentArk 稳定小写 Key。"""
    text = re.sub(r"[^a-z0-9]+", "-", str(value).lower()).strip("-")
    if not text or not text[0].isalpha():
        text = f"migrated-{text}"
    text = text[:63].rstrip("-")
    if len(text) < 2:
        text = f"{text}-x"
    return text


def require_object(value: dict[str, Any], key: str) -> dict[str, Any]:
    """读取必需 JSON Object。"""
    nested = value.get(key)
    if not isinstance(nested, dict):
        raise MigrationError(f"{key} must be an object")
    return nested


def require_text(value: dict[str, Any], key: str) -> str:
    """读取必需非空文本。"""
    nested = value.get(key)
    if not isinstance(nested, str) or not nested.strip():
        raise MigrationError(f"{key} must not be blank")
    return nested


def require_uuid_v7(value: dict[str, Any], key: str) -> str:
    """读取并校验目标 AgentArk UUIDv7，防止 Dry Run 延迟到远端才失败。"""
    text = require_text(value, key)
    if UUID_V7.fullmatch(text) is None:
        raise MigrationError(f"{key} must be a canonical UUIDv7")
    return text


def resolve_value(value: Any, mappings: dict[str, dict[str, Any]]) -> Any:
    """递归解析 Body 中的来源映射引用。"""
    if isinstance(value, dict):
        if set(value) == {"$mapping", "field"}:
            source = value["$mapping"]
            field = value["field"]
            try:
                return mappings[source][field]
            except KeyError as exception:
                raise MigrationError(f"required mapping is missing: {source}.{field}") from exception
        return {key: resolve_value(nested, mappings) for key, nested in value.items()}
    if isinstance(value, list):
        return [resolve_value(nested, mappings) for nested in value]
    return value


def resolve_path(path: str, mappings: dict[str, dict[str, Any]]) -> str:
    """解析 URL 路径中的来源映射引用并执行 Segment 编码。"""

    def replace(match: re.Match[str]) -> str:
        source = match.group(1)
        field = match.group(2)
        try:
            value = mappings[source][field]
        except KeyError as exception:
            raise MigrationError(f"required path mapping is missing: {source}.{field}") from exception
        return urllib.parse.quote(str(value), safe="")

    return PATH_MAPPING.sub(replace, path)


def extract_pointer(value: Any, pointer: Any) -> Any:
    """从响应提取 Checkpoint 字段；非字符串值作为常量写入。"""
    if not isinstance(pointer, str) or not pointer.startswith("/"):
        return pointer
    current = value
    for part in pointer[1:].split("/"):
        key = part.replace("~1", "/").replace("~0", "~")
        if isinstance(current, dict) and key in current:
            current = current[key]
        else:
            raise MigrationError(f"response mapping pointer is missing: {pointer}")
    return current


def execute_plan(
    plan: dict[str, Any], config: dict[str, Any], checkpoint_path: Path, report_path: Path
) -> int:
    """按依赖顺序执行计划，并在每次外部调用前后原子更新 Checkpoint。"""
    checkpoint = (
        load_json(checkpoint_path)
        if checkpoint_path.exists()
        else {
            "schemaVersion": SCHEMA_VERSION,
            "sourceCommit": SOURCE_COMMIT,
            "planHash": plan["planHash"],
            "operations": {},
            "mappings": copy.deepcopy(config.get("preexistingMappings", {})),
            "audit": [],
        }
    )
    if checkpoint.get("sourceCommit") != SOURCE_COMMIT or checkpoint.get("planHash") != plan["planHash"]:
        raise MigrationError("checkpoint sourceCommit or planHash does not match")
    operations_state = checkpoint["operations"]
    mappings = checkpoint["mappings"]
    clients = build_clients(config)
    failures: list[dict[str, str]] = []
    completed: set[str] = {
        operation_id_value
        for operation_id_value, state in operations_state.items()
        if state.get("status") == "SUCCEEDED"
    }
    for operation in plan["operations"]:
        identifier = operation["operationId"]
        existing = operations_state.get(identifier)
        if existing and existing.get("status") == "SUCCEEDED":
            if existing.get("sourceHash") != operation["sourceHash"]:
                raise MigrationError(f"source changed after successful operation: {identifier}")
            completed.add(identifier)
            continue
        missing = [dependency for dependency in operation["dependsOn"] if dependency not in completed]
        if missing:
            failures.append(
                {"operationId": identifier, "sourceKey": operation["sourceKey"], "code": "DEPENDENCY_FAILED"}
            )
            continue
        try:
            if existing and existing.get("status") == "IN_FLIGHT":
                reconciled = reconcile(operation, clients, mappings)
                if reconciled is not None:
                    update_mapping(operation, reconciled, mappings)
                    finish_operation(checkpoint, operation, "RECONCILED")
                    atomic_write_json(checkpoint_path, checkpoint)
                    completed.add(identifier)
                    continue
                raise MigrationError("in-flight operation requires manual reconciliation")
            operations_state[identifier] = {
                "status": "IN_FLIGHT",
                "sourceHash": operation["sourceHash"],
                "attempts": int((existing or {}).get("attempts", 0)) + 1,
                "startedAt": now_utc(),
            }
            atomic_write_json(checkpoint_path, checkpoint)
            if operation["method"] == "RECORD":
                mappings.setdefault(operation["sourceKey"], {}).update(operation["mapping"])
                response = operation["mapping"]
            else:
                client = clients.get(operation["plane"])
                if client is None:
                    raise MigrationError(f"HTTP client is not configured for plane {operation['plane']}")
                result = client.request(
                    operation["method"],
                    resolve_path(operation["path"], mappings),
                    resolve_value(operation["body"], mappings),
                    operation.get("headers", {}),
                )
                if result.status not in operation["expectedStatuses"]:
                    raise MigrationError(
                        f"unexpected HTTP status for {operation['action']}: {result.status}"
                    )
                response = result.body
                update_mapping(operation, response, mappings)
            finish_operation(checkpoint, operation, "SUCCEEDED")
            checkpoint["audit"].append(
                {
                    "operationId": identifier,
                    "sourceKey": operation["sourceKey"],
                    "action": operation["action"],
                    "result": "SUCCEEDED",
                    "occurredAt": now_utc(),
                }
            )
            atomic_write_json(checkpoint_path, checkpoint)
            completed.add(identifier)
        except MigrationError as exception:
            operations_state[identifier]["status"] = "FAILED"
            operations_state[identifier]["errorCode"] = type(exception).__name__
            operations_state[identifier]["completedAt"] = now_utc()
            atomic_write_json(checkpoint_path, checkpoint)
            failures.append(
                {"operationId": identifier, "sourceKey": operation["sourceKey"], "code": str(exception)}
            )
    report = validation_report(plan, checkpoint, failures)
    atomic_write_json(report_path, report)
    return 0 if not failures else 1


def build_clients(config: dict[str, Any]) -> dict[str, JsonHttpClient]:
    """从非敏感基地址与令牌文件构造三个 Plane Client。"""
    clients: dict[str, JsonHttpClient] = {}
    endpoints = require_object(config, "endpoints")
    token_files = config.get("tokenFiles", {})
    if not isinstance(token_files, dict):
        raise MigrationError("tokenFiles must be an object")
    for plane in ("control", "runtime", "scheduler"):
        base_url = endpoints.get(plane)
        if base_url is None:
            continue
        token_path = token_files.get(plane)
        clients[plane] = JsonHttpClient(
            str(base_url), Path(token_path).expanduser() if token_path else None
        )
    return clients


def reconcile(
    operation: dict[str, Any], clients: dict[str, JsonHttpClient], mappings: dict[str, dict[str, Any]]
) -> Any | None:
    """Crash Resume 时按稳定 Key 读取目标资源，避免重复创建。"""
    rule = operation.get("reconcile")
    if not isinstance(rule, dict):
        return None
    client = clients.get(rule.get("plane"))
    if client is None:
        return None
    result = client.request("GET", resolve_path(rule["path"], mappings))
    if result.status != 200:
        return None
    collection = extract_pointer(result.body, rule["collectionPointer"])
    if not isinstance(collection, list):
        return None
    matches = [item for item in collection if isinstance(item, dict) and item.get(rule["matchField"]) == rule["matchValue"]]
    if len(matches) != 1:
        return None
    return matches[0]


def update_mapping(
    operation: dict[str, Any], response: Any, mappings: dict[str, dict[str, Any]]
) -> None:
    """把目标 ID、Revision、Snapshot Hash 等非秘密结果写入来源映射。"""
    target = mappings.setdefault(operation["sourceKey"], {})
    extracted: dict[str, Any] = {}
    for field, pointer in operation.get("mappingExtract", {}).items():
        extracted[field] = extract_pointer(response, pointer)
    if operation["action"].startswith("PUBLISH_AGENT_V"):
        version = str(extracted["sourceVersion"])
        target.setdefault("revisions", {})[version] = copy.deepcopy(extracted)
    target.update(extracted)


def finish_operation(checkpoint: dict[str, Any], operation: dict[str, Any], result: str) -> None:
    """写入不含响应正文的操作终态。"""
    state = checkpoint["operations"][operation["operationId"]]
    state["status"] = "SUCCEEDED"
    state["result"] = result
    state["completedAt"] = now_utc()


def validation_report(
    plan: dict[str, Any], checkpoint: dict[str, Any] | None, failures: list[dict[str, str]]
) -> dict[str, Any]:
    """生成 Count、Hash、Reference、Snapshot、状态与每资源错误报告。"""
    resource_counts = Counter(
        operation["sourceKey"].split(":", 1)[0] for operation in plan["operations"]
    )
    succeeded = 0
    if checkpoint is not None:
        succeeded = sum(
            1 for state in checkpoint.get("operations", {}).values() if state.get("status") == "SUCCEEDED"
        )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "sourceCommit": SOURCE_COMMIT,
        "planHash": plan["planHash"],
        "generatedAt": now_utc(),
        "dryRun": checkpoint is None,
        "counts": {
            "sourceResources": plan["sourceCounts"]["total"],
            "sourceByResourceType": plan["sourceCounts"]["byResourceType"],
            "operations": len(plan["operations"]),
            "succeeded": succeeded,
            "failed": len(failures),
            "operationsByResourceType": dict(sorted(resource_counts.items())),
            "objectMigrations": len(plan["objectMigrations"]),
        },
        "hashes": {
            "plan": plan["planHash"],
            "sourceBackup": plan["source"]["readOnlyBackup"]["checksum"],
        },
        "referenceValidation": "PASSED",
        "timeMapping": "UTC_RFC3339_MICROS",
        "statusMapping": "PASSED",
        "secretMigration": "REFERENCE_ONLY",
        "failures": failures,
        "warnings": plan["warnings"],
    }


def parse_arguments() -> argparse.Namespace:
    """解析 Dry Run、Apply 与 Validate CLI 参数。"""
    parser = argparse.ArgumentParser(description="Aistio 到 AgentArk 的安全迁移工具")
    parser.add_argument("mode", choices=("validate", "dry-run", "apply"))
    parser.add_argument("--export", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--checkpoint", type=Path, default=Path(".agentark/migration/aistio-checkpoint.json"))
    parser.add_argument("--report", type=Path, default=Path(".agentark/migration/aistio-report.json"))
    parser.add_argument("--plan", type=Path, default=Path(".agentark/migration/aistio-plan.json"))
    return parser.parse_args()


def main() -> int:
    """执行输入验证、计划生成、Dry Run 或可恢复 Apply。"""
    arguments = parse_arguments()
    try:
        bundle = validate_export(load_json_or_ndjson(arguments.export))
        config = load_json(arguments.config)
        plan = Planner(config).build(bundle)
        atomic_write_json(arguments.plan, plan)
        if arguments.mode == "validate":
            return 0
        if arguments.mode == "dry-run":
            atomic_write_json(arguments.report, validation_report(plan, None, []))
            return 0
        return execute_plan(plan, config, arguments.checkpoint, arguments.report)
    except (MigrationError, OSError, ValueError) as exception:
        print(f"migration failed: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
