---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Disaster Recovery Runbook

## 恢复原则

MySQL、Object Store 和 Qdrant 是需要协同恢复的权威/派生数据；Redis 只重建缓存、Lease 和通知，不从 Redis 备份恢复业务事实。Secret Provider、Git/Contracts 和部署配置必须恢复到与数据版本匹配的审计点。

## 顺序

1. 隔离受损环境，冻结写入和外部副作用，保留日志、Audit、Binlog、Object Version 和 Qdrant Snapshot 证据。
2. 恢复 Secret Provider 与只读基础网络，再按 Control → Runtime → Scheduler MySQL 的目标时间执行 Full + PITR。
3. 恢复 Object Store 版本，验证 Snapshot、Skill、Document、Event/State Payload 的 Hash 和引用。
4. 按固定 Knowledge Revision 恢复 Qdrant Snapshot，复核 Point Count、Payload Tenant Filter 和 Checksum。
5. 启动空 Redis，重新构建缓存；清除旧 Lease，使用数据库 Fencing Token 和 Reconciliation 决定 Owner。
6. 以只读方式运行 Count/Hash/Reference 校验，再启动 Control、Scheduler、Runtime、Gateway/Web。
7. Reconcile Outbox、Deployment、WorkItem、Orphan Run、Job、Dead Letter、Knowledge Ingestion 和 Runtime Instance。
8. 执行主流程、SSE/HITL、旧 Session Pin、Scheduler 和跨租户验证后才解除写入冻结。

## RPO/RTO 与回退

工程目标为 Core RPO 不超过 5 分钟、RTO 不超过 60 分钟；目标环境实测覆盖本文件中的本地 Fixture 结果。恢复失败时保留原始备份只读，销毁当前恢复实例并从新的隔离目标重试，禁止覆盖唯一备份。详细命令见 `backup-restore.md`。
