#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 演练证据写入已忽略目录，不提交运行数据或临时凭据。
readonly EVIDENCE_DIRECTORY="${REPOSITORY_ROOT}/.agentark/evidence/phase22"
# 所有备份、数据目录和 Secret 都限制在系统临时目录。
readonly TEMPORARY_DIRECTORY="$(mktemp -d)"
# 容器名称带当前进程标识，避免触碰用户现有容器。
readonly RESOURCE_PREFIX="agentark-phase22-restore-$$"
# 固定 MySQL 演练版本。
readonly MYSQL_IMAGE="mysql:8.4.11"
# 固定独立 Binlog Reader 镜像和内容摘要；Server 最小镜像本身不含 mysqlbinlog。
readonly MYSQL_BINLOG_IMAGE="percona/percona-server:8.4@sha256:01cf1439241e11c5d49312db1b18ee01a5947869c1a1dec18417d88ffaeb5717"
# 固定 Qdrant 演练版本。
readonly QDRANT_IMAGE="qdrant/qdrant:v1.18.3"
# 固定 Redis 演练版本。
readonly REDIS_IMAGE="redis:8.10.0"
# 记录实际创建的精确容器名称。
readonly -a CONTAINERS=(
  "${RESOURCE_PREFIX}-mysql-source"
  "${RESOURCE_PREFIX}-mysql-target"
  "${RESOURCE_PREFIX}-qdrant-source"
  "${RESOURCE_PREFIX}-qdrant-target"
  "${RESOURCE_PREFIX}-redis-source"
  "${RESOURCE_PREFIX}-redis-target"
)

# 无论成功或失败都只删除本脚本创建的容器与临时目录。
cleanup() {
  local status=$?
  for container in "${CONTAINERS[@]}"; do
    docker rm --force "${container}" >/dev/null 2>&1 || true
  done
  rm -rf "${TEMPORARY_DIRECTORY}"
  exit "${status}"
}
trap cleanup EXIT

# 验证演练依赖，缺失时立即失败而不是生成部分备份。
for command in docker curl jq openssl shasum tar git; do
  command -v "${command}" >/dev/null || {
    echo "required command is missing: ${command}" >&2
    exit 1
  }
done
docker info >/dev/null

# 生成只供临时容器文件挂载的随机凭据。
umask 077
openssl rand -hex 32 | tr -d '\n' > "${TEMPORARY_DIRECTORY}/mysql-root-password"
openssl rand -hex 32 | tr -d '\n' > "${TEMPORARY_DIRECTORY}/redis-password"
chmod 0600 "${TEMPORARY_DIRECTORY}"/*-password
mkdir -p \
  "${TEMPORARY_DIRECTORY}/mysql-source" \
  "${TEMPORARY_DIRECTORY}/mysql-target" \
  "${TEMPORARY_DIRECTORY}/objects/source/project-a" \
  "${TEMPORARY_DIRECTORY}/objects/target" \
  "${TEMPORARY_DIRECTORY}/qdrant-source" \
  "${TEMPORARY_DIRECTORY}/qdrant-target"

# 等待指定 MySQL 容器接受本地 root 连接。
wait_mysql() {
  local container="$1"
  local deadline=$((SECONDS + 120))
  until docker exec "${container}" /bin/sh -c \
      'MYSQL_PWD=$(cat /run/secrets/root) mysqladmin ping --host=127.0.0.1 --user=root --silent' \
      >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "MySQL readiness timeout: ${container}" >&2
      exit 1
    fi
    sleep 1
  done
}

# 通过标准输入执行 SQL，避免 SQL 和凭据出现在进程参数中。
mysql_sql() {
  local container="$1"
  docker exec --interactive "${container}" /bin/sh -c \
    'MYSQL_PWD=$(cat /run/secrets/root) mysql --user=root --batch --skip-column-names'
}

# 启动 Source MySQL，并开启 ROW Binlog 供实际 PITR 回放。
docker run --detach --name "${RESOURCE_PREFIX}-mysql-source" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/mysql-root-password,dst=/run/secrets/root,readonly" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/mysql-source,dst=/var/lib/mysql" \
  --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root \
  "${MYSQL_IMAGE}" \
  --server-id=101 \
  --log-bin=/var/lib/mysql/binlog \
  --binlog-format=ROW \
  --default-time-zone=+00:00 >/dev/null
wait_mysql "${RESOURCE_PREFIX}-mysql-source"
readonly MYSQL_STARTED="$(date +%s)"

# 创建基线业务事实并切换 Binlog，使全量备份后的增量边界确定。
mysql_sql "${RESOURCE_PREFIX}-mysql-source" <<'SQL'
CREATE DATABASE agentark_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE agentark_restore.recovery_probe (
  id BIGINT NOT NULL PRIMARY KEY,
  payload VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
INSERT INTO agentark_restore.recovery_probe(id, payload) VALUES (1, 'full-backup');
FLUSH BINARY LOGS;
SQL

# 生成事务一致的逻辑全量备份，并记录其 Source Binlog 坐标。
docker exec "${RESOURCE_PREFIX}-mysql-source" /bin/sh -c \
  'MYSQL_PWD=$(cat /run/secrets/root) mysqldump --user=root --single-transaction --source-data=2 --routines --events --triggers --databases agentark_restore' \
  > "${TEMPORARY_DIRECTORY}/mysql-full.sql"
readonly BINLOG_FILE="$(sed -n "s/.*SOURCE_LOG_FILE='\([^']*\)'.*/\1/p" "${TEMPORARY_DIRECTORY}/mysql-full.sql" | head -1)"
readonly BINLOG_POSITION="$(sed -n "s/.*SOURCE_LOG_POS=\([0-9][0-9]*\).*/\1/p" "${TEMPORARY_DIRECTORY}/mysql-full.sql" | head -1)"
test -n "${BINLOG_FILE}"
test -n "${BINLOG_POSITION}"

