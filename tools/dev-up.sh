#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
secret_dir="${agentark_root}/deploy/compose/.secrets"
profile=core
prepare_only=false

# 显示只包含非敏感参数的命令用法。
usage() {
    printf '%s\n' "Usage: tools/dev-up.sh [--profile core|rag] [--prepare-only]"
}

# 验证本地必需命令存在，避免在生成一半时静默失败。
require_command() {
    command_name=$1
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        printf '%s\n' "Required command is missing: ${command_name}" >&2
        exit 1
    fi
}

# 首次生成 256 bit 十六进制 Secret，已有文件永不覆盖。
ensure_secret() {
    secret_path=$1
    if [ -f "${secret_path}" ]; then
        return
    fi
    umask 077
    openssl rand -hex 32 >"${secret_path}"
    chmod 600 "${secret_path}"
}

# 限制本地 Secret 为 256 bit 十六进制值，防止换行、Shell 或 SQL 元字符进入初始化流程。
validate_secret() {
    secret_path=$1
    secret_value=$(cat "${secret_path}")
    case "${secret_value}" in
        ''|*[!0123456789abcdefABCDEF]*)
            printf '%s\n' "Local secret must contain exactly 64 hexadecimal characters: ${secret_path}" >&2
            exit 1
            ;;
    esac
    if [ "${#secret_value}" -ne 64 ]; then
        printf '%s\n' "Local secret must contain exactly 64 hexadecimal characters: ${secret_path}" >&2
        exit 1
    fi
    chmod 600 "${secret_path}"
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --profile)
            if [ "$#" -lt 2 ]; then
                usage >&2
                exit 2
            fi
            profile=$2
            shift 2
            ;;
        --prepare-only)
            prepare_only=true
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

if [ "${profile}" != "core" ] && [ "${profile}" != "rag" ]; then
    printf '%s\n' "Unsupported profile: ${profile}" >&2
    exit 2
fi

require_command docker
require_command openssl

# 在生成凭据前连接 Docker，防止无法判断旧 MySQL 卷时误生成新密码。
if ! docker info >/dev/null 2>&1; then
    printf '%s\n' "Docker daemon is unavailable. Start Docker Desktop and retry." >&2
    exit 1
fi

# MySQL 账号密码已持久到数据卷；旧卷存在时严禁补生丢失的文件 Secret。
mysql_secret_missing=false
for mysql_secret_file in \
    mysql-root-password \
    mysql-control-password \
    mysql-runtime-password \
    mysql-scheduler-password; do
    if [ ! -f "${secret_dir}/${mysql_secret_file}" ]; then
        mysql_secret_missing=true
    fi
done
if [ "${mysql_secret_missing}" = "true" ] && docker volume inspect agentark_mysql-data >/dev/null 2>&1; then
    printf '%s\n' \
        "MySQL volume exists but one or more MySQL secret files are missing. Restore the original secrets or explicitly reset local data." >&2
    exit 1
fi

mkdir -p "${secret_dir}"
chmod 700 "${secret_dir}"
ensure_secret "${secret_dir}/mysql-root-password"
ensure_secret "${secret_dir}/mysql-control-password"
ensure_secret "${secret_dir}/mysql-runtime-password"
ensure_secret "${secret_dir}/mysql-scheduler-password"
ensure_secret "${secret_dir}/redis-password"
ensure_secret "${secret_dir}/minio-root-password"

for secret_file in "${secret_dir}"/*-password; do
    validate_secret "${secret_file}"
done

# 只输出配置验证结果，不渲染包含本地路径的完整 Compose 文本。
docker compose -f "${compose_file}" --profile "${profile}" config --quiet

if [ "${prepare_only}" = "true" ]; then
    printf '%s\n' "Local secrets prepared and Compose profile validated: ${profile}"
    exit 0
fi

# 构建四个可执行 JAR，单元测试由验收命令单独运行。
"${agentark_root}/mvnw" \
    -f "${agentark_root}/pom.xml" \
    -pl agentark-services/agentark-gateway-server,agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server \
    -am \
    -DskipTests \
    package

# 构建本地 Server 镜像并等待全部健康检查通过。
docker compose -f "${compose_file}" --profile "${profile}" up --detach --build --wait --wait-timeout 240

"${script_dir}/dev-status.sh"
