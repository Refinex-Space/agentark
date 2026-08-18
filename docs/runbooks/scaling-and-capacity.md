---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 扩缩容与容量 Runbook

## 扩缩容信号

| 平面 | 主信号 | 辅助信号 | 禁止做法 |
|---|---|---|---|
| Gateway | Request Rate、SSE 连接数、Event Delivery Lag | CPU、连接错误、下游超时 | Session Sticky、按租户高基数 Metric |
| Control | Request Rate、DB Pool 等待、写入 P95 | Outbox Lag、乐观锁冲突 | 通过增加连接无限压高 MySQL |
| Runtime | Active Run、Work Queue、Event Lag、Provider 并发 | CPU/内存、Lease Conflict | 以 Redis 作为 Run 权威状态、共享 Session 内存 |
| Scheduler | Queue Depth、Oldest Age、Job Type | Retry/Dead Letter、Lease Conflict | 多副本无 Fencing、无幂等写重试 |

Chart 默认提供 CPU HPA，并为 Runtime/Scheduler 预留 KEDA Prometheus Trigger。启用 KEDA 前必须确认指标低基数、查询固定且指标后端不可用时不会缩到零；生产最小副本默认为 2。Provider 并发、组织/项目 Quota 和 Worker Pool 必须共同限制噪声邻居。

## 容量变更流程

1. 从 `docs/operations/phase-22-capacity-rpo-rto.md` 复制最近一次已批准基线，不沿用开发机吞吐作为生产容量。
2. 用生产相同镜像 Digest、数据库规格、网络和配额执行 `tools/production/performance-rehearsal.sh` 等价场景。
3. 同时记录 P50/P95/P99、错误率、最大值、GC、DB Pool、CPU/内存和下游限流；模型首 Token、Tool、Sandbox 冷启动单列。
4. 以最先达到的安全上限确定单 Pod 容量，并保留滚动升级、节点故障和 Provider 429 的冗余。
5. 变更 HPA/KEDA、资源请求或连接池后重新执行节点 Drain、Retry Storm 和数据库故障演练。

## 本机回归基线

```bash
./tools/production/performance-rehearsal.sh
```

脚本启动真实四服务 JWT 栈，发布不可变 Snapshot，创建 Deployment，并以 k6 同时执行 Control 读取/写入、Runtime Session/Turn/Event 和 Scheduler Cron。阈值保持为 PLAN 目标；任何检查失败或 P95 超限都会返回非零。该结果用于回归，不是生产容量批准。
