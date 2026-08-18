#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 固定一次性集群名称，删除范围不会扩大到其他 kind 集群。
readonly CLUSTER_NAME="agentark-phase22"
# 固定应用 Namespace。
readonly APPLICATION_NAMESPACE="agentark-system"
# 固定 Calico Operator Chart 版本。
readonly CALICO_VERSION="v3.32.1"
# 固定 Calico Chart 下载后的 SHA-256。
readonly CALICO_CHART_SHA="563f75f29bdbb13dde13a1d51244b96f42b4fe0eef5be763fb55fa9756f31c93"
# 固定工具目录。
readonly BIN_DIRECTORY="${REPOSITORY_ROOT}/.agentark/bin"
# 固定 kind 客户端。
readonly KIND="${BIN_DIRECTORY}/kind"
# 固定 Helm 客户端。
readonly HELM="${BIN_DIRECTORY}/helm"
# 使用系统 kubectl，但 Context 只写临时 Kubeconfig。
readonly KUBECTL="$(command -v kubectl)"
# 固定 Chart 路径。
readonly CHART_DIRECTORY="${REPOSITORY_ROOT}/deploy/helm/agentark"
# kind 演练 Values。
readonly VALUES_FILE="${REPOSITORY_ROOT}/tools/production/fixtures/kind-values.yaml"
# 外部依赖 Fixture。
readonly DEPENDENCIES_FILE="${REPOSITORY_ROOT}/tools/production/fixtures/kind-dependencies.yaml"
# kind 三节点配置。
readonly CLUSTER_CONFIG="${REPOSITORY_ROOT}/tools/production/fixtures/kind-cluster.yaml"
# 证据输出目录已由 `.gitignore` 排除。
readonly EVIDENCE_DIRECTORY="${REPOSITORY_ROOT}/.agentark/evidence/phase22"
# 临时目录保存 Kubeconfig 和随机 Secret 文件。
readonly TEMPORARY_DIRECTORY="$(mktemp -d)"
# 不修改用户默认 Kubeconfig。
export KUBECONFIG="${TEMPORARY_DIRECTORY}/kubeconfig"
# 默认失败或成功后删除本脚本创建的集群；显式保留仅用于诊断。
readonly KEEP_CLUSTER="${AGENTARK_KEEP_KIND:-0}"
# 记录脚本是否真正创建了集群，避免删除外部资源。
CLUSTER_CREATED=0
# Calico 镜像由宿主拉取后导入 kind，避免节点继承不可达的本机代理。
readonly -a CALICO_IMAGES=(
  "quay.io/tigera/operator:v1.42.3"
  "quay.io/calico/node:v3.32.1"
  "quay.io/calico/cni:v3.32.1"
  "quay.io/calico/kube-controllers:v3.32.1"
  "quay.io/calico/typha:v3.32.1"
  "quay.io/calico/node-driver-registrar:v3.32.1"
  "quay.io/calico/csi:v3.32.1"
  "quay.io/calico/pod2daemon-flexvol:v3.32.1"
)
# 演练基础设施和 NetworkPolicy 探针镜像同样由宿主导入。
readonly -a INFRA_IMAGES=(
  "mysql:8.4.11"
  "redis:8.10.0"
  "busybox:1.37.0"
)
# Docker Desktop 架构映射为 kind 节点平台。
case "$(docker info --format '{{.Architecture}}')" in
  aarch64|arm64)
    readonly KIND_PLATFORM="linux/arm64"
    ;;
  x86_64|amd64)
    readonly KIND_PLATFORM="linux/amd64"
    ;;
  *)
    echo "unsupported Docker architecture for kind rehearsal" >&2
    exit 2
    ;;
esac

# 在清理前保存脱敏资源状态和事件。
cleanup() {
  local status=$?
  mkdir -p "${EVIDENCE_DIRECTORY}"
  if [[ "${CLUSTER_CREATED}" == "1" ]]; then
    "${KUBECTL}" get nodes,pods,deployments,jobs -A -o wide \
      > "${EVIDENCE_DIRECTORY}/kind-final-state.txt" 2>&1 || true
    "${KUBECTL}" get events -A --sort-by=.lastTimestamp \
      > "${EVIDENCE_DIRECTORY}/kind-events.txt" 2>&1 || true
    : > "${EVIDENCE_DIRECTORY}/kind-job-logs.txt"
    while IFS= read -r job_name; do
      [[ -z "${job_name}" ]] && continue
      {
        echo "JOB=${job_name}"
        "${KUBECTL}" logs --namespace "${APPLICATION_NAMESPACE}" \
          "job/${job_name}" --all-containers=true
      } >> "${EVIDENCE_DIRECTORY}/kind-job-logs.txt" 2>&1 || true
    done < <("${KUBECTL}" get jobs --namespace "${APPLICATION_NAMESPACE}" \
      --output jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)
    if [[ "${KEEP_CLUSTER}" != "1" ]]; then
      "${KIND}" delete cluster --name "${CLUSTER_NAME}" >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "${TEMPORARY_DIRECTORY}"
  exit "${status}"
}
trap cleanup EXIT

