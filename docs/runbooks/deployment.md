---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Deployment Runbook

## 拓扑与外部依赖

AgentArk 部署 Gateway、Control、Runtime、Scheduler 和 Web 五个无状态工作负载，以及三个一次性 Flyway Owner Job。MySQL、Redis、Object Store、Qdrant、OIDC、Vault/Secret Manager 和 OTel 后端由外部平台提供；禁止让任一服务使用其他平面的数据库账号。

## 部署步骤

1. 从 `deploy/helm/agentark/values-production.example.yaml` 派生受控 Values，替换所有 Registry Digest、HTTPS Endpoint、TLS、StorageClass、Secret 名和 Sandbox RuntimeClass。
2. 执行 `tools/production/validate-chart.sh`、Helm Template、kubeconform 和集群 Server-side Dry Run。
3. 验证 MySQL 备份点、三个最小权限账号、Redis TLS、Object/Qdrant 备份和 OIDC/Vault 可用性。
4. 先运行 Flyway Job，逐个确认 Control、Runtime、Scheduler Schema History。
5. 部署 Control、Scheduler、Runtime、Gateway、Web；Runtime/Scheduler 在 Readiness 前不得 Claim 工作。
6. 验证 Health、Build Info、Trace、Metric、NetworkPolicy、Secret 脱敏和跨 Namespace 阻断。
7. 执行真实 E2E、SSE 重连、HITL、Scheduler、备份恢复抽样和业务 Smoke。

## 回滚

应用回滚只使用已记录且兼容当前 DB/Contract 的旧 Digest。Flyway 不回退；若新版本写入了旧版本不理解的数据，保持新版本只读或前向修复，不能强行降级。详见 `upgrade-rollback.md`。