# 第二条事实属于 PITR 目标，第三条事实位于下一 Binlog，必须不被恢复。
mysql_sql "${RESOURCE_PREFIX}-mysql-source" <<'SQL'
INSERT INTO agentark_restore.recovery_probe(id, payload) VALUES (2, 'pitr-target');
FLUSH BINARY LOGS;
INSERT INTO agentark_restore.recovery_probe(id, payload) VALUES (3, 'after-pitr-target');
SQL

# 启动空 Target MySQL，先恢复全量备份，再从精确坐标回放关闭的 Binlog。
docker run --detach --name "${RESOURCE_PREFIX}-mysql-target" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/mysql-root-password,dst=/run/secrets/root,readonly" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/mysql-target,dst=/var/lib/mysql" \
  --env MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root \
  "${MYSQL_IMAGE}" --server-id=102 --default-time-zone=+00:00 >/dev/null
wait_mysql "${RESOURCE_PREFIX}-mysql-target"
mysql_sql "${RESOURCE_PREFIX}-mysql-target" < "${TEMPORARY_DIRECTORY}/mysql-full.sql"
docker run --rm --interactive \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/mysql-source,dst=/source,readonly" \
  --entrypoint /usr/bin/mysqlbinlog \
  "${MYSQL_BINLOG_IMAGE}" \
  --start-position="${BINLOG_POSITION}" "/source/${BINLOG_FILE}" \
  | mysql_sql "${RESOURCE_PREFIX}-mysql-target"
readonly MYSQL_ROWS="$(printf '%s\n' \
  'SELECT GROUP_CONCAT(CONCAT(id, ":", payload) ORDER BY id SEPARATOR ",") FROM agentark_restore.recovery_probe;' \
  | mysql_sql "${RESOURCE_PREFIX}-mysql-target" | tail -1)"
test "${MYSQL_ROWS}" = "1:full-backup,2:pitr-target"
readonly MYSQL_FINISHED="$(date +%s)"