# 固定工具缺失时安装到已忽略目录。
if [[ ! -x "${KIND}" || ! -x "${HELM}" ]]; then
  "${REPOSITORY_ROOT}/tools/production/bootstrap-tools.sh"
fi

# 禁止静默复用同名集群，避免污染用户已有状态。
if "${KIND}" get clusters | rg -x "${CLUSTER_NAME}" >/dev/null; then
  echo "kind cluster ${CLUSTER_NAME} already exists" >&2
  exit 2
fi

# 六个本地镜像必须已经通过生产 Dockerfile 构建。
for image in \
  agentark/agentark-gateway-server:phase22 \
  agentark/agentark-control-server:phase22 \
  agentark/agentark-runtime-server:phase22 \
  agentark/agentark-scheduler-server:phase22 \
  agentark/agentark-web:phase22 \
  agentark/agentark-migrations:phase22; do
  docker image inspect "${image}" >/dev/null
done

# 先登记清理所有权，再创建禁用默认 CNI 的三节点集群。
CLUSTER_CREATED=1
"${KIND}" create cluster \
  --name "${CLUSTER_NAME}" \
  --config "${CLUSTER_CONFIG}" \
  --kubeconfig "${KUBECONFIG}"

# 宿主 Docker 负责拉取并缓存固定 Calico 镜像，再导入所有节点。
for image in "${CALICO_IMAGES[@]}"; do
  docker image inspect "${image}" >/dev/null 2>&1 || docker pull "${image}" >/dev/null
  if ! "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null 2>&1; then
    docker buildx build \
      --file "${REPOSITORY_ROOT}/tools/production/fixtures/Dockerfile.kind-import" \
      --build-arg "SOURCE_IMAGE=${image}" \
      --platform "${KIND_PLATFORM}" \
      --provenance=false \
      --tag "${image}" \
      --load \
      "${REPOSITORY_ROOT}" >/dev/null
    "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null
  fi
done
# 导入 MySQL、Redis 和探针镜像，避免 kind 节点直接访问外部 Registry。
for image in "${INFRA_IMAGES[@]}"; do
  docker image inspect "${image}" >/dev/null 2>&1 || docker pull "${image}" >/dev/null
  if ! "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null 2>&1; then
    docker buildx build \
      --file "${REPOSITORY_ROOT}/tools/production/fixtures/Dockerfile.kind-import" \
      --build-arg "SOURCE_IMAGE=${image}" \
      --platform "${KIND_PLATFORM}" \
      --provenance=false \
      --tag "${image}" \
      --load \
      "${REPOSITORY_ROOT}" >/dev/null
    "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null
  fi
done

# 在隔离 Helm Home 中下载并校验固定 Calico Operator Chart。
readonly CALICO_CACHE="${REPOSITORY_ROOT}/.agentark/cache/phase22-tools/tigera-operator-${CALICO_VERSION}.tgz"
if [[ ! -f "${CALICO_CACHE}" ]] || \
   [[ "$(shasum -a 256 "${CALICO_CACHE}" | awk '{print $1}')" != "${CALICO_CHART_SHA}" ]]; then
  export HELM_CONFIG_HOME="${TEMPORARY_DIRECTORY}/helm-config"
  export HELM_CACHE_HOME="${TEMPORARY_DIRECTORY}/helm-cache"
  export HELM_DATA_HOME="${TEMPORARY_DIRECTORY}/helm-data"
  mkdir -p "$(dirname "${CALICO_CACHE}")"
  "${HELM}" repo add projectcalico https://docs.tigera.io/calico/charts
  "${HELM}" repo update projectcalico
  "${HELM}" pull projectcalico/tigera-operator \
    --version "${CALICO_VERSION}" \
    --destination "$(dirname "${CALICO_CACHE}")"
fi
test "$(shasum -a 256 "${CALICO_CACHE}" | awk '{print $1}')" = "${CALICO_CHART_SHA}"

