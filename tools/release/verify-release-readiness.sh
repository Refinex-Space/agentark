#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "${REPOSITORY_ROOT}"

# 发布基线必须包含法律、协作、安全、版本和收官证据入口。
for required in \
  LICENSE \
  NOTICE \
  THIRD_PARTY_NOTICES.md \
  CHANGELOG.md \
  CONTRIBUTING.md \
  SECURITY.md \
  docs/release/architecture-drift-audit.md \
  docs/release/gates-g0-g9.md \
  docs/release/release-artifacts.md \
  docs/releases/v0-1-0.md \
  docs/implementation/phase-23-release-readiness.md; do
  test -s "${required}"
done

# Phase 00–22 必须全部保留 DONE 状态和非空阶段报告。
python3 - <<'PY'
from pathlib import Path
import re

plan = Path("PLAN.md").read_text(encoding="utf-8")
rows = {}
for line in plan.splitlines():
    match = re.match(r"\|\s*(\d{2})\s*\|\s*([^|]+)\|[^|]*\|\s*`([^`]+)`", line)
    if match:
        rows[int(match.group(1))] = (match.group(2).strip(), match.group(3))
for phase in range(23):
    status, report = rows.get(phase, ("MISSING", ""))
    if status != "DONE":
        raise SystemExit(f"Phase {phase:02d} is not DONE: {status}")
    path = Path(report)
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Phase {phase:02d} report is missing: {report}")
print("Phase 00-22 evidence verified")
PY

# AgentScope Runtime 类型只能出现在专用 Provider；Knowledge 只允许指定 RAG Adapter。
unexpected_agentscope="$(
  rg -l '^import io\.agentscope' --glob '*.java' --glob '!target/**' \
    agentark-* 2>/dev/null \
    | awk '!/^agentark-runtime-provider-agentscope\// && \
           !/^agentark-knowledge\/src\/(main|test)\/java\/space\/refinex\/agentark\/knowledge\/adapter\/out\/vector\/agentscope\//'
)"
if [[ -n "${unexpected_agentscope}" ]]; then
  echo "AgentScope import escaped the whitelist:" >&2
  echo "${unexpected_agentscope}" >&2
  exit 1
fi

# 生产源码禁止跨 Schema SQL；隔离验证测试可以使用无权 Sentinel 查询。
if rg -n 'agentark_(control|runtime|scheduler)\.' \
  agentark-control/src/main agentark-runtime/src/main agentark-scheduling/src/main \
  agentark-services/*/src/main; then
  echo "cross-schema SQL or identifier detected in production source" >&2
  exit 1
fi

# 只能存在四个明确的部署入口。
spring_applications="$(rg -l '@SpringBootApplication' --glob '*.java' --glob '!target/**' \
  agentark-services | LC_ALL=C sort)"
if [[ "$(printf '%s\n' "${spring_applications}" | sed '/^$/d' | wc -l | tr -d ' ')" != "4" ]]; then
  echo "expected exactly four SpringBootApplication classes" >&2
  echo "${spring_applications}" >&2
  exit 1
fi

# Gateway 不得拥有 Mapper、业务实现模块或数据库依赖。
if rg -n '@Mapper|TableName|JpaRepository|agentark-control|agentark-runtime' \
  agentark-services/agentark-gateway-server; then
  echo "Gateway business or persistence dependency detected" >&2
  exit 1
fi

# Runtime 和 Scheduler 的 POM 必须保持跨平面实现隔离。
if rg -n '<artifactId>agentark-control</artifactId>' agentark-runtime/pom.xml \
  agentark-runtime-provider-agentscope/pom.xml; then
  echo "Runtime depends on Control implementation" >&2
  exit 1
fi
if rg -n '<artifactId>agentark-runtime(-provider-agentscope)?</artifactId>' \
  agentark-scheduling/pom.xml; then
  echo "Scheduler depends on Runtime implementation" >&2
  exit 1
fi

# 禁止 JPA/Hibernate、万能工具类和业务类型进入 Foundation。
if rg -n 'jakarta\.persistence|org\.hibernate|JpaRepository|RedisUtils|SpringContextHolder' \
  agentark-foundation/*/src/main agentark-control/src/main agentark-runtime/src/main \
  agentark-scheduling/src/main \
  --glob '!target/**'; then
  exit 1
fi
if rg -n '@(Controller|RestController|Mapper|Entity|TableName)' agentark-foundation/*/src/main \
  --glob '!target/**'; then
  exit 1
fi

# 首个 Provider 与项目版本必须固定，不允许动态依赖。
rg -q '<version>0\.1\.0</version>' pom.xml
rg -q '<agentscope.version>2\.0\.2</agentscope.version>' pom.xml
if rg -n '<version>\s*(LATEST|RELEASE|[^<]*SNAPSHOT)' --glob 'pom.xml' .; then
  echo "dynamic Maven version detected" >&2
  exit 1
fi

# 所有生产基础镜像变量必须有固定摘要，FROM 只能引用这些受控变量。
for dockerfile in deploy/container/Dockerfile.*; do
  while IFS= read -r image_variable; do
    if ! rg -q "^ARG ${image_variable}=[^[:space:]]+@sha256:[0-9a-f]{64}$" \
      "${dockerfile}"; then
      echo "container base image variable is not pinned: ${dockerfile}:${image_variable}" >&2
      exit 1
    fi
  done < <(sed -n 's/^FROM ${\([^}]*\)}.*/\1/p' "${dockerfile}")
done

# 默认 Compose/Helm 必须保持 Java-only，不能恢复 Go Aistio。
if rg -ni 'aistio|golang' deploy/compose deploy/helm; then
  echo "default deployment still references Go Aistio" >&2
  exit 1
fi

# 临时标记不得进入首个基线；拼接模式避免门禁脚本自匹配。
temporary_pattern='\b(TO''DO|FIX''ME|HA''CK|TEMPO''RARY)\b'
if rg -n "${temporary_pattern}" \
  --glob '!PLAN.md' \
  --glob '!docs/implementation/**' \
  --glob '!target/**' \
  --glob '!node_modules/**' \
  --glob '!.agentark/**' .; then
  echo "temporary marker detected" >&2
  exit 1
fi

./tools/release/verify-contract-baseline.sh
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check

echo "release readiness static audit passed"
