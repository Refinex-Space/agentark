#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 固定 Chart 路径。
readonly CHART_DIRECTORY="${REPOSITORY_ROOT}/deploy/helm/agentark"
# 可选第一参数为待验证 Values；默认验证生产示例。
readonly VALUES_FILE="${1:-${CHART_DIRECTORY}/values-production.example.yaml}"
# 使用仓库内固定工具，避免依赖系统漂移。
readonly HELM="${REPOSITORY_ROOT}/.agentark/bin/helm"
# Kubernetes OpenAPI Schema 校验器。
readonly KUBECONFORM="${REPOSITORY_ROOT}/.agentark/bin/kubeconform"

# 缺少固定工具时先安全引导安装。
if [[ ! -x "${HELM}" || ! -x "${KUBECONFORM}" ]]; then
  "${REPOSITORY_ROOT}/tools/production/bootstrap-tools.sh"
fi

# Values 必须是仓库内或调用方明确给出的普通文件。
if [[ ! -f "${VALUES_FILE}" || -L "${VALUES_FILE}" ]]; then
  echo "values file must be a regular non-symlink file" >&2
  exit 2
fi

# 临时目录由 mktemp 创建并在退出时回收。
readonly TEMPORARY_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TEMPORARY_DIRECTORY}"' EXIT
# 保存默认和生产渲染结果供静态门禁使用。
readonly DEFAULT_RENDERED="${TEMPORARY_DIRECTORY}/default.yaml"
readonly PRODUCTION_RENDERED="${TEMPORARY_DIRECTORY}/production.yaml"

# Helm Lint 同时验证 Values Schema 与模板语法。
"${HELM}" lint "${CHART_DIRECTORY}"
# 默认渲染必须符合用户给出的验收命令。
"${HELM}" template agentark "${CHART_DIRECTORY}" \
  --values "${CHART_DIRECTORY}/values.yaml" > "${DEFAULT_RENDERED}"
# 生产 Values 必须触发额外失败关闭门禁。
"${HELM}" template agentark "${CHART_DIRECTORY}" \
  --values "${VALUES_FILE}" > "${PRODUCTION_RENDERED}"

# 对内置 Kubernetes API 执行严格 Schema 校验；可选 ExternalSecret/KEDA 默认不渲染。
"${KUBECONFORM}" -strict -summary -kubernetes-version 1.34.0 \
  "${DEFAULT_RENDERED}" "${PRODUCTION_RENDERED}"

# 证明关键生产失败关闭条件确实拒绝渲染，避免只验证安全示例的假阳性。
expect_production_rejection() {
  local label="$1"
  shift
  if "${HELM}" template agentark "${CHART_DIRECTORY}" \
    --values "${VALUES_FILE}" "$@" >/dev/null 2>&1; then
    echo "production validation did not reject ${label}" >&2
    exit 1
  fi
}
# Redis 明文连接必须失败。
expect_production_rejection "Redis without TLS" \
  --set global.external.redis.tls=false
# HTTP Built-in Identity Issuer 必须失败。
expect_production_rejection "HTTP built-in identity issuer" \
  --set-string global.external.identity.issuerUri=http://agentark.example.com
# 未知身份模式必须失败，避免隐式回退到弱认证。
expect_production_rejection "unknown identity mode" \
  --set-string global.external.identity.mode=unknown
# 全网出口必须失败。
expect_production_rejection "world-open egress" \
  --set-string 'global.externalEgressCidrs[0]=0.0.0.0/0'
# 未启用生产 Secret Resolver 必须失败。
expect_production_rejection "disabled Vault resolver" \
  --set secretManagement.vault.enabled=false

# 默认拓扑不得引入禁止的额外平台依赖。
if rg -ni 'nacos|consul|istio|linkerd|kafka|elasticsearch|neo4j|aistio|golang' "${DEFAULT_RENDERED}"; then
  echo "default chart contains a forbidden platform dependency" >&2
  exit 1
fi
# Chart 不生成含值的 Kubernetes Secret。
if rg -n '^kind: Secret$' "${DEFAULT_RENDERED}" "${PRODUCTION_RENDERED}"; then
  echo "chart must not render plaintext Kubernetes Secret resources" >&2
  exit 1
fi
# 生产镜像必须全部按 Digest 渲染。
if rg -n '^\s*image:\s+[^@\s]+:[^@\s]+$' "${PRODUCTION_RENDERED}"; then
  echo "production chart rendered an image tag" >&2
  exit 1
fi
# 多副本 Runtime/Gateway 明确不使用 Session Sticky。
test "$(rg -c '^  sessionAffinity: None$' "${PRODUCTION_RENDERED}")" -ge 2
# 所有五个应用 Deployment 均使用只读根和非 Root。
test "$(rg -c '^\s*readOnlyRootFilesystem: true$' "${PRODUCTION_RENDERED}")" -ge 5
test "$(rg -c '^\s*runAsNonRoot: true$' "${PRODUCTION_RENDERED}")" -ge 5

# 输出可审计的资源类别统计。
rg '^kind:' "${PRODUCTION_RENDERED}" | sort | uniq -c
