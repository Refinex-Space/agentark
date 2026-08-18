#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 默认只运行可重复的组件故障测试；--full 额外创建并删除临时 Docker/kind 资源。
FULL_REHEARSAL=0
if [[ "${1:-}" == "--full" ]]; then
  FULL_REHEARSAL=1
elif [[ "$#" -gt 0 ]]; then
  echo "Usage: tools/production/fault-rehearsal.sh [--full]" >&2
  exit 2
fi

# Runtime、Provider 和 Scheduler 故障语义必须在无外部服务时可确定重放。
"${REPOSITORY_ROOT}/mvnw" \
  -pl agentark-runtime,agentark-runtime-provider-agentscope,agentark-scheduling \
  -am \
  -Dtest='RuntimeLeaseFencingTest,RuntimeRecoveryTest,RuntimeSseTest,RuntimeApprovalTest,AgentScopeExecutionControlTest,SchedulerWorkerTest,CronTriggerServiceTest,CronCalculatorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

# Qdrant 不可用、Tenant Filter、固定 Revision 和恢复边界使用真实 Testcontainers Qdrant。
"${REPOSITORY_ROOT}/mvnw" \
  -pl agentark-knowledge \
  -am \
  -Dit.test=QdrantKnowledgeVectorStoreIT \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  verify

# 默认 Telemetry 脱敏与 Exporter 不可用不阻断由 Foundation 上下文测试固定。
"${REPOSITORY_ROOT}/mvnw" \
  -pl agentark-foundation/agentark-starter-observability \
  -am \
  -Dtest=AgentArkObservabilityAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

if [[ "${FULL_REHEARSAL}" == "1" ]]; then
  # 全量模式实际执行 MySQL/Qdrant/Object/Redis 恢复演练。
  "${REPOSITORY_ROOT}/tools/production/restore-rehearsal.sh"
  # 全量模式实际执行 NetworkPolicy、节点 Drain、Pod 重调度和 RollingUpdate。
  "${REPOSITORY_ROOT}/tools/production/kind-rehearsal.sh"
fi
