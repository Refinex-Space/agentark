#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
agentark_root=$(CDPATH= cd -- "${script_dir}/.." && pwd)
compose_file="${agentark_root}/deploy/compose/docker-compose.yml"
secret_dir="${agentark_root}/deploy/compose/.secrets"

# 只从权限受限文件读取现有 MySQL 凭据，不输出任何值。
root_password=$(cat "${secret_dir}/mysql-root-password")
identity_password=$(cat "${secret_dir}/mysql-identity-password")

# 对已有 MySQL 命名卷幂等补齐 Identity Schema 和最小权限账号。
MYSQL_PWD="${root_password}" docker compose -f "${compose_file}" --profile core exec -T \
    -e MYSQL_PWD mysql mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS agentark_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'agentark_identity'@'%' IDENTIFIED BY '${identity_password}';
ALTER USER 'agentark_identity'@'%' IDENTIFIED BY '${identity_password}';
GRANT ALL PRIVILEGES ON agentark_identity.* TO 'agentark_identity'@'%';
FLUSH PRIVILEGES;
SQL

unset root_password identity_password
printf '%s\n' "Identity MySQL schema and account are ready"