# 对象备份使用不可变内容 Hash 与归档 Hash 双重校验，不依赖目录名授权。
printf '%s' 'agentark-object-alpha' > "${TEMPORARY_DIRECTORY}/objects/source/project-a/alpha.bin"
printf '%s' 'agentark-object-beta' > "${TEMPORARY_DIRECTORY}/objects/source/project-a/beta.bin"
(cd "${TEMPORARY_DIRECTORY}/objects/source" && shasum -a 256 project-a/*.bin > object-manifest.sha256)
tar -C "${TEMPORARY_DIRECTORY}/objects/source" -czf "${TEMPORARY_DIRECTORY}/objects.tar.gz" .
readonly OBJECT_ARCHIVE_HASH="$(shasum -a 256 "${TEMPORARY_DIRECTORY}/objects.tar.gz" | awk '{print $1}')"
tar -C "${TEMPORARY_DIRECTORY}/objects/target" -xzf "${TEMPORARY_DIRECTORY}/objects.tar.gz"
(cd "${TEMPORARY_DIRECTORY}/objects/target" && shasum -a 256 -c object-manifest.sha256 >/dev/null)

# 等待 Qdrant REST 端点健康。
wait_qdrant() {
  local port="$1"
  local deadline=$((SECONDS + 120))
  until curl --fail --silent "http://127.0.0.1:${port}/healthz" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Qdrant readiness timeout on port ${port}" >&2
      exit 1
    fi
    sleep 1
  done
}

# 创建固定 Revision 向量数据并通过 Qdrant 原生 Snapshot 恢复到空实例。
docker run --detach --name "${RESOURCE_PREFIX}-qdrant-source" \
  --publish 127.0.0.1::6333 \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/qdrant-source,dst=/qdrant/storage" \
  "${QDRANT_IMAGE}" >/dev/null
readonly QDRANT_SOURCE_PORT="$(docker port "${RESOURCE_PREFIX}-qdrant-source" 6333/tcp | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p' | head -1)"
wait_qdrant "${QDRANT_SOURCE_PORT}"
curl --fail --silent --request PUT \
  --header 'Content-Type: application/json' \
  --data '{"vectors":{"size":4,"distance":"Cosine"}}' \
  "http://127.0.0.1:${QDRANT_SOURCE_PORT}/collections/agentark_revision" >/dev/null
curl --fail --silent --request PUT \
  --header 'Content-Type: application/json' \
  --data '{"points":[{"id":1,"vector":[0.1,0.2,0.3,0.4],"payload":{"projectId":"project-a","revisionId":"revision-1"}}]}' \
  "http://127.0.0.1:${QDRANT_SOURCE_PORT}/collections/agentark_revision/points?wait=true" >/dev/null
readonly SNAPSHOT_NAME="$(curl --fail --silent --request POST \
  "http://127.0.0.1:${QDRANT_SOURCE_PORT}/collections/agentark_revision/snapshots" \
  | jq --raw-output '.result.name')"
test -n "${SNAPSHOT_NAME}"
curl --fail --silent \
  "http://127.0.0.1:${QDRANT_SOURCE_PORT}/collections/agentark_revision/snapshots/${SNAPSHOT_NAME}" \
  --output "${TEMPORARY_DIRECTORY}/qdrant.snapshot"
readonly QDRANT_SNAPSHOT_HASH="$(shasum -a 256 "${TEMPORARY_DIRECTORY}/qdrant.snapshot" | awk '{print $1}')"

docker run --detach --name "${RESOURCE_PREFIX}-qdrant-target" \
  --publish 127.0.0.1::6333 \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/qdrant-target,dst=/qdrant/storage" \
  "${QDRANT_IMAGE}" >/dev/null
readonly QDRANT_TARGET_PORT="$(docker port "${RESOURCE_PREFIX}-qdrant-target" 6333/tcp | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p' | head -1)"
wait_qdrant "${QDRANT_TARGET_PORT}"
curl --fail --silent --request POST \
  --form "snapshot=@${TEMPORARY_DIRECTORY}/qdrant.snapshot" \
  "http://127.0.0.1:${QDRANT_TARGET_PORT}/collections/agentark_revision/snapshots/upload?priority=snapshot" >/dev/null
readonly QDRANT_POINTS="$(curl --fail --silent \
  "http://127.0.0.1:${QDRANT_TARGET_PORT}/collections/agentark_revision" \
  | jq --raw-output '.result.points_count')"
test "${QDRANT_POINTS}" = "1"

# Redis 只验证全量丢失后的新实例为空，证明恢复流程不把它当权威事实源。
docker run --detach --name "${RESOURCE_PREFIX}-redis-source" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/redis-password,dst=/run/secrets/redis,readonly" \
  "${REDIS_IMAGE}" /bin/sh -c 'exec redis-server --requirepass "$(cat /run/secrets/redis)"' >/dev/null
sleep 2
docker exec "${RESOURCE_PREFIX}-redis-source" /bin/sh -c \
  'REDISCLI_AUTH=$(cat /run/secrets/redis) redis-cli SET runtime:lease transient >/dev/null'
docker run --detach --name "${RESOURCE_PREFIX}-redis-target" \
  --mount "type=bind,src=${TEMPORARY_DIRECTORY}/redis-password,dst=/run/secrets/redis,readonly" \
  "${REDIS_IMAGE}" /bin/sh -c 'exec redis-server --requirepass "$(cat /run/secrets/redis)"' >/dev/null
sleep 2
readonly REDIS_KEYS="$(docker exec "${RESOURCE_PREFIX}-redis-target" /bin/sh -c \
  'REDISCLI_AUTH=$(cat /run/secrets/redis) redis-cli DBSIZE' | tr -d '\r')"
test "${REDIS_KEYS}" = "0"

# 证据只保留版本、Hash、数量和耗时，不保存备份正文、路径或 Secret。
mkdir -p "${EVIDENCE_DIRECTORY}"
readonly FINISHED_AT="$(date +%s)"
{
  echo "repository_commit=$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)"
  echo "mysql_image=${MYSQL_IMAGE}"
  echo "mysql_binlog_image=${MYSQL_BINLOG_IMAGE}"
  echo "mysql_rows=${MYSQL_ROWS}"
  echo "mysql_full_pitr_seconds=$((MYSQL_FINISHED - MYSQL_STARTED))"
  echo "object_archive_sha256=${OBJECT_ARCHIVE_HASH}"
  echo "qdrant_image=${QDRANT_IMAGE}"
  echo "qdrant_snapshot_sha256=${QDRANT_SNAPSHOT_HASH}"
  echo "qdrant_points=${QDRANT_POINTS}"
  echo "redis_authoritative_restore=OMITTED"
  echo "redis_keys_after_rebuild=${REDIS_KEYS}"
  echo "total_restore_rehearsal_seconds=$((FINISHED_AT - MYSQL_STARTED))"
  echo "secret_values_in_evidence=NONE"
} > "${EVIDENCE_DIRECTORY}/restore-report.txt"

cat "${EVIDENCE_DIRECTORY}/restore-report.txt"
