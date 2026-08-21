---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Kubernetes 部署与滚动升级 Runbook

## 发布前检查

```bash
./tools/production/validate-chart.sh deploy/helm/agentark/values-production.example.yaml
helm lint deploy/helm/agentark
helm template agentark deploy/helm/agentark \
  --values <approved-production-values.yaml> > /tmp/agentark-rendered.yaml
```

生产 Values 必须开启 `global.productionValidation`，镜像使用真实 `@sha256` Digest，Secret 来自 ExistingSecret 或 ExternalSecret，Vault 与所选 Built-in Identity/OIDC Issuer 使用真实 HTTPS，Redis 开启 TLS，MySQL 使用 `VERIFY_IDENTITY`，Object Store 支持 RWX/快照/复制，Sandbox RuntimeClass 与 NetworkPolicy 已由集群平台验证。出口 CIDR 不能为全网或云元数据范围。默认不引入 Nacos、Consul、Service Mesh、Kafka、Elasticsearch 或 Neo4j。

## Expand → Migrate → Contract

1. **Expand**：先发布兼容 N/N-1 的契约、读取路径和可空字段；禁止先删除旧字段或收紧旧值。
2. **Migrate**：以 Helm Hook Flyway Job 分别迁移 Built-in Identity（若启用）、Control、Runtime、Scheduler Schema；Job 使用各自最小权限账号，任一失败则中止 Deployment 更新。
3. **Application**：按 Control → Runtime → Scheduler → Gateway/Web 顺序滚动；`maxUnavailable=0`，观察 Readiness、错误率、Outbox/Event Lag 和 DB Pool。
4. **Contract**：在观察窗口和旧版本回退窗口结束后，另一个发布批次移除旧读写路径。

Snapshot、Runtime Event 和 Internal API 必须至少兼容 N/N-1。Session 固定 Revision/Snapshot，滚动升级不能重编译或漂移已存在 Session。

## Drain 与回退

- Runtime/Scheduler Pod 在 `preStop` 后停止领取新工作，等待当前 Lease/Attempt 到安全边界；终止宽限期必须大于 PreStop 和 Spring Graceful Shutdown 总和。
- 节点维护使用 `kubectl drain --ignore-daemonsets --delete-emptydir-data`，先观察 PDB，再确认 Runtime 实例形成 `DRAINING/DRAINED`。
- 应用回退只允许切换到支持当前数据库和 Snapshot/Event Schema 的镜像 Digest。Flyway 迁移不降级；需要收紧约束时新增前向迁移。
- 出现数据不一致、陈旧 Fencing 写入、跨租户访问或无法恢复的 Run 时立即停止灰度并回滚路由/镜像，保持数据库只前进。

## 仓库多节点演练

```bash
./tools/production/build-images.sh
./tools/production/kind-rehearsal.sh
```

演练创建独立三节点 kind 集群、安装 Calico、执行三套 Flyway、运行五个双副本工作负载，验证 NetworkPolicy、非 Root、只读根、无 Sticky Session、节点 Drain、Runtime `DRAINED` 和 RollingUpdate；完成后删除本脚本创建的集群。
