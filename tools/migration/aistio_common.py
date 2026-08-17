#!/usr/bin/env python3
"""Aistio 绞杀工具共享的规范化、校验、HTTP 与安全文件能力。"""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


SCHEMA_VERSION = 1
SOURCE_COMMIT = "0c61e7494197ded54eefdeaf9bdeb51807beb752"
MAX_HTTP_BODY_BYTES = 8 * 1024 * 1024
FORBIDDEN_SECRET_KEY = re.compile(
    r"(?:password(?:Hash)?|ciphertext|clientSecret|secret(?:Value|Key|Hash)|"
    r"credentialValue|apiKey(?:Value|Hash)|webhookToken|privateKey|masterKey|"
    r"accessToken|refreshToken|bearerToken|sessionToken|tokenHash)$",
    re.IGNORECASE,
)


class MigrationError(RuntimeError):
    """表示不包含业务正文或凭据的稳定迁移错误。"""


def canonical_json(value: Any) -> str:
    """返回 UTF-8、键排序、无多余空白的语言中立 Canonical JSON。"""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def canonical_hash(value: Any) -> str:
    """计算与迁移报告绑定的规范 SHA-256。"""
    digest = hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()
    return f"sha256:{digest}"


def utc_instant(value: Any, field: str) -> str:
    """把 Epoch 毫秒或 RFC 3339 时刻规范为 UTC 微秒精度文本。"""
    if isinstance(value, bool):
        raise MigrationError(f"{field} must be an epoch millisecond or RFC 3339 instant")
    if isinstance(value, (int, float)):
        parsed = datetime.fromtimestamp(float(value) / 1000.0, tz=timezone.utc)
    elif isinstance(value, str):
        text = value.strip()
        if not text:
            raise MigrationError(f"{field} must not be blank")
        try:
            parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        except ValueError as exception:
            raise MigrationError(f"{field} must be an RFC 3339 instant") from exception
        if parsed.tzinfo is None:
            raise MigrationError(f"{field} must contain an explicit timezone")
        parsed = parsed.astimezone(timezone.utc)
    else:
        raise MigrationError(f"{field} must be an epoch millisecond or RFC 3339 instant")
    return parsed.isoformat(timespec="microseconds").replace("+00:00", "Z")


def reject_secret_fields(value: Any, path: str = "$") -> None:
    """递归拒绝 Aistio 导出中的明文、摘要、密文和可重放 Token 字段。"""
    if isinstance(value, dict):
        for key, nested in value.items():
            key_text = str(key)
            child = f"{path}.{key_text}"
            if FORBIDDEN_SECRET_KEY.search(key_text):
                raise MigrationError(f"forbidden secret field at {child}")
            reject_secret_fields(nested, child)
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            reject_secret_fields(nested, f"{path}[{index}]")


def require_sha256(value: Any, field: str) -> str:
    """校验带算法前缀的规范 SHA-256。"""
    if not isinstance(value, str) or re.fullmatch(r"sha256:[0-9a-f]{64}", value) is None:
        raise MigrationError(f"{field} must use sha256:<64-lower-hex>")
    return value


def load_json_or_ndjson(path: Path) -> dict[str, Any]:
    """读取单个 JSON Bundle，或把 NDJSON 资源行包装为 Bundle。"""
    raw = path.read_text(encoding="utf-8")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError:
        records: list[dict[str, Any]] = []
        for number, line in enumerate(raw.splitlines(), start=1):
            if not line.strip():
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as exception:
                raise MigrationError(f"invalid NDJSON at line {number}") from exception
            if not isinstance(item, dict):
                raise MigrationError(f"NDJSON line {number} must be an object")
            records.append(item)
        if not records or records[0].get("recordType") != "header":
            raise MigrationError("NDJSON export must start with a header record")
        source = records[0].get("source")
        if not isinstance(source, dict):
            raise MigrationError("NDJSON header source must be an object")
        resources = records[1:]
        value = {
            "schemaVersion": SCHEMA_VERSION,
            "source": source,
            "resources": resources,
        }
    if not isinstance(value, dict):
        raise MigrationError("migration export must be a JSON object")
    return value


def load_json(path: Path) -> dict[str, Any]:
    """读取必须为 JSON Object 的配置或 Checkpoint。"""
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise MigrationError(f"cannot read JSON object: {path}") from exception
    if not isinstance(value, dict):
        raise MigrationError(f"JSON document must be an object: {path}")
    return value


