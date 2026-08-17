#!/usr/bin/env python3
"""验证 Shadow Compare 阈值和 Java-only 兼容代理。"""

from __future__ import annotations

import copy
import json
import sys
import tempfile
import threading
import unittest
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
MIGRATION_ROOT = ROOT / "tools" / "migration"
sys.path.insert(0, str(MIGRATION_ROOT))

from aistio_common import MigrationError, load_json  # noqa: E402
from aistio_shadow import (  # noqa: E402
    CompatibilityProxy,
    compare,
    comparison_record,
    summarize,
)
from aistio_common import HttpResult  # noqa: E402


class JsonServer:
    """返回固定 JSON 的 Loopback Server，并记录访问次数。"""

    def __init__(self, body: dict[str, Any]):
        self.body = body
        self.calls = 0
        parent = self

        class Handler(BaseHTTPRequestHandler):
            """返回外层固定 Body。"""

            def do_GET(self) -> None:
                """处理只读比较请求。"""
                parent.calls += 1
                encoded = json.dumps(parent.body).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)

            def log_message(self, format_string: str, *arguments: Any) -> None:
                """禁止测试访问日志。"""
                return

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self) -> "JsonServer":
        """启动 Server。"""
        self.thread.start()
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        """停止 Server。"""
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    @property
    def base_url(self) -> str:
        """返回 Loopback 基地址。"""
        return f"http://127.0.0.1:{self.server.server_address[1]}"