# 第一阶段只安装 Calico CRD 与 Operator，避免 Helm API Discovery 在同一事务识别不到新 CRD。
"${HELM}" upgrade --install calico "${CALICO_CACHE}" \
  --namespace tigera-operator \
  --create-namespace \
  --set installation.enabled=false \
  --set apiServer.enabled=false \
  --set goldmane.enabled=false \
  --set whisker.enabled=false \
  --wait \
  --timeout 5m
# Operator 在启动后异步创建 CRD；先等待资源出现，再等待 Established 条件。
for attempt in $(seq 1 60); do
  if "${KUBECTL}" get customresourcedefinition/installations.operator.tigera.io \
      >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
"${KUBECTL}" wait --for=condition=Established \
  customresourcedefinition/installations.operator.tigera.io \
  --timeout=120s
# 第二阶段创建 Installation，启用 Calico CNI/NetworkPolicy，并等待节点可用。
"${HELM}" upgrade calico "${CALICO_CACHE}" \
  --namespace tigera-operator \
  --set installation.kubernetesProvider=Kind \
  --set installation.calicoNetwork.ipPools[0].cidr=192.168.0.0/16 \
  --set installation.calicoNetwork.ipPools[0].encapsulation=VXLAN \
  --set installation.calicoNetwork.ipPools[0].natOutgoing=Enabled \
  --set apiServer.enabled=false \
  --set goldmane.enabled=false \
  --set whisker.enabled=false \
  --wait \
  --timeout 10m
"${KUBECTL}" wait --for=condition=Ready nodes --all --timeout=300s

# 将本地内容镜像导入所有 kind 节点，演练不访问 Registry。
for image in \
  agentark/agentark-gateway-server:phase22 \
  agentark/agentark-control-server:phase22 \
  agentark/agentark-runtime-server:phase22 \
  agentark/agentark-scheduler-server:phase22 \
  agentark/agentark-web:phase22 \
  agentark/agentark-migrations:phase22; do
  if ! "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null 2>&1; then
    docker buildx build \
      --file "${REPOSITORY_ROOT}/tools/production/fixtures/Dockerfile.kind-import" \
      --build-arg "SOURCE_IMAGE=${image}" \
      --platform "${KIND_PLATFORM}" \
      --provenance=false \
      --tag "${image}" \
      --load \
      "${REPOSITORY_ROOT}" >/dev/null
    "${KIND}" load docker-image --name "${CLUSTER_NAME}" "${image}" >/dev/null
  fi
done

# 创建演练 Namespace。
"${KUBECTL}" create namespace "${APPLICATION_NAMESPACE}"
# 将已审计 MySQL 初始化脚本作为只读 ConfigMap 挂载。
"${KUBECTL}" create configmap agentark-mysql-init \
  --namespace "${APPLICATION_NAMESPACE}" \
  --from-file=01-agentark-schemas.sh="${REPOSITORY_ROOT}/deploy/compose/mysql/init/01-agentark-schemas.sh"

# 生成只存在于临时目录的随机凭据文件。
for key in root control runtime scheduler redis runtime_token scheduler_token vault; do
  openssl rand -hex 32 | tr -d '\n' > "${TEMPORARY_DIRECTORY}/${key}"
  chmod 0600 "${TEMPORARY_DIRECTORY}/${key}"
done

# 同一个 Secret 同时提供基础设施文件键和应用稳定键；命令行不出现 Secret 值。
"${KUBECTL}" create secret generic agentark-kind-secrets \
  --namespace "${APPLICATION_NAMESPACE}" \
  --from-file=mysql_root_password="${TEMPORARY_DIRECTORY}/root" \
  --from-file=mysql_control_password="${TEMPORARY_DIRECTORY}/control" \
  --from-file=mysql_runtime_password="${TEMPORARY_DIRECTORY}/runtime" \
  --from-file=mysql_scheduler_password="${TEMPORARY_DIRECTORY}/scheduler" \
  --from-file=redis_password="${TEMPORARY_DIRECTORY}/redis" \
  --from-file=control-database-password="${TEMPORARY_DIRECTORY}/control" \
  --from-file=runtime-database-password="${TEMPORARY_DIRECTORY}/runtime" \
  --from-file=scheduler-database-password="${TEMPORARY_DIRECTORY}/scheduler" \
  --from-file=redis-password="${TEMPORARY_DIRECTORY}/redis" \
  --from-file=runtime-service-token="${TEMPORARY_DIRECTORY}/runtime_token" \
  --from-file=scheduler-service-token="${TEMPORARY_DIRECTORY}/scheduler_token" \
  --from-file=vault-token="${TEMPORARY_DIRECTORY}/vault"