def atomic_write_json(path: Path, value: Any) -> None:
    """在同目录先写临时文件再原子替换 Checkpoint 或报告。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, ensure_ascii=False, sort_keys=True, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise


def json_pointer_differences(left: Any, right: Any, path: str = "") -> list[str]:
    """返回不含字段值的稳定 JSON Pointer 差异列表。"""
    if type(left) is not type(right):
        return [path or "/"]
    if isinstance(left, dict):
        result: list[str] = []
        for key in sorted(set(left) | set(right)):
            escaped = str(key).replace("~", "~0").replace("/", "~1")
            child = f"{path}/{escaped}"
            if key not in left or key not in right:
                result.append(child)
            else:
                result.extend(json_pointer_differences(left[key], right[key], child))
        return result
    if isinstance(left, list):
        result = []
        maximum = max(len(left), len(right))
        for index in range(maximum):
            child = f"{path}/{index}"
            if index >= len(left) or index >= len(right):
                result.append(child)
            else:
                result.extend(json_pointer_differences(left[index], right[index], child))
        return result
    return [] if left == right else [path or "/"]


def remove_json_pointers(value: Any, pointers: Iterable[str]) -> Any:
    """复制 JSON 后删除允许忽略的非业务字段。"""
    copied = json.loads(canonical_json(value))
    for pointer in pointers:
        if not pointer.startswith("/"):
            raise MigrationError("ignored JSON pointer must start with '/'")
        parts = [part.replace("~1", "/").replace("~0", "~") for part in pointer[1:].split("/")]
        current = copied
        for part in parts[:-1]:
            if isinstance(current, dict):
                current = current.get(part)
            elif isinstance(current, list) and part.isdigit() and int(part) < len(current):
                current = current[int(part)]
            else:
                current = None
            if current is None:
                break
        if current is None or not parts:
            continue
        last = parts[-1]
        if isinstance(current, dict):
            current.pop(last, None)
        elif isinstance(current, list) and last.isdigit() and int(last) < len(current):
            current.pop(int(last))
    return copied


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """拒绝迁移工具跟随跨主机重定向。"""

    def redirect_request(self, req: Any, fp: Any, code: int, msg: str, headers: Any, newurl: str) -> None:
        """始终拒绝自动重定向。"""
        return None


@dataclass(frozen=True)
class HttpResult:
    """表示一次有界 HTTP 调用结果。"""

    status: int
    body: Any
    elapsed_ms: int


class JsonHttpClient:
    """只访问配置基地址、拒绝重定向并从文件加载短期令牌的 JSON Client。"""

    def __init__(self, base_url: str, token_file: Path | None, timeout_seconds: float = 10.0):
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise MigrationError("HTTP base URL must be absolute")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise MigrationError("HTTP base URL must not contain credentials, query, or fragment")
        if parsed.scheme == "http" and parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
            raise MigrationError("non-loopback migration endpoint must use HTTPS")
        if timeout_seconds <= 0 or timeout_seconds > 60:
            raise MigrationError("HTTP timeout must be between 0 and 60 seconds")
        self._base_url = base_url.rstrip("/")
        self._token_file = token_file
        self._timeout_seconds = timeout_seconds
        self._opener = urllib.request.build_opener(NoRedirectHandler())

    def request(
        self,
        method: str,
        path: str,
        body: Any | None = None,
        headers: dict[str, str] | None = None,
    ) -> HttpResult:
        """执行一次有界 JSON 请求，错误正文不进入异常消息。"""
        if not path.startswith("/") or path.startswith("//"):
            raise MigrationError("HTTP path must be an absolute path")
        request_headers = {"Accept": "application/json", "User-Agent": "agentark-aistio-migration/1"}
        request_headers.update(headers or {})
        token = self._load_token()
        if token is not None:
            request_headers["Authorization"] = f"Bearer {token}"
        encoded = None
        if body is not None:
            encoded = canonical_json(body).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self._base_url}{path}", data=encoded, headers=request_headers, method=method
        )
        started = datetime.now(timezone.utc)
        try:
            with self._opener.open(request, timeout=self._timeout_seconds) as response:
                status = response.status
                raw = response.read(MAX_HTTP_BODY_BYTES + 1)
        except urllib.error.HTTPError as exception:
            status = exception.code
            raw = exception.read(MAX_HTTP_BODY_BYTES + 1)
        except (OSError, urllib.error.URLError) as exception:
            raise MigrationError("migration endpoint is unreachable") from exception
        elapsed = int((datetime.now(timezone.utc) - started).total_seconds() * 1000)
        if len(raw) > MAX_HTTP_BODY_BYTES:
            raise MigrationError("migration endpoint response exceeds size limit")
        if not raw:
            parsed_body: Any = None
        else:
            try:
                parsed_body = json.loads(raw)
            except json.JSONDecodeError as exception:
                raise MigrationError("migration endpoint returned invalid JSON") from exception
        return HttpResult(status=status, body=parsed_body, elapsed_ms=elapsed)

    def _load_token(self) -> str | None:
        """按请求读取令牌文件，禁止符号链接、空白和超限内容。"""
        if self._token_file is None:
            return None
        path = self._token_file
        if path.is_symlink() or not path.is_file():
            raise MigrationError("token file must be a regular non-symlink file")
        metadata = path.stat()
        if stat.S_IMODE(metadata.st_mode) & 0o077:
            raise MigrationError("token file must not be readable or writable by group/other")
        if metadata.st_size < 1 or metadata.st_size > 8192:
            raise MigrationError("token file size is invalid")
        token = path.read_text(encoding="utf-8").strip()
        if not token or any(character.isspace() for character in token):
            raise MigrationError("token file content is invalid")
        return token
