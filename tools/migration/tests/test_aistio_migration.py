#!/usr/bin/env python3
"""验证 Aistio 迁移计划、Secret 拒绝、Apply、Checkpoint 和 Resume。"""

from __future__ import annotations

import copy
import json
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
MIGRATION_ROOT = ROOT / "tools" / "migration"
sys.path.insert(0, str(MIGRATION_ROOT))

from aistio_common import MigrationError, load_json  # noqa: E402
from aistio_migrate import (  # noqa: E402
    Planner,
    execute_plan,
    validate_export,
)


class FakeAgentArkServer:
    """为迁移 Apply 提供确定性 Control API 响应。"""

    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []
        self.requests: list[tuple[str, str, Any]] = []
        parent = self

        class Handler(BaseHTTPRequestHandler):
            """记录方法和路径并返回最小强类型资源。"""

            def do_POST(self) -> None:
                """处理创建与发布请求。"""
                parent.calls.append(("POST", self.path))
                body = self._read_body()
                parent.requests.append(("POST", self.path, body))
                if self.path.endswith("/environments"):
                    self._json(201, {"id": "019d0000-0000-7000-8000-000000000101", "key": "legacy-prod"})
                elif self.path.endswith("/catalog/prompt"):
                    self._json(201, {"id": "019d0000-0000-7000-8000-000000000102", "key": "prompt-legacy-agent"})
                elif self.path.endswith("/versions"):
                    version = sum(1 for method, path in parent.calls if method == "POST" and path.endswith("/versions"))
                    self._json(201, {
                        "id": f"019d0000-0000-7000-8000-{version:012d}",
                        "contentHash": "sha256:" + f"{version:064x}",
                    })
                elif self.path.endswith("/agents"):
                    self._json(201, {"id": "019d0000-0000-7000-8000-000000000103", "key": "legacy-agent"})
                elif self.path.endswith("/publish"):
                    version = sum(1 for method, path in parent.calls if method == "POST" and path.endswith("/publish"))
                    self._json(201, {
                        "id": f"019d0000-0000-7000-8001-{version:012d}",
                        "snapshotId": f"019d0000-0000-7000-8002-{version:012d}",
                        "contentHash": "sha256:" + f"{version + 10:064x}",
                    })
                elif self.path.endswith("/deployments"):
                    self._json(201, {"id": "019d0000-0000-7000-8000-000000000104", "version": 0})
                elif self.path == "/api/v1/scheduler/triggers":
                    self._json(201, {
                        "id": "019d0000-0000-7000-8000-000000000105",
                        "key": body["key"],
                    })
                else:
                    self._json(404, {"code": "NOT_FOUND"})

            def do_PUT(self) -> None:
                """处理第二个旧版本的 Draft 更新。"""
                parent.calls.append(("PUT", self.path))
                body = self._read_body()
                parent.requests.append(("PUT", self.path, body))
                self._json(200, {"version": 1})

            def do_GET(self) -> None:
                """提供 Crash Reconcile 读取集合。"""
                parent.calls.append(("GET", self.path))
                self._json(200, {"items": []})

            def _read_body(self) -> Any:
                """读取 JSON 请求正文，供契约断言使用。"""
                length = int(self.headers.get("Content-Length", "0"))
                if not length:
                    return None
                return json.loads(self.rfile.read(length))

            def _json(self, status: int, body: dict[str, Any]) -> None:
                """返回 JSON 响应。"""
                encoded = json.dumps(body).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)

            def log_message(self, format_string: str, *arguments: Any) -> None:
                """禁止测试输出路径噪声。"""
                return

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self) -> "FakeAgentArkServer":
        """启动测试 Server。"""
        self.thread.start()
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        """停止并释放测试 Server。"""
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    @property
    def base_url(self) -> str:
        """返回 Loopback 基地址。"""
        return f"http://127.0.0.1:{self.server.server_address[1]}"


