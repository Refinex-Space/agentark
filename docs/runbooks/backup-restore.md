---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 生产备份与恢复 Runbook

## 目标与边界

Core 数据目标为 `RPO ≤ 5 min`、`RTO ≤ 60 min`。该目标同时覆盖三个 MySQL Schema、Control/Runtime Object Store、Secret Provider 引用和发布配置；Redis 只保存可重建的缓存、租约和通知，不作为业务事实恢复源。Qdrant 由 KnowledgeRevision 派生，但生产仍应保留 Snapshot 以缩短 RTO。

Helm Chart 不部署 MySQL、Redis、对象存储或 Qdrant，生产必须使用具备备份、跨故障域复制、监控和演练能力的托管服务。备份系统不得把 Secret 值写入 Git、ConfigMap、日志或演练报告。

## 备份策略

| 数据 | 最低策略 | 完整性证据 | Owner |
|---|---|---|---|
| MySQL | 每日全量、Binlog 连续归档且最大间隔 5 分钟、至少跨区一份 | 全量 Hash、Binlog 文件/位置、UTC 时间、三 Schema 行数与引用检查 | 数据库平台 |
| Object Store | Versioning、对象锁或等价不可变保留、跨区复制 | Bucket 清单、Object Key/Version/Size/SHA-256 | 存储平台 |
| Qdrant | 每个 READY KnowledgeRevision 的 Collection Snapshot，按变更和每日计划执行 | Snapshot Hash、Collection/Revision、Point Count | Knowledge 平面 |
| Secret Provider | Provider 原生备份、KMS/Unseal Key 独立保管 | 恢复演练编号；应用只记录 SecretRef | 安全平台 |
| Redis | 不恢复业务事实；只保留平台按需 AOF/HA | 重建后 Lease、缓存、限流为空且 Reconcile 正常 | 平台运行时 |
| Contract/Config | Git Tag、镜像 Digest、Helm Values、Schema、SBOM 和签名 | Commit、Digest、签名身份与审批记录 | 发布平台 |

## 恢复顺序

1. 冻结 Gateway 写流量和 Scheduler/Runtime 新 Claim，记录故障时间与目标恢复时间点；不要删除现有卷或备份。
2. 恢复 Secret Provider 和工作负载身份，但不导出 Secret 值。
3. 恢复三个 MySQL Schema 到同一 UTC 时间点，分别使用独立账号校验 Flyway History、Count、主键和逻辑引用。
4. 恢复 Object Store Version，并按数据库 ObjectRef 校验 Authority、Size 和 SHA-256。
5. 恢复 Qdrant Snapshot；若 Snapshot 不可信，则从 READY KnowledgeRevision、原文和 Chunk Artifact 重建，不改变 Revision 身份。
6. 以空 Redis 启动，禁止从旧 Lease 恢复 Owner；运行 Runtime/Scheduler/Outbox/Deployment Reconcile。
7. 先启动 Control，再启动 Runtime、Scheduler，最后开放 Gateway；逐项核对审计、Outbox Lag、孤儿 Run、Job、Lease 和 Deployment 指针。
8. 解除写冻结前执行跨租户读取、旧 Fencing Token、Event Sequence、Snapshot Hash 和抽样业务流程验证。

## 仓库演练

```bash
./tools/production/restore-rehearsal.sh
```

脚本只创建带 `agentark-phase22-restore-<pid>` 前缀的临时容器和 `mktemp` 数据目录。它实际执行 MySQL 全量恢复与 Binlog PITR、对象归档 Hash 校验、Qdrant 原生 Snapshot 恢复，并证明 Redis 新实例为空；退出时精确清理自己创建的资源。证据写入已忽略的 `.agentark/evidence/phase22/restore-report.txt`。

该快速演练证明恢复机制和顺序，不代表生产数据量、跨区带宽、托管服务权限或 KMS 恢复已经验收。每个生产环境至少每季度执行一次带真实容量副本的隔离恢复，并把实测 RPO/RTO、审批人和偏差记录到变更系统。

## 失败与回退

- 任一 Schema 时间点、Object Hash 或 Snapshot Count 不一致时保持只读，禁止部分开放。
- PITR 目标选错时保留失败实例，重新从不可变全量备份恢复到新实例；禁止在失败实例上覆盖式修补。
- Qdrant 失败不改变 KnowledgeRevision；切换到受控“不可检索”状态或从权威 Artifact 重建。
- Redis 恢复旧 Lease 属于错误操作，应清空并重新 Reconcile。
- 回退到旧应用版本前必须确认其支持当前 Flyway、Snapshot 和 Event Schema；已应用迁移不得回写历史文件。
