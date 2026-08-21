#!/usr/bin/env python3
"""Validate AgentArk's repository knowledge control plane with stdlib only."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[2]
REQUIRED_ROOT = ("AGENTS.md", "CLAUDE.md", "README.md", "PLAN.md")
FRONT_MATTER_KEYS = ("owner", "updated", "status", "referenced_by")
STALE_TOKENS = (
    "docs/control-plane/system-architecture.md",
    "docs/harness/control-plane/system-architecture.md",
    "agentark-runtime-agentscope",
    "PLANS.md",
)
LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
CHINESE_RE = re.compile(r"[\u3400-\u9fff]")
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
APPROVED_ROOT_MODULES = (
    "agentark-bom",
    "agentark-kernel",
    "agentark-foundation",
    "agentark-control",
    "agentark-knowledge",
    "agentark-runtime",
    "agentark-runtime-provider-agentscope",
    "agentark-scheduling",
    "agentark-services",
)
APPROVED_FOUNDATION_MODULES = (
    "agentark-starter-web",
    "agentark-starter-security",
    "agentark-starter-persistence",
    "agentark-starter-redis",
    "agentark-starter-storage",
    "agentark-starter-observability",
)
APPROVED_SERVICE_MODULES = (
    "agentark-gateway-server",
    "agentark-control-server",
    "agentark-runtime-server",
    "agentark-scheduler-server",
)
REQUIRED_CONTRACTS = (
    "contracts/openapi/public-control-v1.yaml",
    "contracts/openapi/public-gateway-v1.yaml",
    "contracts/openapi/public-runtime-v1.yaml",
    "contracts/openapi/public-scheduler-v1.yaml",
    "contracts/openapi/internal-control-v1.yaml",
    "contracts/openapi/internal-runtime-v1.yaml",
    "contracts/openapi/internal-scheduler-v1.yaml",
    "contracts/asyncapi/runtime-events-v1.yaml",
    "contracts/schemas/agent-revision-snapshot/v1.json",
    "contracts/schemas/runtime-event/v1.json",
    "contracts/schemas/problem-detail/v1.json",
    "contracts/schemas/catalog-public/v1.json",
    "contracts/schemas/knowledge-public/v1.json",
    "contracts/schemas/knowledge-ingestion-internal/v1.json",
    "contracts/schemas/knowledge-retrieval/v1.json",
)
SERVER_ARTIFACTS = frozenset(APPROVED_SERVICE_MODULES)
KERNEL_FORBIDDEN_IMPORTS = (
    "org.springframework",
    "jakarta.persistence",
    "com.baomidou",
    "io.agentscope",
    "com.fasterxml",
    "org.redisson",
    "redis.clients",
)
SCHEMA_OWNERS = {
    "agentark-control": (
        "agentark_control",
        "control",
        "agentark-services/agentark-control-server/src/main/resources/application.yml",
        "AGENTARK_CONTROL_DB",
    ),
    "agentark-runtime": (
        "agentark_runtime",
        "runtime",
        "agentark-services/agentark-runtime-server/src/main/resources/application.yml",
        "AGENTARK_RUNTIME_DB",
    ),
    "agentark-scheduling": (
        "agentark_scheduler",
        "scheduler",
        "agentark-services/agentark-scheduler-server/src/main/resources/application.yml",
        "AGENTARK_SCHEDULER_DB",
    ),
}
JAVA_LICENSE_HEADER = """/*
 * Copyright 2026 refinex.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

"""


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_front_matter(path: Path, text: str) -> dict[str, str]:
    if not text.startswith("---\n"):
        return {}
    end = text.find("\n---\n", 4)
    if end < 0:
        return {}
    result: dict[str, str] = {}
    for line in text[4:end].splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            result[key.strip()] = value.strip()
    return result


def markdown_targets(path: Path, text: str) -> set[Path]:
    targets: set[Path] = set()
    for raw in LINK_RE.findall(text):
        value = raw.strip().strip("<>")
        if not value or value.startswith(("http://", "https://", "mailto:", "#")):
            continue
        value = unquote(value.split("#", 1)[0].split("?", 1)[0])
        if value:
            targets.add((path.parent / value).resolve())
    return targets


def changed_paths(base: str | None) -> set[str]:
    command = ["git", "diff", "--name-only"]
    command.append(f"{base}...HEAD" if base else "HEAD")
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    paths = set(result.stdout.splitlines()) if result.returncode == 0 else set()
    untracked = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if not base and untracked.returncode == 0:
        paths.update(untracked.stdout.splitlines())
    return {path for path in paths if path}


def require_docs_with_sensitive_changes(changed: set[str], errors: list[str]) -> None:
    migration_planes = {
        "agentark-control": "docs/database/control-schema.md",
        "agentark-runtime": "docs/database/runtime-schema.md",
        "agentark-scheduling": "docs/database/scheduler-schema.md",
    }
    for module, document in migration_planes.items():
        prefix = f"{module}/src/main/resources/db/migration/"
        if any(path.startswith(prefix) for path in changed) and document not in changed:
            errors.append(f"{prefix} changed without {document}")

    if any(path.startswith("contracts/") for path in changed):
        owners = {"docs/standards/api.md", "docs/architecture/overview.md"}
        if not owners.intersection(changed):
            errors.append("contracts/ changed without API standard or architecture update")

    config_change = any(
        path.startswith(("deploy/", "agentark-services/"))
        and ("application" in Path(path).name or path.startswith("deploy/"))
        for path in changed
    )
    if config_change and not {"docs/config/reference.md", "docs/guides/runbook.md"}.intersection(changed):
        errors.append("runtime/deploy configuration changed without config reference or runbook")


def pom_modules(path: Path, errors: list[str]) -> tuple[str, ...]:
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        errors.append(f"invalid Maven POM {rel(path)}: {exc}")
        return ()
    return tuple(
        element.text.strip()
        for element in root.findall("./m:modules/m:module", MAVEN_NAMESPACE)
        if element.text and element.text.strip()
    )


def require_pom_documentation(path: Path, errors: list[str]) -> None:
    lines = read(path).splitlines()
    descriptions = [line.strip() for line in lines if line.strip().startswith("<description>")]
    if len(descriptions) != 1 or not CHINESE_RE.search(descriptions[0]):
        errors.append(f"POM description must be a single Chinese line: {rel(path)}")

    for index, line in enumerate(lines):
        element = line.strip()
        if element not in {"<dependency>", "<plugin>", "<execution>"}:
            continue
        previous = index - 1
        while previous >= 0 and not lines[previous].strip():
            previous -= 1
        comment = lines[previous].strip() if previous >= 0 else ""
        if not (
            comment.startswith("<!--")
            and comment.endswith("-->")
            and CHINESE_RE.search(comment)
        ):
            errors.append(
                f"{element[1:-1]} lacks an adjacent Chinese comment: {rel(path)}:{index + 1}"
            )


def require_maven_foundation(errors: list[str]) -> None:
    root_pom = ROOT / "pom.xml"
    if not root_pom.is_file():
        return

    required_files = (
        ROOT / "mvnw",
        ROOT / "mvnw.cmd",
        ROOT / ".mvn/wrapper/maven-wrapper.properties",
        ROOT / "LICENSE",
        ROOT / "NOTICE",
    )
    for path in required_files:
        if not path.is_file():
            errors.append(f"Maven foundation is missing {rel(path)}")

    module_sets = (
        (root_pom, APPROVED_ROOT_MODULES),
        (ROOT / "agentark-foundation/pom.xml", APPROVED_FOUNDATION_MODULES),
        (ROOT / "agentark-services/pom.xml", APPROVED_SERVICE_MODULES),
    )
    for path, expected in module_sets:
        if not path.is_file():
            errors.append(f"Maven foundation is missing {rel(path)}")
            continue
        actual = pom_modules(path, errors)
        if actual != expected:
            errors.append(f"unexpected Maven modules in {rel(path)}: {actual}")

    if (ROOT / "upstream-baseline").exists():
        errors.append("mechanical upstream-baseline must not exist in the implementation worktree")

    allowed_agentscope_poms = {
        ROOT / "agentark-bom/pom.xml",
        ROOT / "agentark-knowledge/pom.xml",
        ROOT / "agentark-runtime-provider-agentscope/pom.xml",
    }
    knowledge_agentscope_pom = ROOT / "agentark-knowledge/pom.xml"
    for module in APPROVED_ROOT_MODULES:
        module_root = ROOT / module
        if not module_root.is_dir():
            continue
        for path in module_root.rglob("pom.xml"):
            if "target" in path.parts:
                continue
            try:
                pom = ET.parse(path).getroot()
            except (ET.ParseError, OSError):
                continue
            dependencies = pom.findall(".//m:dependency", MAVEN_NAMESPACE)
            for dependency in dependencies:
                group = dependency.find("m:groupId", MAVEN_NAMESPACE)
                if group is None or not group.text or group.text.strip() != "io.agentscope":
                    continue
                artifact = dependency.find("m:artifactId", MAVEN_NAMESPACE)
                artifact_id = artifact.text.strip() if artifact is not None and artifact.text else ""
                if path not in allowed_agentscope_poms:
                    errors.append(
                        f"AgentScope dependency escapes provider boundary: {rel(path)}"
                    )
                elif path == knowledge_agentscope_pom and artifact_id != "agentscope-core":
                    errors.append(
                        "Knowledge AgentScope boundary only permits agentscope-core: "
                        f"{artifact_id or '<missing>'}"
                    )

    server_roots = {
        (ROOT / "agentark-services" / module).resolve() for module in APPROVED_SERVICE_MODULES
    }
    for path in ROOT.rglob("pom.xml"):
        if "target" in path.parts or ".agentark" in path.parts:
            continue
        require_pom_documentation(path, errors)
        try:
            pom = ET.parse(path).getroot()
        except (ET.ParseError, OSError):
            continue
        is_server = any(root in path.resolve().parents for root in server_roots)
        if is_server:
            continue
        dependencies = pom.findall("./m:dependencies/m:dependency", MAVEN_NAMESPACE)
        for dependency in dependencies:
            artifact = dependency.find("m:artifactId", MAVEN_NAMESPACE)
            if artifact is not None and artifact.text and artifact.text.strip() in SERVER_ARTIFACTS:
                errors.append(f"library POM depends on server module: {rel(path)}")


def require_phase03_boundaries(errors: list[str]) -> None:
    for name in REQUIRED_CONTRACTS:
        path = ROOT / name
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f"Phase 03 contract is missing or empty: {name}")

    kernel_source = ROOT / "agentark-kernel/src/main/java"
    if kernel_source.is_dir():
        for path in kernel_source.rglob("*.java"):
            text = read(path)
            for token in KERNEL_FORBIDDEN_IMPORTS:
                if token in text:
                    errors.append(f"Kernel dependency boundary violation {token}: {rel(path)}")

    services_root = (ROOT / "agentark-services").resolve()
    server_roots = {
        (services_root / module).resolve() for module in APPROVED_SERVICE_MODULES
    }
    for path in ROOT.rglob("*.java"):
        if "target" in path.parts or ".agentark" in path.parts:
            continue
        text = read(path)
        if not text.startswith(JAVA_LICENSE_HEADER):
            errors.append(f"Java file does not use the standard Apache-2.0 header: {rel(path)}")
        if "@SpringBootApplication" not in text:
            continue
        resolved = path.resolve()
        if not any(root in resolved.parents for root in server_roots):
            errors.append(f"@SpringBootApplication outside a server module: {rel(path)}")


def require_schema_ownership(errors: list[str]) -> None:
    """Enforce Phase 06 Schema, Flyway and server configuration ownership."""
    schema_names = {definition[0] for definition in SCHEMA_OWNERS.values()}
    qualified_schema = re.compile(r"agentark_(?:control|runtime|scheduler)\s*\.", re.IGNORECASE)

    for module, (schema, location, server_config, environment_prefix) in SCHEMA_OWNERS.items():
        migration_root = ROOT / module / "src/main/resources/db/migration" / location
        baseline = migration_root / "V1__phase_06_schema_baseline.sql"
        if not baseline.is_file() or baseline.stat().st_size == 0:
            errors.append(f"Phase 06 migration baseline is missing or empty: {rel(baseline)}")

        main_root = ROOT / module / "src/main"
        if main_root.is_dir():
            for path in main_root.rglob("*"):
                if not path.is_file() or "target" in path.parts:
                    continue
                try:
                    text = read(path)
                except UnicodeDecodeError:
                    continue
                foreign = sorted(name for name in schema_names - {schema} if name in text)
                if foreign:
                    errors.append(
                        f"cross-Schema reference {foreign} in owner module: {rel(path)}"
                    )
                if qualified_schema.search(text):
                    errors.append(
                        f"qualified Schema SQL is forbidden; use the owner DataSource: {rel(path)}"
                    )

        config_path = ROOT / server_config
        if not config_path.is_file():
            errors.append(f"owner server configuration is missing: {server_config}")
            continue
        config = read(config_path)
        required_tokens = (
            f"${{{environment_prefix}_URL}}",
            f"${{{environment_prefix}_USERNAME}}",
            f"${{{environment_prefix}_PASSWORD}}",
            f"default-schema: {schema}",
            f"schemas: {schema}",
            f"locations: classpath:db/migration/{location}",
            "clean-disabled: true",
            "create-schemas: false",
        )
        for token in required_tokens:
            if token not in config:
                errors.append(f"owner server configuration lacks {token}: {server_config}")


def split_sql_definitions(body: str) -> list[str]:
    """Split CREATE TABLE definitions on top-level commas."""
    definitions: list[str] = []
    start = 0
    depth = 0
    quoted = False
    index = 0
    while index < len(body):
        character = body[index]
        if character == "'":
            if quoted and index + 1 < len(body) and body[index + 1] == "'":
                index += 2
                continue
            quoted = not quoted
        elif not quoted:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                definitions.append(body[start:index].strip())
                start = index + 1
        index += 1
    tail = body[start:].strip()
    if tail:
        definitions.append(tail)
    return definitions


def require_flyway_comments(errors: list[str]) -> None:
    """Require native Chinese comments for every Flyway table and column."""
    create_table = re.compile(
        r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-z][a-z0-9_]*)`?"
        r"\s*\((.*?)\)\s*(ENGINE\s*=\s*[^;]+);",
        re.IGNORECASE | re.DOTALL,
    )
    column_start = re.compile(r"^`?([a-z][a-z0-9_]*)`?\s+(.+)$", re.IGNORECASE | re.DOTALL)
    comment_value = re.compile(r"\bCOMMENT\s+'((?:''|[^'])+)'", re.IGNORECASE | re.DOTALL)
    enumerable_check = re.compile(
        r"\bCHECK\s*\(\s*`?([a-z][a-z0-9_]*)`?\s+IN\s*\(([^()]*)\)\s*\)",
        re.IGNORECASE | re.DOTALL,
    )
    non_column_tokens = {"primary", "constraint", "unique", "foreign", "check", "index", "key"}

    for path in ROOT.rglob("V*.sql"):
        if "target" in path.parts or ".agentark" in path.parts:
            continue
        sql = read(path)
        tables = list(create_table.finditer(sql))
        declared_table_count = len(re.findall(r"\bCREATE\s+TABLE\b", sql, re.IGNORECASE))
        if len(tables) != declared_table_count:
            errors.append(f"cannot parse every Flyway CREATE TABLE for comments: {rel(path)}")
            continue

        for table in tables:
            table_name, body, trailer = table.groups()
            table_comment = re.search(
                r"\bCOMMENT\s*=\s*'((?:''|[^'])+)'", trailer, re.IGNORECASE | re.DOTALL
            )
            if not table_comment or not CHINESE_RE.search(table_comment.group(1)):
                errors.append(f"Flyway table lacks a Chinese COMMENT: {rel(path)}:{table_name}")

            columns: dict[str, str] = {}
            comments: dict[str, str] = {}
            for definition in split_sql_definitions(body):
                match = column_start.match(definition)
                if not match or match.group(1).lower() in non_column_tokens:
                    continue
                column_name = match.group(1).lower()
                column_definition = match.group(2)
                columns[column_name] = column_definition
                column_comment = comment_value.search(column_definition)
                if not column_comment or not CHINESE_RE.search(column_comment.group(1)):
                    errors.append(
                        f"Flyway column lacks a Chinese COMMENT: {rel(path)}:"
                        f"{table_name}.{column_name}"
                    )
                    continue
                comments[column_name] = column_comment.group(1)
                if re.match(r"^BOOLEAN\b", column_definition, re.IGNORECASE):
                    if "0" not in column_comment.group(1) or "1" not in column_comment.group(1):
                        errors.append(
                            f"Flyway BOOLEAN COMMENT must document 0 and 1: {rel(path)}:"
                            f"{table_name}.{column_name}"
                        )

            for check in enumerable_check.finditer(body):
                column_name = check.group(1).lower()
                values = re.findall(r"'((?:''|[^'])+)'", check.group(2))
                comment = comments.get(column_name, "")
                missing = [value for value in values if value not in comment]
                if column_name not in columns or missing:
                    errors.append(
                        f"Flyway enumerable COMMENT is incomplete {missing}: {rel(path)}:"
                        f"{table_name}.{column_name}"
                    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", help="Git base revision for docs-with-code checks")
    args = parser.parse_args()
    errors: list[str] = []

    for name in REQUIRED_ROOT:
        if not (ROOT / name).is_file():
            errors.append(f"missing root control file: {name}")

    agents = ROOT / "AGENTS.md"
    if agents.is_file() and len(read(agents).splitlines()) > 150:
        errors.append("AGENTS.md exceeds 150 lines")

    claude = ROOT / "CLAUDE.md"
    if claude.is_file() and read(claude) != "@AGENTS.md\n":
        errors.append("CLAUDE.md must contain exactly @AGENTS.md")

    docs = sorted((ROOT / "docs").rglob("*.md"))
    plan = ROOT / "PLAN.md"
    active_docs: list[Path] = []
    for path in [plan, *docs]:
        text = read(path)
        front_matter = parse_front_matter(path, text)
        if not front_matter:
            errors.append(f"missing front matter: {rel(path)}")
            continue
        if front_matter.get("status", "").lower() == "active":
            active_docs.append(path)
            for key in FRONT_MATTER_KEYS:
                if not front_matter.get(key):
                    errors.append(f"missing front matter key {key}: {rel(path)}")

    route_sources = [ROOT / "AGENTS.md", ROOT / "docs/README.md"]
    routed_targets: set[Path] = set()
    route_text = ""
    for source in route_sources:
        if source.is_file():
            source_text = read(source)
            route_text += source_text
            routed_targets.update(markdown_targets(source, source_text))
    for path in active_docs:
        if path not in routed_targets and rel(path) not in route_text:
            errors.append(f"active document is not directly routed: {rel(path)}")

    markdown = [ROOT / "README.md", ROOT / "AGENTS.md", plan, *docs]
    for path in markdown:
        if not path.is_file():
            continue
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), start=1):
            if line.rstrip(" \t") != line:
                errors.append(f"trailing whitespace: {rel(path)}:{line_number}")
        for token in STALE_TOKENS:
            if token in text:
                errors.append(f"stale token {token}: {rel(path)}")
        for target in markdown_targets(path, text):
            if not target.exists():
                errors.append(f"broken link in {rel(path)}: {target}")

    if plan.is_file():
        text = read(plan)
        phases = re.findall(r"^## Phase (\d{2})\b", text, flags=re.MULTILINE)
        expected = [f"{value:02d}" for value in range(24)]
        if phases != expected:
            errors.append(f"PLAN phase headings must be exactly 00-23, got {phases}")
        status_rows = re.findall(
            r"^\| (\d{2}) \| (NOT_STARTED|IN_PROGRESS|BLOCKED|DONE) \|",
            text,
            flags=re.MULTILINE,
        )
        if [phase for phase, _ in status_rows] != expected:
            errors.append("PLAN status table must contain exactly Phase 00-23")

    require_docs_with_sensitive_changes(changed_paths(args.base), errors)
    require_maven_foundation(errors)
    require_phase03_boundaries(errors)
    require_schema_ownership(errors)
    require_flyway_comments(errors)

    if errors:
        print(f"knowledge gate failed: {len(errors)} error(s)")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"knowledge gate passed: {len(active_docs)} active documents checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
