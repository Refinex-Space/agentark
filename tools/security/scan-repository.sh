#!/usr/bin/env bash

set -euo pipefail

# Trivy 0.72.0 官方多架构镜像摘要；使用摘要避免标签被替换。
readonly TRIVY_IMAGE="aquasec/trivy@sha256:cffe3f5161a47a6823fbd23d985795b3ed72a4c806da4c4df16266c02accdd6f"
# 根据脚本位置解析仓库根目录，不依赖调用方当前目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 复用 Maven 已解析依赖，避免扫描器并发打满 Maven Central 限流。
readonly MAVEN_REPOSITORY="$(${REPOSITORY_ROOT}/mvnw -q help:evaluate \
  -Dexpression=settings.localRepository -DforceStdout)"
# 将漏洞数据库保存在已忽略 Harness 缓存目录，Maven clean 后仍可复用。
readonly CACHE_DIRECTORY="${REPOSITORY_ROOT}/.agentark/cache/trivy"
# 创建专用缓存目录，不写入受版本控制源码。
mkdir -p "${CACHE_DIRECTORY}"

# Docker 必须可用，避免把未执行扫描误报为成功。
docker version >/dev/null

# 先从 Maven 实际解析图生成 CycloneDX，避免静态 POM 扫描误判 BOM 覆盖和 Exclusion。
"${REPOSITORY_ROOT}/mvnw" -B -ntp -DskipTests cyclonedx:makeAggregateBom

# 扫描源码 Secret 与 IaC；依赖漏洞由后续实际解析的 SBOM 和 Web Lockfile 扫描负责。
docker run --rm \
  --mount "type=bind,src=${REPOSITORY_ROOT},dst=/workspace,readonly" \
  --mount "type=bind,src=${MAVEN_REPOSITORY},dst=/root/.m2/repository,readonly" \
  --mount "type=bind,src=${CACHE_DIRECTORY},dst=/cache" \
  "${TRIVY_IMAGE}" \
  --cache-dir /cache \
  --config /workspace/.trivy.yaml \
  --scanners secret,misconfig \
  fs /workspace

# 扫描 Maven 实际解析后的生产/运行依赖 CycloneDX，不读取会误报的原始聚合 POM。
docker run --rm \
  --mount "type=bind,src=${REPOSITORY_ROOT},dst=/workspace,readonly" \
  --mount "type=bind,src=${CACHE_DIRECTORY},dst=/cache" \
  "${TRIVY_IMAGE}" \
  --cache-dir /cache \
  sbom \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --ignore-unfixed=false \
  /workspace/target/bom.json

# 单独按 pnpm Lockfile 扫描 Web 生产依赖漏洞。
docker run --rm \
  --mount "type=bind,src=${REPOSITORY_ROOT},dst=/workspace,readonly" \
  --mount "type=bind,src=${CACHE_DIRECTORY},dst=/cache" \
  "${TRIVY_IMAGE}" \
  --cache-dir /cache \
  fs \
  --scanners vuln \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --ignore-unfixed=false \
  /workspace/agentark-web
