#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录，避免调用方当前目录影响契约路径。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 0.1.0 是首个冻结的公共与内部契约集合。
readonly BASELINE="${REPOSITORY_ROOT}/contracts/baselines/0.1.0.sha256"
# 临时清单只在本次校验进程中存在。
readonly ACTUAL="$(mktemp)"

# 无论成功或失败都清理当前脚本创建的单个临时文件。
cleanup() {
  rm -f "${ACTUAL}"
}
trap cleanup EXIT

cd "${REPOSITORY_ROOT}"

# 按稳定路径顺序计算全部版本化 Contract；Baseline 文件本身不参与摘要。
find contracts -type f \( -name '*.yaml' -o -name '*.json' \) \
  -not -path 'contracts/baselines/*' -print \
  | LC_ALL=C sort \
  | while IFS= read -r file; do
      shasum -a 256 "${file}"
    done >"${ACTUAL}"

# 任何增删改都必须通过新版本契约与兼容评审显式更新基线。
diff -u "${BASELINE}" "${ACTUAL}"
echo "contract baseline 0.1.0 verified"
