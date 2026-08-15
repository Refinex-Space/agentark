#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
health_failed=false

# 逐个检查服务脱敏健康端点，既不打印 Secret 也不隐藏失败。
check_health() {
    service_name=$1
    service_url=$2
    if curl --fail --silent --show-error "${service_url}" >/dev/null; then
        printf '%s\n' "${service_name}: UP"
    else
        printf '%s\n' "${service_name}: DOWN" >&2
        health_failed=true
    fi
}

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Required command is missing: docker" >&2
    exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
    printf '%s\n' "Required command is missing: curl" >&2
    exit 1
fi

# 同时展示 Core/RAG 容器，未启动的可选服务不会被创建。
docker compose -f "${compose_file}" --profile core --profile rag ps

check_health gateway http://127.0.0.1:8080/actuator/health
check_health control http://127.0.0.1:8081/actuator/health
check_health runtime http://127.0.0.1:8082/actuator/health
check_health scheduler http://127.0.0.1:8083/actuator/health

if [ "${health_failed}" = "true" ]; then
    exit 1
fi
