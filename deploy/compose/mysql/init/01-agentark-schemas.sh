#!/bin/sh

set -eu

# 仅从 Docker Secret 文件读取初始化凭据，禁止向日志输出。
mysql_root_password=$(cat /run/secrets/mysql_root_password)
mysql_control_password=$(cat /run/secrets/mysql_control_password)
mysql_runtime_password=$(cat /run/secrets/mysql_runtime_password)
mysql_scheduler_password=$(cat /run/secrets/mysql_scheduler_password)
mysql_identity_password=""
if [ -f /run/secrets/mysql_identity_password ]; then
    mysql_identity_password=$(cat /run/secrets/mysql_identity_password)
fi

# 密码由 dev-up.sh 生成为纯十六进制字符，可安全放入首次初始化 SQL。
MYSQL_PWD="${mysql_root_password}" mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS agentark_control CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS agentark_runtime CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS agentark_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'agentark_control'@'%' IDENTIFIED BY '${mysql_control_password}';
ALTER USER 'agentark_control'@'%' IDENTIFIED BY '${mysql_control_password}';
GRANT ALL PRIVILEGES ON agentark_control.* TO 'agentark_control'@'%';

CREATE USER IF NOT EXISTS 'agentark_runtime'@'%' IDENTIFIED BY '${mysql_runtime_password}';
ALTER USER 'agentark_runtime'@'%' IDENTIFIED BY '${mysql_runtime_password}';
GRANT ALL PRIVILEGES ON agentark_runtime.* TO 'agentark_runtime'@'%';

CREATE USER IF NOT EXISTS 'agentark_scheduler'@'%' IDENTIFIED BY '${mysql_scheduler_password}';
ALTER USER 'agentark_scheduler'@'%' IDENTIFIED BY '${mysql_scheduler_password}';
GRANT ALL PRIVILEGES ON agentark_scheduler.* TO 'agentark_scheduler'@'%';

$(if [ -n "${mysql_identity_password}" ]; then cat <<IDENTITY_SQL
CREATE DATABASE IF NOT EXISTS agentark_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'agentark_identity'@'%' IDENTIFIED BY '${mysql_identity_password}';
ALTER USER 'agentark_identity'@'%' IDENTIFIED BY '${mysql_identity_password}';
GRANT ALL PRIVILEGES ON agentark_identity.* TO 'agentark_identity'@'%';
IDENTITY_SQL
fi)

FLUSH PRIVILEGES;
SQL
