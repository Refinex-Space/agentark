#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
secret_dir="${agentark_root}/deploy/compose/.secrets"

# 验证命令存在，失败时给出可执行的本地修复方向。
require_command() {
    command_name=$1
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        printf '%s\n' "Required command is missing: ${command_name}" >&2
        exit 1
    fi
}

# 确认指定 Compose 服务存在、正在运行且健康。
verify_container() {
    service_name=$1
    container_id=$(docker compose -f "${compose_file}" --profile core ps --quiet "${service_name}")
    if [ -z "${container_id}" ]; then
        printf '%s\n' "${service_name}: container is missing" >&2
        exit 1
    fi
    health_status=$(docker inspect --format '{{.State.Health.Status}}' "${container_id}")
    if [ "${health_status}" != "healthy" ]; then
        printf '%s\n' "${service_name}: container health is ${health_status}" >&2
        exit 1
    fi
    printf '%s\n' "${service_name}: healthy"
}

# 检查四个 Server 的健康分组、Build Info 与敏感 Actuator 隐藏边界。
verify_server() {
    service_name=$1
    service_port=$2
    base_url="http://127.0.0.1:${service_port}/actuator"
    for health_path in health health/liveness health/readiness; do
        response=$(curl --fail --silent --show-error "${base_url}/${health_path}")
        if ! printf '%s' "${response}" | grep -F '"status":"UP"' >/dev/null; then
            printf '%s\n' "${service_name}: ${health_path} is not UP" >&2
            exit 1
        fi
    done
    info_response=$(curl --fail --silent --show-error "${base_url}/info")
    if ! printf '%s' "${info_response}" | grep -F '"build"' >/dev/null; then
        printf '%s\n' "${service_name}: build info is missing" >&2
        exit 1
    fi
    env_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${base_url}/env")
    if [ "${env_status}" != "404" ]; then
        printf '%s\n' "${service_name}: actuator env exposure returned ${env_status}" >&2
        exit 1
    fi
    printf '%s\n' "${service_name}: health=UP info=SAFE env=HIDDEN"
}

# 验证账号可访问自身 Schema、Flyway V1 成功且无业务表，并拒绝两个非归属 Schema。
verify_mysql_account() {
    account_name=$1
    own_schema=$2
    denied_schema_one=$3
    denied_schema_two=$4
    password_file=$5
    account_password=$(cat "${password_file}")
    own_result=$(
        MYSQL_PWD="${account_password}" \
            docker compose -f "${compose_file}" --profile core exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse 'SELECT DATABASE()' "${own_schema}"
    )
    if [ "${own_result}" != "${own_schema}" ]; then
        printf '%s\n' "${account_name}: own-schema check failed" >&2
        exit 1
    fi
    migration_result=$(
        MYSQL_PWD="${account_password}" \
            docker compose -f "${compose_file}" --profile core exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse \
            "SELECT CONCAT(version, ':', success) FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" \
            "${own_schema}"
    )
    if [ "${migration_result}" != "1:1" ]; then
        printf '%s\n' "${account_name}: expected successful Flyway V1, got ${migration_result:-missing}" >&2
        exit 1
    fi
    business_table_count=$(
        MYSQL_PWD="${account_password}" \
            docker compose -f "${compose_file}" --profile core exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse \
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'" \
            "${own_schema}"
    )
    if [ "${business_table_count}" != "0" ]; then
        printf '%s\n' "${account_name}: Phase 06 unexpectedly created ${business_table_count} business table(s)" >&2
        exit 1
    fi
    for denied_schema in "${denied_schema_one}" "${denied_schema_two}"; do
        if MYSQL_PWD="${account_password}" \
            docker compose -f "${compose_file}" --profile core exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse "USE ${denied_schema}" >/dev/null 2>&1; then
            printf '%s\n' "${account_name}: unexpectedly accessed ${denied_schema}" >&2
            exit 1
        fi
    done
    printf '%s\n' "${account_name}: own=${own_schema} migration=V1 business_tables=0 cross=DENIED"
    unset account_password
}

require_command docker
require_command curl

for secret_file in \
    mysql-control-password \
    mysql-runtime-password \
    mysql-scheduler-password; do
    if [ ! -r "${secret_dir}/${secret_file}" ]; then
        printf '%s\n' "Local secret is missing; run ./tools/dev-up.sh --prepare-only: ${secret_file}" >&2
        exit 1
    fi
done

for service_name in mysql redis minio gateway control runtime scheduler; do
    verify_container "${service_name}"
done

verify_server gateway 8080
verify_server control 8081
verify_server runtime 8082
verify_server scheduler 8083

verify_mysql_account \
    agentark_control agentark_control agentark_runtime agentark_scheduler \
    "${secret_dir}/mysql-control-password"
verify_mysql_account \
    agentark_runtime agentark_runtime agentark_control agentark_scheduler \
    "${secret_dir}/mysql-runtime-password"
verify_mysql_account \
    agentark_scheduler agentark_scheduler agentark_control agentark_runtime \
    "${secret_dir}/mysql-scheduler-password"

if [ -n "$(docker compose -f "${compose_file}" --profile core ps --quiet qdrant)" ]; then
    printf '%s\n' "qdrant: unexpectedly enabled in Core profile" >&2
    exit 1
fi
printf '%s\n' "qdrant: disabled in Core profile"
