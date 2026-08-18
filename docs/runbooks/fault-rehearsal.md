---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 故障演练 Runbook

## 演练矩阵

| 故障 | 注入方式 | 必须观察到的行为 |
|---|---|---|
| Runtime Pod/节点 | kind Node Drain、RollingUpdate | 不接新工作；PDB 生效；实例 `DRAINED`；在途任务恢复或形成新 Attempt |
| Redis 全量丢失 | 恢复演练启动空 Redis | Session/Run/Job 权威事实不丢；Lease/缓存重新建立；旧 Owner 不能写 |
| MySQL | PITR 到指定 Binlog 位置 | 三 Schema 同一时间点；Event/Outbox/Job 可 Reconcile；不恢复目标之后事实 |
| Qdrant | 原生 Snapshot 恢复 | 固定 KnowledgeRevision Point Count 与 Payload 保持一致；失败时不可伪装 READY |
| Object Store | 归档恢复与 SHA-256 校验 | ObjectRef Authority、Size、Hash 一致；不使用签名 URL 作为持久引用 |
| Control 不可用 | Runtime Recovery/缓存测试 | 已缓存 Snapshot 可继续；新 Snapshot 加载形成可查询失败/重试，不丢 runId |
| Provider 429/Timeout | AgentScope Execution Control 测试 | 转换为稳定错误类别；按策略重试/终止；不泄漏 Provider 正文 |
| Scheduler Retry Storm | Worker/Cron/Fencing 测试 | Retry Budget、Jitter、Dead Letter、旧 Token 拒绝 |
| OTel Backend 不可用 | Observability 上下文测试 | Export 丢弃或退避，不阻断业务；Audit/Event 不依赖采样 |
| 跨 Namespace 访问 | Calico NetworkPolicy 探针 | 非授权 Namespace 直连 Gateway/平面超时或拒绝 |

## 命令

```bash
# 快速、可重复的组件故障语义
./tools/production/fault-rehearsal.sh

# 包含临时 MySQL/Qdrant/Object/Redis 恢复和三节点 kind 故障注入
./tools/production/fault-rehearsal.sh --full
```

`--full` 会创建并删除临时容器和固定名 `agentark-phase22` kind 集群。运行前必须确认该集群名不存在、Docker 资源足够且六个本地生产镜像已构建；脚本禁止静默复用同名集群。

## 停止条件与回滚

出现跨租户可见、陈旧 Fencing 写入成功、Event 丢失、Secret 出现在输出、恢复 Hash 不一致或业务线程依赖 OTel 时立即停止演练和发布。保留脱敏事件、测试报告和镜像/Commit 身份；回滚应用路由或镜像，但数据库只使用新的前向迁移修正。
