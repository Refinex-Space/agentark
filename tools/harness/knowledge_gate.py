#!/usr/bin/env python3
"""Validate AgentArk's repository knowledge control plane with stdlib only."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
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

    if errors:
        print(f"knowledge gate failed: {len(errors)} error(s)")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"knowledge gate passed: {len(active_docs)} active documents checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