class AistioShadowTest(unittest.TestCase):
    """覆盖 Shadow Gate、字段差异和 Java-only 默认模式。"""

    def setUp(self) -> None:
        """读取测试 Cutover Flags。"""
        self.config = load_json(MIGRATION_ROOT / "fixtures" / "aistio-cutover-test.json")

    def test_compare_passes_approved_threshold_after_ignored_fields(self) -> None:
        """证明时间字段不同不会掩盖业务字段一致，批准阈值可执行。"""
        go_body = {"id": "legacy-1", "name": "Agent", "createdAt": "old", "updatedAt": "old"}
        java_body = {
            "id": "target-1",
            "organizationId": "target-org",
            "name": "Agent",
            "createdAt": "new",
            "updatedAt": "new",
        }
        with JsonServer(go_body) as go_server, JsonServer(java_body) as java_server, tempfile.TemporaryDirectory() as directory:
            config = copy.deepcopy(self.config)
            config["endpoints"]["go"] = go_server.base_url
            config["endpoints"]["java"] = java_server.base_url
            config_path = Path(directory) / "config.json"
            cases_path = Path(directory) / "cases.json"
            report_path = Path(directory) / "report.json"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            cases_path.write_text(
                json.dumps({"cases": [{
                    "caseId": "agent-1",
                    "cohort": "C3_AGENT",
                    "tenant": config["projectId"],
                    "goPath": "/api/agents/agent-1",
                    "javaPath": "/api/v1/projects/p/agents/target-1",
                    "ignorePointers": ["/createdAt", "/updatedAt"],
                    "normalization": {
                        "fieldMappings": [
                            {"canonical": "name", "go": "/name", "java": "/name"},
                        ],
                    },
                    "securityCritical": True,
                }]}),
                encoding="utf-8",
            )

            result = compare(config, cases_path, report_path)

            self.assertEqual(0, result)
            report = load_json(report_path)
            self.assertTrue(report["summary"]["approved"])
            self.assertEqual([], report["records"][0]["differencePointers"])

    def test_security_mismatch_blocks_gate(self) -> None:
        """证明任何安全关键 Case 差异都会阻断，即使整体 Match Rate 阈值宽松。"""
        records = [{
            "match": False,
            "securityCritical": True,
            "goStatus": 403,
            "javaStatus": 200,
            "goLatencyMs": 1,
            "javaLatencyMs": 1,
        }]

        summary = summarize(records, {
            "minMatchRate": 0.0,
            "maxErrorRate": 1.0,
            "maxP95LatencyRatio": 10.0,
        })

        self.assertFalse(summary["approved"])
        self.assertEqual(1, summary["securityMismatches"])

    def test_secret_field_or_unsafe_ignore_blocks_security_gate(self) -> None:
        """证明响应中的 Token 字段或忽略授权字段都会阻断安全 Shadow Case。"""
        case = {
            "caseId": "security-secret",
            "cohort": "C1_AUTH",
            "tenant": "tenant-1",
            "securityCritical": True,
        }
        record = comparison_record(
            case,
            HttpResult(200, {"id": "a", "accessToken": "legacy"}, 1),
            HttpResult(200, {"id": "a"}, 1),
            [],
        )
        self.assertFalse(record["secretRedactionMatch"])
        self.assertFalse(summarize([record], self.config["thresholds"])["approved"])

        with self.assertRaises(MigrationError):
            comparison_record(
                case,
                HttpResult(200, {"role": "admin"}, 1),
                HttpResult(200, {"role": "admin"}, 1),
                ["/role"],
            )

    def test_java_only_proxy_never_calls_go(self) -> None:
        """证明最终默认模式只读 Java，Go Fallback 已关闭。"""
        with JsonServer({"id": "go", "name": "Agent"}) as go_server, JsonServer({"id": "target-1", "name": "Agent"}) as java_server, tempfile.TemporaryDirectory() as directory:
            config = copy.deepcopy(self.config)
            config["mode"] = "JAVA_ONLY"
            config["endpoints"]["go"] = go_server.base_url
            config["endpoints"]["java"] = java_server.base_url
            checkpoint = {
                "mappings": {"agent:agent-1": {"id": "target-1"}}
            }
            proxy = CompatibilityProxy(config, checkpoint, Path(directory) / "report.json")

            result, record = proxy.handle_get(
                "/api/agents/agent-1", config["projectId"], "catalog-read"
            )

            self.assertEqual(200, result.status)
            self.assertIsNone(record)
            self.assertEqual(0, go_server.calls)
            self.assertEqual(1, java_server.calls)

    def test_go_fallback_requires_future_expiry_within_twenty_four_hours(self) -> None:
        """证明 Go Fallback 不能无期限保留或使用过期窗口。"""
        config = copy.deepcopy(self.config)
        config["mode"] = "GO_FALLBACK"
        config["goFallbackExpiresAt"] = "2026-08-16T00:00:00Z"

        with self.assertRaises(MigrationError):
            CompatibilityProxy(config, {"mappings": {}}, Path("unused.json"))

    def test_java_only_missing_mapping_and_expired_fallback_fail_closed(self) -> None:
        """证明最终模式缺少映射不回 Go，Fallback 到期后任何 Cohort 都失败关闭。"""
        with JsonServer({"id": "go", "name": "Agent"}) as go_server, JsonServer({"id": "java", "name": "Agent"}) as java_server:
            java_only = copy.deepcopy(self.config)
            java_only["mode"] = "JAVA_ONLY"
            java_only["endpoints"]["go"] = go_server.base_url
            java_only["endpoints"]["java"] = java_server.base_url
            proxy = CompatibilityProxy(java_only, {"mappings": {}}, Path("unused.json"))
            with self.assertRaises(MigrationError):
                proxy.handle_get("/api/agents/agent-1", "unknown", "unknown")
            self.assertEqual(0, go_server.calls)

            fallback = copy.deepcopy(java_only)
            fallback["mode"] = "GO_FALLBACK"
            fallback["goFallbackExpiresAt"] = (
                datetime.now(timezone.utc) + timedelta(hours=1)
            ).isoformat().replace("+00:00", "Z")
            proxy = CompatibilityProxy(fallback, {"mappings": {}}, Path("unused.json"))
            proxy.fallback_expires_at = datetime.now(timezone.utc) - timedelta(seconds=1)
            with self.assertRaises(MigrationError):
                proxy.handle_get("/api/agents/agent-1", "unknown", "unknown")

    def test_proxy_never_returns_forbidden_secret_field(self) -> None:
        """证明即使 Cohort 尚未切 Java，临时代理也不会把旧 Go Token 字段返回调用方。"""
        with JsonServer({"id": "go", "accessToken": "legacy"}) as go_server, JsonServer({"id": "java"}) as java_server:
            config = copy.deepcopy(self.config)
            config["mode"] = "SHADOW"
            config["endpoints"]["go"] = go_server.base_url
            config["endpoints"]["java"] = java_server.base_url
            proxy = CompatibilityProxy(config, {"mappings": {}}, Path("unused.json"))

            with self.assertRaises(MigrationError):
                proxy.handle_get("/api/agents/agent-1", "unknown", "unknown")
            self.assertEqual(1, go_server.calls)
            self.assertEqual(0, java_server.calls)


if __name__ == "__main__":
    unittest.main()
