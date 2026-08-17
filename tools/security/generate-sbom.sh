#!/usr/bin/env bash

set -euo pipefail

# Trivy 0.72.0 官方多架构镜像摘要。
readonly TRIVY_IMAGE="aquasec/trivy@sha256:cffe3f5161a47a6823fbd23d985795b3ed72a4c806da4c4df16266c02accdd6f"
# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 输出目录由调用方指定，默认写入已忽略的 target 目录。
readonly OUTPUT_REQUEST="${1:-${REPOSITORY_ROOT}/target/security}"
# 创建输出目录，不修改受版本控制文件。
mkdir -p "${OUTPUT_REQUEST}"
# Docker Bind Mount 要求绝对路径，因此在目录创建后规范化。
readonly OUTPUT_DIRECTORY="$(cd "${OUTPUT_REQUEST}" && pwd)"
# 固定 CycloneDX JSON 输出文件。
readonly OUTPUT_FILE="${OUTPUT_DIRECTORY}/agentark-repository.cdx.json"

# 生成仓库级 CycloneDX SBOM，补充 Maven 聚合 SBOM 对前端和 IaC 的覆盖。
docker run --rm \
  --mount "type=bind,src=${REPOSITORY_ROOT},dst=/workspace,readonly" \
  --mount "type=bind,src=${OUTPUT_DIRECTORY},dst=/output" \
  "${TRIVY_IMAGE}" \
  fs \
  --skip-dirs /workspace/.agentark \
  --skip-dirs /workspace/agentark-web/node_modules \
  --skip-dirs /workspace/agentark-web/dist \
  --skip-dirs /workspace/target \
  --skip-files "**/pom.xml" \
  --format cyclonedx \
  --output /output/agentark-repository.cdx.json \
  /workspace

echo "${OUTPUT_FILE}"
