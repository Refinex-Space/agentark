#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
compose_identity_file="${agentark_root}/deploy/compose/docker-compose.identity.yml"
secret_dir="${agentark_root}/deploy/compose/.secrets"

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Required command is missing: docker" >&2
    exit 1
fi

# 同时覆盖 Core、RAG 和已准备的 Identity Profile；故意不使用 --volumes，保留本地持久数据。
if [ -f "${secret_dir}/mysql-identity-password" ] \
    && [ -f "${secret_dir}/identity-user-password" ] \
    && [ -f "${secret_dir}/identity-password-pepper" ] \
    && [ -f "${secret_dir}/identity-signing-private-key.pem" ]; then
    docker compose \
        -f "${compose_file}" \
        -f "${compose_identity_file}" \
        --profile core \
        --profile rag \
        down --remove-orphans
else
    docker compose -f "${compose_file}" --profile core --profile rag down --remove-orphans
fi
