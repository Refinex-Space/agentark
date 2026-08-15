#!/usr/bin/env python3
"""Verify fixed upstream commits without modifying upstream repositories."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PARENT = ROOT.parent
SOURCES = (
    (
        "AgentScope Java",
        "AGENTSCOPE_REPO",
        DEFAULT_PARENT / "agentscope-java",
        "AGENTSCOPE_ROOT",
        ROOT / ".agentark/upstreams/agentscope-java-2.0.2",
        "0c61e7494197ded54eefdeaf9bdeb51807beb752",
        ("agentscope-service", "agentscope-harness"),
    ),
    (
        "DeepSeek Harness",
        "DEEPSEEK_HARNESS_REPO",
        DEFAULT_PARENT / "deepseek-harness",
        "DEEPSEEK_HARNESS_ROOT",
        ROOT / ".agentark/upstreams/deepseek-harness",
        "47f943859bef60e4160492346772ded9b24f765a",
        ("apps", "packages"),
    ),
)


def git(path: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(path), *args],
        text=True,
        capture_output=True,
        check=False,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-worktrees", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []

    for name, repo_env, repo_default, root_env, root_default, commit, anchors in SOURCES:
        repo = Path(os.environ.get(repo_env, repo_default)).expanduser().resolve()
        worktree = Path(os.environ.get(root_env, root_default)).expanduser().resolve()
        if git(repo, "rev-parse", "--git-dir").returncode != 0:
            errors.append(f"{name} source repository is unavailable: {repo}")
            continue
        if git(repo, "cat-file", "-e", f"{commit}^{{commit}}").returncode != 0:
            errors.append(f"{name} fixed commit is unavailable: {commit}")
            continue
        if args.require_worktrees:
            head = git(worktree, "rev-parse", "HEAD")
            if head.returncode != 0:
                errors.append(f"{name} fixed worktree is unavailable: {worktree}")
            elif head.stdout.strip() != commit:
                errors.append(
                    f"{name} fixed worktree mismatch: expected {commit}, got {head.stdout.strip()}"
                )
            elif git(worktree, "status", "--porcelain").stdout.strip():
                errors.append(f"{name} fixed worktree contains local changes: {worktree}")
            elif git(worktree, "symbolic-ref", "-q", "HEAD").returncode == 0:
                errors.append(f"{name} fixed worktree is not detached: {worktree}")
            else:
                for anchor in anchors:
                    if not (worktree / anchor).is_dir():
                        errors.append(
                            f"{name} fixed worktree is missing required directory: {anchor}"
                        )
        print(f"verified {name} source commit: {commit}")

    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