class AistioMigrationTest(unittest.TestCase):
    """覆盖 Phase 21 数据迁移强制语义。"""

    def setUp(self) -> None:
        """读取每个测试独享的 Fixture 和配置副本。"""
        self.bundle = load_json(MIGRATION_ROOT / "fixtures" / "aistio-export-v1.json")
        self.config = load_json(MIGRATION_ROOT / "fixtures" / "aistio-cutover-test.json")

    def test_plan_contains_versions_owner_pin_and_object_manifest(self) -> None:
        """证明计划冻结两个 Revision、活动 Session Owner 和 Object Store 搬运清单。"""
        bundle = validate_export(copy.deepcopy(self.bundle))
        plan = Planner(self.config).build(bundle)

        actions = [operation["action"] for operation in plan["operations"]]
        self.assertIn("PUBLISH_AGENT_V1", actions)
        self.assertIn("PUBLISH_AGENT_V2", actions)
        self.assertIn("PIN_SESSION_OWNER", actions)
        self.assertIn("DEFER_TEAM", actions)
        self.assertEqual(1, len(plan["objectMigrations"]))
        serialized = json.dumps(plan, ensure_ascii=False)
        self.assertNotIn("passwordHash", serialized)
        self.assertNotIn("ciphertext", serialized)
        self.assertTrue(plan["planHash"].startswith("sha256:"))

    def test_rejects_secret_fields_and_changed_hash(self) -> None:
        """证明密文/摘要字段和伪造 Canonical Hash 在任何计划生成前被拒绝。"""
        forbidden = copy.deepcopy(self.bundle)
        forbidden["resources"][0]["payload"]["passwordHash"] = "not-exportable"
        with self.assertRaises(MigrationError):
            validate_export(forbidden)

        forbidden_api_hash = copy.deepcopy(self.bundle)
        forbidden_api_hash["resources"][1]["payload"]["apiKeyHash"] = "not-exportable"
        with self.assertRaises(MigrationError):
            validate_export(forbidden_api_hash)

        missing_owner_reference = copy.deepcopy(self.bundle)
        missing_owner_reference["resources"][3]["references"] = [
            "agent:agent-1",
            "environment:env-1",
        ]
        with self.assertRaises(MigrationError):
            validate_export(missing_owner_reference)

        changed_hash = copy.deepcopy(self.bundle)
        changed_hash["resources"][1]["canonicalHash"] = "sha256:" + "0" * 64
        with self.assertRaises(MigrationError):
            validate_export(changed_hash)

    def test_requires_explicit_principal_and_secret_reference_mappings(self) -> None:
        """证明外部身份和旧 Vault Credential 不能被静默伪造成目标身份或 Secret。"""
        missing_principal = copy.deepcopy(self.config)
        missing_principal["principalMappings"] = {}
        with self.assertRaises(MigrationError):
            Planner(missing_principal).build(validate_export(copy.deepcopy(self.bundle)))

        with_secret = copy.deepcopy(self.bundle)
        with_secret["resources"].append({
            "type": "secret_metadata",
            "sourceId": "credential-1",
            "ownerId": "user-1",
            "status": "ENABLED",
            "updatedAt": "2026-08-17T00:00:00Z",
            "payload": {
                "key": "legacy-credential",
                "name": "旧凭据引用",
                "migrationState": "REQUIRES_EXTERNAL_SECRET_REBINDING",
            },
            "references": ["user_identity:user-1"],
        })
        with self.assertRaises(MigrationError):
            Planner(copy.deepcopy(self.config)).build(validate_export(with_secret))

    def test_rejects_normalized_target_key_collision(self) -> None:
        """证明两个来源 Key 规范化为同一目标 Key 时必须人工拆分。"""
        collided = copy.deepcopy(self.bundle)
        duplicate = copy.deepcopy(collided["resources"][1])
        duplicate["sourceId"] = "env-2"
        duplicate["payload"]["key"] = "legacy_prod"
        duplicate["canonicalHash"] = None
        collided["resources"].append(duplicate)
        with self.assertRaises(MigrationError):
            Planner(copy.deepcopy(self.config)).build(validate_export(collided))

    def test_scheduler_trigger_matches_public_contract_shape(self) -> None:
        """证明 Cron 拆分请求包含 Scheduler Public Contract 的全部必填字段。"""
        with FakeAgentArkServer() as server, tempfile.TemporaryDirectory() as directory:
            bundle = copy.deepcopy(self.bundle)
            deployment = next(
                item for item in bundle["resources"] if item["type"] == "deployment"
            )
            deployment["payload"]["triggerType"] = "CRON"
            deployment["payload"]["cronExpression"] = "0 0 * * * *"
            deployment.pop("canonicalHash", None)
            config = copy.deepcopy(self.config)
            config["endpoints"]["control"] = server.base_url
            config["endpoints"]["scheduler"] = server.base_url
            plan = Planner(config).build(validate_export(bundle))

            result = execute_plan(
                plan,
                config,
                Path(directory) / "checkpoint.json",
                Path(directory) / "report.json",
            )

            self.assertEqual(0, result)
            trigger = next(
                body for method, path, body in server.requests
                if method == "POST" and path == "/api/v1/scheduler/triggers"
            )
            self.assertEqual({
                "organizationId",
                "projectId",
                "key",
                "type",
                "cronExpression",
                "zoneId",
                "config",
                "secretRef",
                "targetContract",
                "targetJobType",
            }, set(trigger))
            self.assertIsInstance(trigger["config"]["deploymentId"], str)
            self.assertEqual("RUNTIME_TURN", trigger["targetJobType"])

    def test_apply_is_resumable_and_keeps_all_revision_mappings(self) -> None:
        """证明 Apply 后 Resume 不重复调用，并保留来源版本到目标 Snapshot Hash 映射。"""
        with FakeAgentArkServer() as server, tempfile.TemporaryDirectory() as directory:
            config = copy.deepcopy(self.config)
            config["endpoints"]["control"] = server.base_url
            config["endpoints"]["scheduler"] = server.base_url
            plan = Planner(config).build(validate_export(copy.deepcopy(self.bundle)))
            checkpoint = Path(directory) / "checkpoint.json"
            report = Path(directory) / "report.json"

            first = execute_plan(plan, config, checkpoint, report)
            first_call_count = len(server.calls)
            second = execute_plan(plan, config, checkpoint, report)

            self.assertEqual(0, first)
            self.assertEqual(0, second)
            self.assertEqual(first_call_count, len(server.calls))
            saved = load_json(checkpoint)
            agent_mapping = saved["mappings"]["agent:agent-1"]
            self.assertEqual({"1", "2"}, set(agent_mapping["revisions"]))
            self.assertEqual(
                "GO_UNTIL_TERMINAL",
                saved["mappings"]["session:session-active-1"]["owner"],
            )
            self.assertEqual(0, load_json(report)["counts"]["failed"])


if __name__ == "__main__":
    unittest.main()
