#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
compose_identity_file="${agentark_root}/deploy/compose/docker-compose.identity.yml"
secret_dir="${agentark_root}/deploy/compose/.secrets"
identity_enabled=true

# 显示验证命令用法；默认与 dev-up.sh 一致验证内置账号身份。
usage() {
    printf '%s\n' "Usage: tools/verify-core.sh [--identity|--no-identity]"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --identity)
            identity_enabled=true
            shift
            ;;
        --no-identity)
            identity_enabled=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
done

# 使用与当前身份模式一致的 Compose Project 视图执行只读验证命令。
compose() {
    if [ "${identity_enabled}" = "true" ]; then
        docker compose \
            -f "${compose_file}" \
            -f "${compose_identity_file}" \
            --profile core \
            "$@"
    else
        docker compose -f "${compose_file}" --profile core "$@"
    fi
}

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
    container_id=$(compose ps --quiet "${service_name}")
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
    info_policy=$3
    base_url="http://127.0.0.1:${service_port}/actuator"
    for health_path in health health/liveness health/readiness; do
        response=$(curl --fail --silent --show-error "${base_url}/${health_path}")
        if ! printf '%s' "${response}" | grep -F '"status":"UP"' >/dev/null; then
            printf '%s\n' "${service_name}: ${health_path} is not UP" >&2
            exit 1
        fi
    done
    if [ "${info_policy}" = "protected" ]; then
        info_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${base_url}/info")
        if [ "${info_status}" != "401" ] && [ "${info_status}" != "403" ]; then
            printf '%s\n' "${service_name}: protected info returned ${info_status}" >&2
            exit 1
        fi
        info_result=PROTECTED
    else
        info_response=$(curl --fail --silent --show-error "${base_url}/info")
        if ! printf '%s' "${info_response}" | grep -F '"build"' >/dev/null; then
            printf '%s\n' "${service_name}: build info is missing" >&2
            exit 1
        fi
        info_result=SAFE
    fi
    env_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${base_url}/env")
    if [ "${env_status}" != "401" ] \
        && [ "${env_status}" != "403" ] \
        && [ "${env_status}" != "404" ]; then
        printf '%s\n' "${service_name}: actuator env exposure returned ${env_status}" >&2
        exit 1
    fi
    printf '%s\n' "${service_name}: health=UP info=${info_result} env=HIDDEN"
}

# 验证账号可访问自身 Schema、达到所属最新 Flyway/业务表基线，并拒绝两个非归属 Schema。
verify_mysql_account() {
    account_name=$1
    own_schema=$2
    denied_schema_one=$3
    denied_schema_two=$4
    password_file=$5
    expected_version=$6
    expected_table_count=$7
    account_password=$(cat "${password_file}")
    own_result=$(
        MYSQL_PWD="${account_password}" \
            compose exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse 'SELECT DATABASE()' "${own_schema}"
    )
    if [ "${own_result}" != "${own_schema}" ]; then
        printf '%s\n' "${account_name}: own-schema check failed" >&2
        exit 1
    fi
    migration_result=$(
        MYSQL_PWD="${account_password}" \
            compose exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse \
            "SELECT CONCAT(version, ':', success) FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" \
            "${own_schema}"
    )
    if [ "${migration_result}" != "${expected_version}:1" ]; then
        printf '%s\n' "${account_name}: expected successful Flyway V${expected_version}, got ${migration_result:-missing}" >&2
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
    if [ "${business_table_count}" != "${expected_table_count}" ]; then
        printf '%s\n' "${account_name}: expected ${expected_table_count} business table(s), got ${business_table_count}" >&2
        exit 1
    fi
    for denied_schema in "${denied_schema_one}" "${denied_schema_two}"; do
        if MYSQL_PWD="${account_password}" \
            compose exec -T \
            -e MYSQL_PWD \
            mysql mysql -u"${account_name}" -Nse "USE ${denied_schema}" >/dev/null 2>&1; then
            printf '%s\n' "${account_name}: unexpectedly accessed ${denied_schema}" >&2
            exit 1
        fi
    done
    printf '%s\n' "${account_name}: own=${own_schema} migration=V${expected_version} business_tables=${expected_table_count} cross=DENIED"
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

if [ "${identity_enabled}" = "true" ]; then
    for secret_file in \
        mysql-identity-password \
        identity-user-password \
        identity-password-pepper \
        identity-signing-private-key.pem; do
        if [ ! -r "${secret_dir}/${secret_file}" ]; then
            printf '%s\n' "Local identity secret is missing; run ./tools/dev-up.sh --prepare-only: ${secret_file}" >&2
            exit 1
        fi
    done
fi

for service_name in mysql redis minio gateway control runtime scheduler; do
    verify_container "${service_name}"
done
verify_server gateway 8080 protected
verify_server control 8081 safe
verify_server runtime 8082 safe
verify_server scheduler 8083 safe
if [ "${identity_enabled}" = "true" ]; then
    session_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
        http://127.0.0.1:8080/api/v1/auth/session)
    if [ "${session_status}" != "200" ]; then
        printf '%s\n' "gateway: anonymous BFF session returned ${session_status}" >&2
        exit 1
    fi
    session_projection=$(curl --fail --silent --show-error \
        http://127.0.0.1:8080/api/v1/auth/session)
    if ! printf '%s' "${session_projection}" | grep -F '"loginMode":"PASSWORD"' >/dev/null; then
        printf '%s\n' "identity: Gateway session is not in PASSWORD mode" >&2
        exit 1
    fi
    jwk_projection=$(curl --fail --silent --show-error \
        http://127.0.0.1:8080/api/v1/auth/jwks)
    if ! printf '%s' "${jwk_projection}" | grep -F '"kty":"RSA"' >/dev/null; then
        printf '%s\n' "identity: RSA JWK is missing" >&2
        exit 1
    fi
    unset session_projection jwk_projection
    printf '%s\n' "identity: mysql=READY gateway_session=ANONYMOUS login_mode=PASSWORD jwk=RS256"
else
    printf '%s\n' "identity: disabled"
fi

verify_mysql_account \
    agentark_control agentark_control agentark_runtime agentark_scheduler \
    "${secret_dir}/mysql-control-password" 8 69
verify_mysql_account \
    agentark_runtime agentark_runtime agentark_control agentark_scheduler \
    "${secret_dir}/mysql-runtime-password" 3 13
verify_mysql_account \
    agentark_scheduler agentark_scheduler agentark_control agentark_runtime \
    "${secret_dir}/mysql-scheduler-password" 3 9

if [ "${identity_enabled}" = "true" ]; then
    verify_mysql_account \
        agentark_identity agentark_identity agentark_control agentark_runtime \
        "${secret_dir}/mysql-identity-password" 1 13
    identity_password=$(cat "${secret_dir}/mysql-identity-password")
    if MYSQL_PWD="${identity_password}" compose exec -T -e MYSQL_PWD mysql mysql \
        -uagentark_identity -Nse "USE agentark_scheduler" >/dev/null 2>&1; then
        printf '%s\n' "agentark_identity: unexpectedly accessed agentark_scheduler" >&2
        exit 1
    fi
    unset identity_password
fi

if [ -n "$(compose ps --quiet qdrant)" ]; then
    printf '%s\n' "qdrant: unexpectedly enabled in Core profile" >&2
    exit 1
fi
printf '%s\n' "qdrant: disabled in Core profile"
