#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Required command is missing: docker" >&2
    exit 1
fi

# 同时覆盖 Core 和 RAG Profile；故意不使用 --volumes，保留本地持久数据。
docker compose -f "${compose_file}" --profile core --profile rag down --remove-orphans
