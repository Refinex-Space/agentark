#!/usr/bin/env bash

set -euo pipefail

# Trivy 0.72.0 官方多架构镜像摘要。
readonly TRIVY_IMAGE="aquasec/trivy@sha256:cffe3f5161a47a6823fbd23d985795b3ed72a4c806da4c4df16266c02accdd6f"
# 第一个参数必须是内容寻址的待发布镜像。
readonly TARGET_IMAGE="${1:-}"
# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 将漏洞数据库保存在已忽略 Harness 缓存目录，Maven clean 后仍可复用。
readonly CACHE_DIRECTORY="${REPOSITORY_ROOT}/.agentark/cache/trivy"
# 创建专用缓存目录。
mkdir -p "${CACHE_DIRECTORY}"

if [[ ! "${TARGET_IMAGE}" =~ @sha256:[a-f0-9]{64}$ ]]; then
  echo "image reference must be pinned by sha256 digest" >&2
  exit 2
fi

# 镜像发布前阻断 High/Critical 漏洞和嵌入式 Secret。
docker run --rm \
  --mount "type=bind,src=${CACHE_DIRECTORY},dst=/cache" \
  "${TRIVY_IMAGE}" \
  --cache-dir /cache \
  image \
  --scanners vuln,secret \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --ignore-unfixed=false \
  "${TARGET_IMAGE}"
