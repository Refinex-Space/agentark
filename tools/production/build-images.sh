#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 本地镜像 Tag 可由调用方覆盖；生产发布仍必须转为 Registry Digest。
readonly IMAGE_TAG="${AGENTARK_IMAGE_TAG:-phase22}"
# OCI Revision 使用当前 Commit，不包含工作区内容。
readonly VCS_REF="$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)"
# OCI Build Date 使用 UTC RFC 3339。
readonly BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# 当前项目版本。
readonly IMAGE_VERSION="0.1.0"
# 生产 Service Dockerfile。
readonly SERVICE_DOCKERFILE="${REPOSITORY_ROOT}/deploy/container/Dockerfile.service"

# BuildKit 必须可用，以保留多阶段缓存和固定基础镜像摘要。
docker buildx version >/dev/null

# 构建单个 Java Server 镜像。
build_service() {
  local component="$1"
  local port="$2"
  docker buildx build \
    --file "${SERVICE_DOCKERFILE}" \
    --build-arg "SERVICE_MODULE=agentark-${component}-server" \
    --build-arg "SERVICE_PORT=${port}" \
    --build-arg "VCS_REF=${VCS_REF}" \
    --build-arg "BUILD_DATE=${BUILD_DATE}" \
    --build-arg "IMAGE_VERSION=${IMAGE_VERSION}" \
    --tag "agentark/agentark-${component}-server:${IMAGE_TAG}" \
    --load \
    "${REPOSITORY_ROOT}"
}

# 按固定端口构建四个后端部署单元。
build_service gateway 8080
build_service control 8081
build_service runtime 8082
build_service scheduler 8083

# 构建独立静态 Web 镜像。
docker buildx build \
  --file "${REPOSITORY_ROOT}/deploy/container/Dockerfile.web" \
  --build-arg "VCS_REF=${VCS_REF}" \
  --build-arg "BUILD_DATE=${BUILD_DATE}" \
  --build-arg "IMAGE_VERSION=${IMAGE_VERSION}" \
  --tag "agentark/agentark-web:${IMAGE_TAG}" \
  --load \
  "${REPOSITORY_ROOT}"

# 构建三 Schema 共用但运行时显式选择 Owner Location 的 Flyway 批处理镜像。
docker buildx build \
  --file "${REPOSITORY_ROOT}/deploy/container/Dockerfile.migrations" \
  --build-arg "VCS_REF=${VCS_REF}" \
  --build-arg "BUILD_DATE=${BUILD_DATE}" \
  --build-arg "IMAGE_VERSION=${IMAGE_VERSION}" \
  --tag "agentark/agentark-migrations:${IMAGE_TAG}" \
  --load \
  "${REPOSITORY_ROOT}"

# 输出本地内容 ID；正式发布以 Registry 返回的 RepoDigest、SBOM 和签名为准。
for image in \
  agentark/agentark-gateway-server \
  agentark/agentark-control-server \
  agentark/agentark-runtime-server \
  agentark/agentark-scheduler-server \
  agentark/agentark-web \
  agentark/agentark-migrations; do
  docker image inspect "${image}:${IMAGE_TAG}" \
    --format '{{.RepoTags}} {{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
done

