#!/usr/bin/env bash

set -euo pipefail

# 根据脚本自身位置解析仓库根目录，允许从任意工作目录调用。
readonly FORMAT_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly FORMAT_REPOSITORY_ROOT="$(cd -- "${FORMAT_SCRIPT_DIR}/../.." && pwd)"

cd "${FORMAT_REPOSITORY_ROOT}"

if [[ ! -x "./mvnw" || ! -f "./pom.xml" ]]; then
  printf 'format failed: AgentArk Maven root is incomplete: %s\n' "${FORMAT_REPOSITORY_ROOT}" >&2
  exit 1
fi

# Spotless 是仓库 Java 格式的唯一实现，禁止在脚本中复制另一套格式规则。
exec ./mvnw spotless:apply
