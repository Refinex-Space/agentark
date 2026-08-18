#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 固定 k6 二进制由引导脚本安装到已忽略目录。
readonly K6="${REPOSITORY_ROOT}/.agentark/bin/k6"
# 真实 E2E Stack 生成权限为 0600 的短期会话。
readonly SESSION_FILE="${REPOSITORY_ROOT}/agentark-web/test-results/e2e-session.json"
# 性能种子只保存非敏感资源标识。
readonly SEED_FILE="${REPOSITORY_ROOT}/.agentark/evidence/phase22/performance-seed.json"
# k6 机器可读证据。
readonly SUMMARY_FILE="${REPOSITORY_ROOT}/.agentark/evidence/phase22/performance-summary.json"
# SSE First Event 机器可读证据。
readonly SSE_FILE="${REPOSITORY_ROOT}/.agentark/evidence/phase22/sse-first-event.json"
# k6 人类可读证据。
readonly REPORT_FILE="${REPOSITORY_ROOT}/.agentark/evidence/phase22/performance-report.txt"
# E2E Stack 日志只进入已忽略证据目录。
readonly STACK_LOG="${REPOSITORY_ROOT}/.agentark/evidence/phase22/performance-stack.log"
# 后台 Stack 进程标识。
STACK_PID=""

# 只终止当前脚本启动的 E2E Stack；Stack 自身负责精确清理临时容器和子进程。
cleanup() {
  local status=$?
  if [[ -n "${STACK_PID}" ]] && kill -0 "${STACK_PID}" >/dev/null 2>&1; then
    kill -TERM "${STACK_PID}" >/dev/null 2>&1 || true
    wait "${STACK_PID}" >/dev/null 2>&1 || true
  fi
  exit "${status}"
}
trap cleanup EXIT

# 验证 Docker、Node 和 k6；固定工具缺失时执行安全引导。
command -v docker >/dev/null
command -v node >/dev/null
docker info >/dev/null
if [[ ! -x "${K6}" ]]; then
  "${REPOSITORY_ROOT}/tools/production/bootstrap-tools.sh"
fi
mkdir -p "${REPOSITORY_ROOT}/.agentark/evidence/phase22"
rm -f "${SESSION_FILE}" "${SEED_FILE}" "${SUMMARY_FILE}" "${SSE_FILE}" "${REPORT_FILE}"

# 启动真实四服务测试 Classpath；Scheduler Worker 仅在本演练中启用。
AGENTARK_E2E_SCHEDULER_WORKER_ENABLED=true \
AGENTARK_E2E_SCHEDULER_CRON_SCAN_DELAY=1s \
node "${REPOSITORY_ROOT}/agentark-web/tools/e2e-stack.mjs" \
  > "${STACK_LOG}" 2>&1 &
STACK_PID=$!

# 有界等待真实 Gateway、租户和 JWT 会话准备完成。
readonly DEADLINE=$((SECONDS + 300))
until [[ -s "${SESSION_FILE}" ]]; do
  if ! kill -0 "${STACK_PID}" >/dev/null 2>&1; then
    tail -80 "${STACK_LOG}" >&2
    exit 1
  fi
  if (( SECONDS >= DEADLINE )); then
    echo "performance E2E stack readiness timeout" >&2
    exit 1
  fi
  sleep 1
done

# 通过真实 Control API 发布不可变 Snapshot 并创建 Deployment。
node "${REPOSITORY_ROOT}/tools/production/seed-performance.mjs" \
  "${SESSION_FILE}" "${SEED_FILE}"

# 使用真实 SSE 流和 Last-Event-ID=0 测量 Turn 接单至首个持久事件抵达。
node "${REPOSITORY_ROOT}/tools/production/measure-sse.mjs" \
  "${SESSION_FILE}" "${SEED_FILE}" "${SSE_FILE}"

# 执行固定并发和阈值；输出中不打印 JWT 或临时 Secret。
"${K6}" run \
  --env "SESSION_FILE=${SESSION_FILE}" \
  --env "SEED_FILE=${SEED_FILE}" \
  --summary-export "${SUMMARY_FILE}" \
  "${REPOSITORY_ROOT}/tools/production/performance-baseline.js" \
  | tee "${REPORT_FILE}"