# 启动短期 MySQL/Redis，并等待账号和三 Schema 初始化完成。
"${KUBECTL}" apply --namespace "${APPLICATION_NAMESPACE}" --filename "${DEPENDENCIES_FILE}"
"${KUBECTL}" rollout status deployment/mysql --namespace "${APPLICATION_NAMESPACE}" --timeout=300s
"${KUBECTL}" rollout status deployment/redis --namespace "${APPLICATION_NAMESPACE}" --timeout=180s

# 安装 Chart；三个 pre-install Flyway Job 必须先完成，随后五个工作负载各运行两副本。
readonly INSTALL_STARTED="$(date +%s)"
"${HELM}" upgrade --install agentark "${CHART_DIRECTORY}" \
  --namespace "${APPLICATION_NAMESPACE}" \
  --values "${VALUES_FILE}" \
  --wait \
  --wait-for-jobs \
  --timeout 12m
readonly INSTALL_FINISHED="$(date +%s)"

# 验证五个 Deployment 全部两副本 Ready。
for component in gateway control runtime scheduler web; do
  "${KUBECTL}" rollout status \
    "deployment/agentark-agentark-${component}" \
    --namespace "${APPLICATION_NAMESPACE}" \
    --timeout=300s
  test "$("${KUBECTL}" get deployment "agentark-agentark-${component}" \
    --namespace "${APPLICATION_NAMESPACE}" \
    --output jsonpath='{.status.readyReplicas}')" = "2"
done

# 读取三个 Owner 的最终 Flyway 版本，证明 Hook Job 使用当前迁移镜像。
readonly MYSQL_POD="$("${KUBECTL}" get pods --namespace "${APPLICATION_NAMESPACE}" \
  --selector app.kubernetes.io/name=mysql --output jsonpath='{.items[0].metadata.name}')"
flyway_version() {
  local schema="$1"
  "${KUBECTL}" exec --namespace "${APPLICATION_NAMESPACE}" "${MYSQL_POD}" -- \
    /bin/sh -c "MYSQL_PWD=\$(cat /run/secrets/mysql_root_password) mysql --batch --skip-column-names --user=root ${schema} -e 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1'" \
    | tail -1
}
readonly CONTROL_FLYWAY_VERSION="$(flyway_version agentark_control)"
readonly RUNTIME_FLYWAY_VERSION="$(flyway_version agentark_runtime)"
readonly SCHEDULER_FLYWAY_VERSION="$(flyway_version agentark_scheduler)"
test "${CONTROL_FLYWAY_VERSION}" = "7"
test "${RUNTIME_FLYWAY_VERSION}" = "3"
test "${SCHEDULER_FLYWAY_VERSION}" = "3"

# 运行态验证非 Root、只读根、无 ServiceAccount Token 自动挂载和无 Sticky Session。
for component in gateway control runtime scheduler web; do
  test "$("${KUBECTL}" get deployment "agentark-agentark-${component}" \
    --namespace "${APPLICATION_NAMESPACE}" \
    --output jsonpath='{.spec.template.spec.automountServiceAccountToken}')" = "false"
  test "$("${KUBECTL}" get deployment "agentark-agentark-${component}" \
    --namespace "${APPLICATION_NAMESPACE}" \
    --output jsonpath='{.spec.template.spec.containers[0].securityContext.readOnlyRootFilesystem}')" = "true"
done
test "$("${KUBECTL}" get service agentark-agentark-runtime \
  --namespace "${APPLICATION_NAMESPACE}" --output jsonpath='{.spec.sessionAffinity}')" = "None"

# 同 Namespace Web Pod 可访问 Gateway/Control/Runtime/Scheduler 健康端点。
readonly WEB_POD="$("${KUBECTL}" get pods --namespace "${APPLICATION_NAMESPACE}" \
  --selector app.kubernetes.io/component=web \
  --output jsonpath='{.items[0].metadata.name}')"
for component_port in gateway:8080 control:8081 runtime:8082 scheduler:8083; do
  component="${component_port%%:*}"
  port="${component_port##*:}"
  "${KUBECTL}" exec --namespace "${APPLICATION_NAMESPACE}" "${WEB_POD}" -- \
    wget -T 10 -q -O - "http://agentark-agentark-${component}:${port}/actuator/health/liveness" \
    | rg -q '"status":"UP"'
