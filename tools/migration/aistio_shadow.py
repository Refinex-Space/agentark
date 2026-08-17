#!/usr/bin/env python3
"""Aistio 只读兼容代理、Shadow Compare、Java Primary 与有时限 Go Fallback。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from aistio_common import (
    HttpResult,
    JsonHttpClient,
    MigrationError,
    atomic_write_json,
    canonical_hash,
    json_pointer_differences,
    load_json,
    reject_secret_fields,
    remove_json_pointers,
    utc_instant,
)


MODES = {"SHADOW", "JAVA_PRIMARY", "GO_FALLBACK", "JAVA_ONLY"}
SECURITY_SAFE_IGNORED_POINTERS = {
    "/createdAt",
    "/updatedAt",
    "/requestId",
    "/traceId",
    "/nextCursor",
    "/metadata/continue",
}


def percentile(values: list[int], percentile_value: float) -> int:
    """返回整数毫秒样本的最近秩百分位。"""
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int((len(ordered) - 1) * percentile_value)))
    return ordered[index]


class ShadowComparator:
    """执行有界双读并只记录 Hash、状态、延迟和字段路径差异。"""

    def __init__(self, config: dict[str, Any]):
        reject_secret_fields(config, "$.config")
        self.config = config
        endpoints = require_object(config, "endpoints")
        token_files = config.get("tokenFiles", {})
        if not isinstance(token_files, dict):
            raise MigrationError("tokenFiles must be an object")
        self.go_client = JsonHttpClient(
            require_text(endpoints, "go"),
            Path(token_files["go"]) if token_files.get("go") else None,
        )
        self.java_client = JsonHttpClient(
            require_text(endpoints, "java"),
            Path(token_files["java"]) if token_files.get("java") else None,
        )

    def compare_case(self, case: dict[str, Any]) -> dict[str, Any]:
        """比较单个 Go/Java GET Case。"""
        case_id = require_text(case, "caseId")
        go_path = require_text(case, "goPath")
        java_path = require_text(case, "javaPath")
        ignored = case.get("ignorePointers", [])
        if not isinstance(ignored, list) or any(not isinstance(pointer, str) for pointer in ignored):
            raise MigrationError(f"ignorePointers is invalid: {case_id}")
        go_result = self.go_client.request("GET", go_path)
        java_result = self.java_client.request("GET", java_path)
        return comparison_record(case, go_result, java_result, ignored)


def comparison_record(
    case: dict[str, Any],
    go_result: HttpResult,
    java_result: HttpResult,
    ignored: list[str],
) -> dict[str, Any]:
    """构造不含响应正文的 Shadow 记录。"""
    if bool(case.get("securityCritical", False)):
        unsafe_ignored = set(ignored) - SECURITY_SAFE_IGNORED_POINTERS
        if unsafe_ignored:
            raise MigrationError("security-critical case ignores a protected field")
    secret_redaction_match = is_secret_safe(go_result.body) and is_secret_safe(
        java_result.body
    )
    normalized_go = normalize_body(go_result.body, case, "go", ignored)
    normalized_java = normalize_body(java_result.body, case, "java", ignored)
    differences = json_pointer_differences(normalized_go, normalized_java)
    status_match = go_result.status == java_result.status
    body_match = canonical_hash(normalized_go) == canonical_hash(normalized_java)
    return {
        "caseId": case["caseId"],
        "cohort": case.get("cohort", "UNSPECIFIED"),
        "tenant": case.get("tenant", ""),
        "securityCritical": bool(case.get("securityCritical", False)),
        "goStatus": go_result.status,
        "javaStatus": java_result.status,
        "goHash": canonical_hash(normalized_go),
        "javaHash": canonical_hash(normalized_java),
        "statusMatch": status_match,
        "bodyMatch": body_match,
        "secretRedactionMatch": secret_redaction_match,
        "match": status_match and body_match and secret_redaction_match,
        "differencePointers": differences,
        "goLatencyMs": go_result.elapsed_ms,
        "javaLatencyMs": java_result.elapsed_ms,
        "comparedAt": datetime.now(timezone.utc)
        .isoformat(timespec="microseconds")
        .replace("+00:00", "Z"),
    }


def summarize(records: list[dict[str, Any]], thresholds: dict[str, Any]) -> dict[str, Any]:
    """计算 Match/Error/Latency Gate，并对安全 Case 强制 100% 精确匹配。"""
    if not records:
        raise MigrationError("shadow report has no comparison records")
    total = len(records)
    matches = sum(1 for record in records if record.get("match") is True)
    errors = sum(
        1
        for record in records
        if int(record.get("goStatus", 0)) >= 500 or int(record.get("javaStatus", 0)) >= 500
    )
    security_mismatches = sum(
        1
        for record in records
        if record.get("securityCritical") is True and record.get("match") is not True
    )
    secret_redaction_mismatches = sum(
        1 for record in records if record.get("secretRedactionMatch", True) is not True
    )
    match_rate = matches / total
    error_rate = errors / total
    go_p95 = percentile([int(record["goLatencyMs"]) for record in records], 0.95)
    java_p95 = percentile([int(record["javaLatencyMs"]) for record in records], 0.95)
    latency_ratio = java_p95 / max(go_p95, 1)
    min_match = float(thresholds.get("minMatchRate", 0.999))
    max_error = float(thresholds.get("maxErrorRate", 0.001))
    max_latency = float(thresholds.get("maxP95LatencyRatio", 1.20))
    approved = (
        match_rate >= min_match
        and error_rate <= max_error
        and latency_ratio <= max_latency
        and security_mismatches == 0
        and secret_redaction_mismatches == 0
    )
    return {
        "total": total,
        "matches": matches,
        "matchRate": round(match_rate, 6),
        "errorRate": round(error_rate, 6),
        "goP95LatencyMs": go_p95,
        "javaP95LatencyMs": java_p95,
        "p95LatencyRatio": round(latency_ratio, 6),
        "securityMismatches": security_mismatches,
        "secretRedactionMismatches": secret_redaction_mismatches,
        "thresholds": {
            "minMatchRate": min_match,
            "maxErrorRate": max_error,
            "maxP95LatencyRatio": max_latency,
            "securityMatchRate": 1.0,
            "secretRedactionMatchRate": 1.0,
        },
        "approved": approved,
    }


class CompatibilityProxy:
    """只读临时兼容代理；不承担默认部署，也不代理任何写请求。"""

    def __init__(self, config: dict[str, Any], checkpoint: dict[str, Any], report_path: Path):
        mode = config.get("mode")
        if mode not in MODES:
            raise MigrationError("unsupported cutover mode")
        self.mode = mode
        self.config = config
        self.checkpoint = checkpoint
        self.report_path = report_path
        self.comparator = ShadowComparator(config)
        self.routes = compile_routes(config.get("routes"))
        self.fallback_expires_at = parse_fallback_expiry(config)

    def select(self, path: str, tenant: str, capability: str) -> tuple[dict[str, Any], str]:
        """按 Route、Tenant、Capability 和来源映射解析 Java 目标路径。"""
        route = next((candidate for candidate in self.routes if candidate["pattern"].fullmatch(path)), None)
        if route is None:
            raise MigrationError("legacy route is not allowlisted")
        allow_tenants = set(self.config.get("tenantAllowlist", []))
        allow_capabilities = set(self.config.get("capabilityAllowlist", []))
        if self.mode != "JAVA_ONLY" and allow_tenants and tenant not in allow_tenants:
            return route, "GO"
        if self.mode != "JAVA_ONLY" and allow_capabilities and capability not in allow_capabilities:
            return route, "GO"
        match = route["pattern"].fullmatch(path)
        assert match is not None
        source_id = match.groupdict().get("sourceId")
        mapping_type = route.get("mappingType")
        target_id = ""
        if mapping_type and source_id:
            mapping_key = f"{mapping_type}:{source_id}"
            target_id = str(self.checkpoint.get("mappings", {}).get(mapping_key, {}).get("id", ""))
            if not target_id:
                if self.mode == "JAVA_ONLY":
                    raise MigrationError("Java-only route has no target mapping")
                return route, "GO"
        java_path = route["javaTemplate"].format(
            projectId=self.config.get("projectId", ""), targetId=target_id, sourceId=source_id or ""
        )
        route = dict(route)
        route["javaPath"] = java_path
        return route, "SELECTED"

    def handle_get(self, path: str, tenant: str, capability: str) -> tuple[HttpResult, dict[str, Any] | None]:
        """执行 Shadow、Java Primary、到期 Fallback 或 Java Only 读路径。"""
        route, selection = self.select(path, tenant, capability)
        go_path = path
        if selection == "GO" and self.mode == "GO_FALLBACK" and datetime.now(
            timezone.utc
        ) >= self.fallback_expires_at:
            raise MigrationError("Go fallback window has expired")
        if selection == "GO" and self.mode != "JAVA_ONLY":
            return safe_proxy_result(
                self.comparator.go_client.request("GET", go_path), None
            )
        java_path = route["javaPath"]
        case = {
            "caseId": f"proxy-{canonical_hash({'go': go_path, 'java': java_path})[7:23]}",
            "cohort": route["cohort"],
            "tenant": tenant,
            "securityCritical": bool(route.get("securityCritical", False)),
            "normalization": route.get("normalization"),
        }
        ignored = route.get("ignorePointers", [])
        if self.mode == "SHADOW":
            go_result = self.comparator.go_client.request("GET", go_path)
            java_result = self.comparator.java_client.request("GET", java_path)
            record = comparison_record(case, go_result, java_result, ignored)
            return safe_proxy_result(go_result, record)
        if self.mode == "JAVA_PRIMARY":
            java_result = self.comparator.java_client.request("GET", java_path)
            go_result = self.comparator.go_client.request("GET", go_path)
            record = comparison_record(case, go_result, java_result, ignored)
            return safe_proxy_result(java_result, record)
        if self.mode == "GO_FALLBACK":
            try:
                java_result = self.comparator.java_client.request("GET", java_path)
            except MigrationError:
                java_result = None
            if java_result is not None and java_result.status < 500:
                go_result = self.comparator.go_client.request("GET", go_path)
                record = comparison_record(case, go_result, java_result, ignored)
                return safe_proxy_result(java_result, record)
            if datetime.now(timezone.utc) >= self.fallback_expires_at:
                raise MigrationError("Go fallback window has expired")
            return safe_proxy_result(
                self.comparator.go_client.request("GET", go_path), None
            )
        return safe_proxy_result(
            self.comparator.java_client.request("GET", java_path), None
        )

    def append_record(self, record: dict[str, Any]) -> None:
        """原子追加不含响应正文的 Shadow 记录。"""
        report = load_json(self.report_path) if self.report_path.exists() else {
            "schemaVersion": 1,
            "sourceCommit": "0c61e7494197ded54eefdeaf9bdeb51807beb752",
            "generatedAt": datetime.now(timezone.utc)
            .isoformat(timespec="microseconds")
            .replace("+00:00", "Z"),
            "records": [],
        }
        records = report.setdefault("records", [])
        records.append(record)
        if len(records) > 10000:
            del records[:-10000]
        report["generatedAt"] = datetime.now(timezone.utc).isoformat(
            timespec="microseconds"
        ).replace("+00:00", "Z")
        atomic_write_json(self.report_path, report)


def is_secret_safe(value: Any) -> bool:
    """判断响应是否仍含明确的 Secret、Token、密码、密文或私钥字段。"""
    try:
        reject_secret_fields(value, "$.response")
        return True
    except MigrationError:
        return False


def safe_proxy_result(
    result: HttpResult, record: dict[str, Any] | None
) -> tuple[HttpResult, dict[str, Any] | None]:
    """在任何代理模式返回响应前强制执行 Secret 字段失败关闭。"""
    if not is_secret_safe(result.body):
        raise MigrationError("proxied response contains a forbidden secret field")
    return result, record


def normalize_body(
    value: Any, case: dict[str, Any], side: str, ignored: list[str]
) -> Any:
    """按 Case 的字段语义投影响应，避免比较语言或分页 DTO 的外壳差异。"""
    stripped = remove_json_pointers(value, ignored)
    normalization = case.get("normalization")
    if normalization is None:
        return stripped
    if not isinstance(normalization, dict):
        raise MigrationError("normalization must be an object")
    item_mappings = normalization.get("itemFieldMappings")
    if item_mappings is not None:
        if not isinstance(item_mappings, list) or not item_mappings:
            raise MigrationError("itemFieldMappings must be a non-empty array")
        pointer = normalization.get(f"{side}CollectionPointer")
        if not isinstance(pointer, str):
            raise MigrationError(f"{side}CollectionPointer is required")
        collection = pointer_value(stripped, pointer)
        if not isinstance(collection, list):
            raise MigrationError(f"{side} collection must be an array")
        projected = [project_fields(item, item_mappings, side) for item in collection]
        stable_key = normalization.get("stableItemKey")
        if not isinstance(stable_key, str) or not stable_key:
            raise MigrationError("stableItemKey is required for collection normalization")
        try:
            projected.sort(key=lambda item: str(item[stable_key]))
        except KeyError as exception:
            raise MigrationError("stableItemKey is absent from projected item") from exception
        return {"items": projected}
    field_mappings = normalization.get("fieldMappings")
    if not isinstance(field_mappings, list) or not field_mappings:
        raise MigrationError("fieldMappings must be a non-empty array")
    return project_fields(stripped, field_mappings, side)


def project_fields(value: Any, mappings: list[Any], side: str) -> dict[str, Any]:
    """把一侧 DTO 按显式 JSON Pointer 投影成相同的规范字段集合。"""
    projected: dict[str, Any] = {}
    for raw in mappings:
        if not isinstance(raw, dict):
            raise MigrationError("field mapping must be an object")
        canonical = require_text(raw, "canonical")
        pointer = require_text(raw, side)
        extracted = pointer_value(value, pointer)
        value_map = raw.get(f"{side}Values", {})
        if not isinstance(value_map, dict):
            raise MigrationError(f"{side}Values must be an object")
        mapped = value_map.get(str(extracted), extracted)
        projected[canonical] = mapped
    return projected


def pointer_value(value: Any, pointer: str) -> Any:
    """读取 JSON Pointer；缺失字段使 Shadow Case 失败而不是静默忽略。"""
    if pointer in {"", "/"}:
        return value
    if not pointer.startswith("/"):
        raise MigrationError("normalization JSON pointer must start with '/'")
    current = value
    for raw_part in pointer[1:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        if isinstance(current, dict) and part in current:
            current = current[part]
        elif isinstance(current, list) and part.isdigit() and int(part) < len(current):
            current = current[int(part)]
        else:
            raise MigrationError(f"normalization field is missing: {pointer}")
    return current


def compile_routes(raw_routes: Any) -> list[dict[str, Any]]:
    """编译 Route 正则并拒绝宽泛或缺少 Java Template 的配置。"""
    if not isinstance(raw_routes, list) or not raw_routes:
        raise MigrationError("routes must be a non-empty array")
    routes = []
    for raw in raw_routes:
        if not isinstance(raw, dict):
            raise MigrationError("route must be an object")
        pattern_text = require_text(raw, "legacyPattern")
        if not pattern_text.startswith("^") or not pattern_text.endswith("$"):
            raise MigrationError("legacyPattern must be fully anchored")
        route = dict(raw)
        route["pattern"] = re.compile(pattern_text)
        require_text(route, "javaTemplate")
        require_text(route, "cohort")
        routes.append(route)
    return routes


def parse_fallback_expiry(config: dict[str, Any]) -> datetime:
    """解析 Go Fallback 截止时间；非 Fallback 模式使用 Epoch。"""
    if config.get("mode") != "GO_FALLBACK":
        return datetime.fromtimestamp(0, tz=timezone.utc)
    value = utc_instant(config.get("goFallbackExpiresAt"), "goFallbackExpiresAt")
    expiry = datetime.fromisoformat(value.replace("Z", "+00:00"))
    current = datetime.now(timezone.utc)
    if expiry <= current or expiry > current + timedelta(hours=24):
        raise MigrationError("Go fallback expiry must be within the next 24 hours")
    return expiry


def serve(config: dict[str, Any], checkpoint: dict[str, Any], report_path: Path) -> int:
    """只在 Loopback 启动临时只读代理。"""
    host = config.get("listenHost", "127.0.0.1")
    port = int(config.get("listenPort", 18081))
    if host not in {"127.0.0.1", "::1", "localhost"}:
        raise MigrationError("compatibility proxy must bind to loopback")
    if port < 1024 or port > 65535:
        raise MigrationError("listenPort is invalid")
    proxy = CompatibilityProxy(config, checkpoint, report_path)

    class Handler(BaseHTTPRequestHandler):
        """绑定当前 Proxy 实例的只读 HTTP Handler。"""

        def do_GET(self) -> None:
            """执行只读 Cohort 路由。"""
            try:
                tenant = self.headers.get("X-AgentArk-Migration-Tenant", "")
                capability = self.headers.get("X-AgentArk-Migration-Capability", "")
                result, record = proxy.handle_get(self.path, tenant, capability)
                if record is not None:
                    proxy.append_record(record)
                encoded = json.dumps(result.body, ensure_ascii=False).encode("utf-8")
                self.send_response(result.status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(encoded)
            except MigrationError:
                encoded = b'{"code":"ARK-MIGRATION-READ-FAILED"}'
                self.send_response(503)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)

        def do_POST(self) -> None:
            """拒绝写请求，防止临时代理形成隐式双写。"""
            self._write_rejected()

        def do_PUT(self) -> None:
            """拒绝写请求。"""
            self._write_rejected()

        def do_PATCH(self) -> None:
            """拒绝写请求。"""
            self._write_rejected()

        def do_DELETE(self) -> None:
            """拒绝写请求。"""
            self._write_rejected()

        def _write_rejected(self) -> None:
            """返回稳定的只读代理拒绝响应。"""
            encoded = b'{"code":"ARK-MIGRATION-READ-ONLY"}'
            self.send_response(405)
            self.send_header("Allow", "GET")
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def log_message(self, format_string: str, *arguments: Any) -> None:
            """禁用可能包含旧 ID 或查询参数的默认访问日志。"""
            return

    server = ThreadingHTTPServer((host, port), Handler)
    server.serve_forever()
    return 0


def compare(config: dict[str, Any], cases_path: Path, report_path: Path) -> int:
    """离线执行固定 Case 并按批准阈值返回 Gate 状态。"""
    raw = json.loads(cases_path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or not isinstance(raw.get("cases"), list):
        raise MigrationError("shadow cases must contain a cases array")
    comparator = ShadowComparator(config)
    records = [comparator.compare_case(case) for case in raw["cases"]]
    summary = summarize(records, require_object(config, "thresholds"))
    atomic_write_json(
        report_path,
        {
            "schemaVersion": 1,
            "sourceCommit": "0c61e7494197ded54eefdeaf9bdeb51807beb752",
            "generatedAt": datetime.now(timezone.utc)
            .isoformat(timespec="microseconds")
            .replace("+00:00", "Z"),
            "records": records,
            "summary": summary,
        },
    )
    return 0 if summary["approved"] else 1


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


def parse_arguments() -> argparse.Namespace:
    """解析 Compare、Gate 与 Serve 子命令。"""
    parser = argparse.ArgumentParser(description="Aistio Shadow Compare 与临时只读兼容代理")
    parser.add_argument("mode", choices=("compare", "gate", "serve"))
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--cases", type=Path)
    parser.add_argument("--checkpoint", type=Path, default=Path(".agentark/migration/aistio-checkpoint.json"))
    parser.add_argument("--report", type=Path, default=Path(".agentark/migration/aistio-shadow-report.json"))
    return parser.parse_args()


def main() -> int:
    """执行 Shadow Compare、已有报告 Gate 或临时 Proxy。"""
    arguments = parse_arguments()
    try:
        config = load_json(arguments.config)
        if arguments.mode == "compare":
            if arguments.cases is None:
                raise MigrationError("--cases is required for compare")
            return compare(config, arguments.cases, arguments.report)
        if arguments.mode == "gate":
            report = load_json(arguments.report)
            records = report.get("records")
            if not isinstance(records, list):
                raise MigrationError("shadow report records are missing")
            summary = summarize(records, require_object(config, "thresholds"))
            report["summary"] = summary
            atomic_write_json(arguments.report, report)
            return 0 if summary["approved"] else 1
        checkpoint = load_json(arguments.checkpoint)
        return serve(config, checkpoint, arguments.report)
    except (MigrationError, OSError, ValueError, json.JSONDecodeError) as exception:
        print(f"shadow compare failed: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