done

# 创建外部 Namespace 的攻击测试 Pod；NetworkPolicy 必须阻断直连 Gateway Service。
"${KUBECTL}" create namespace agentark-intruder
"${KUBECTL}" run intruder --namespace agentark-intruder \
  --image=busybox:1.37.0 --image-pull-policy=Never --restart=Never -- sleep 600
"${KUBECTL}" wait --for=condition=Ready pod/intruder --namespace agentark-intruder --timeout=120s
if "${KUBECTL}" exec --namespace agentark-intruder intruder -- \
  wget -T 3 -q -O - \
  "http://agentark-agentark-gateway.${APPLICATION_NAMESPACE}.svc:8080/actuator/health/liveness"; then
  echo "NetworkPolicy failed to block cross-namespace access" >&2
  exit 1
fi

# 排空承载 Runtime Pod 的 Worker，验证 PDB、跨节点重调度和 Spring Drain 状态。
readonly OLD_RUNTIME_POD="$("${KUBECTL}" get pods --namespace "${APPLICATION_NAMESPACE}" \
  --selector app.kubernetes.io/component=runtime \
  --output jsonpath='{.items[0].metadata.name}')"
readonly DRAIN_NODE="$("${KUBECTL}" get pod "${OLD_RUNTIME_POD}" \
  --namespace "${APPLICATION_NAMESPACE}" --output jsonpath='{.spec.nodeName}')"
readonly DRAIN_STARTED="$(date +%s)"
"${KUBECTL}" drain "${DRAIN_NODE}" \
  --ignore-daemonsets \
  --delete-emptydir-data \
  --force \
  --timeout=180s
for component in gateway control runtime scheduler web; do
  "${KUBECTL}" rollout status \
    "deployment/agentark-agentark-${component}" \
    --namespace "${APPLICATION_NAMESPACE}" \
    --timeout=300s
done
readonly DRAIN_FINISHED="$(date +%s)"
"${KUBECTL}" uncordon "${DRAIN_NODE}"

# 被排空 Runtime 实例必须在 MySQL 中形成 DRAINED，而不是继续 Claim。
readonly DRAIN_STATUS="$("${KUBECTL}" exec --namespace "${APPLICATION_NAMESPACE}" "${MYSQL_POD}" -- \
  /bin/sh -c "MYSQL_PWD=\$(cat /run/secrets/mysql_root_password) mysql --batch --skip-column-names --user=root agentark_runtime -e \"SELECT drain_status FROM runtime_instance WHERE instance_key='${OLD_RUNTIME_POD}'\"" | tail -1)"
test "${DRAIN_STATUS}" = "DRAINED"

# 触发 Runtime RollingUpdate，验证 maxUnavailable=0 与 N/N-1 兼容启动。
readonly ROLLING_STARTED="$(date +%s)"
"${KUBECTL}" set env deployment/agentark-agentark-runtime \
  --namespace "${APPLICATION_NAMESPACE}" \
  "AGENTARK_PHASE22_ROLLING_TOKEN=$(date +%s)"
"${KUBECTL}" rollout status deployment/agentark-agentark-runtime \
  --namespace "${APPLICATION_NAMESPACE}" --timeout=300s
readonly ROLLING_FINISHED="$(date +%s)"

# 输出不含 Secret 的运行态验收报告。
mkdir -p "${EVIDENCE_DIRECTORY}"
{
  echo "cluster=${CLUSTER_NAME}"
  echo "kubernetes=$("${KUBECTL}" version --output json | jq -r '.serverVersion.gitVersion')"
  echo "calico=${CALICO_VERSION}"
  echo "replicas_per_workload=2"
  echo "control_flyway_version=${CONTROL_FLYWAY_VERSION}"
  echo "runtime_flyway_version=${RUNTIME_FLYWAY_VERSION}"
  echo "scheduler_flyway_version=${SCHEDULER_FLYWAY_VERSION}"
  echo "install_seconds=$((INSTALL_FINISHED - INSTALL_STARTED))"
  echo "node_drain_recovery_seconds=$((DRAIN_FINISHED - DRAIN_STARTED))"
  echo "rolling_upgrade_seconds=$((ROLLING_FINISHED - ROLLING_STARTED))"
  echo "runtime_drained=${DRAIN_STATUS}"
  echo "cross_namespace_gateway=BLOCKED"
} > "${EVIDENCE_DIRECTORY}/kind-report.txt"

cat "${EVIDENCE_DIRECTORY}/kind-report.txt"
