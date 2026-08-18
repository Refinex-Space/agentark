---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Phase 22 容量、RPO 与 RTO 报告

## 批准边界

本报告是 2026-08-18 在本机 Docker Desktop、真实 Java 四服务、MySQL 8.4、Redis 8.10、Qdrant 1.18.3 和三节点 kind 上执行的工程回归基线。它批准仓库架构门禁和演练工具进入后续环境，不批准任何生产流量、数据规模、跨区带宽或托管服务 SLA。生产上线仍需在目标环境重复同一场景并由服务 Owner 与 SRE 批准。

## 性能结果

最终 k6 同时运行 5 个 Control 读 VU、1 个 Control 写 VU、3 个 Runtime VU 和 1 个 Scheduler VU；20 秒内完成 6,210 个 HTTP 请求，0 个失败，6,111 个检查全部成功。真实 SSE 使用 `Last-Event-ID: 0` 从 Turn 接单到首事件抵达为 `205.97 ms`，并成功保持 20 个并发回放连接 3 秒。

| 目标 | 实测 P95 | 结论 |
|---|---:|---|
| Control 常规读 API `< 300 ms` | `36.84 ms` | 通过本机回归门禁 |
| Control 常规写 API `< 800 ms` | `633.10 ms` | 通过本机回归门禁 |
| Runtime Turn 接受 `< 500 ms` | `259.82 ms` | 通过本机回归门禁 |
| Event 持久化后 API 可见 `< 1 s` | `376.65 ms` | 通过 |
| Event 持久化到真实 SSE 首事件 `< 1 s` | `205.97 ms` | 通过，携带 `Last-Event-ID: 0` |
| Scheduler 到期任务启动 `< 5 s` | `1.92 s` | 通过本机回归门禁 |

60 次 Control 写入的最大值为 `761.44 ms`。第一次双写 VU 探测 P95 为 `804.92 ms`，不能据此批准双写并发容量；扩容或提高并发前必须以生产数据库规格复测，且生产告警仍须覆盖 P99/最大值。

## HA 与恢复结果

| 演练 | 实测 | 结论 |
|---|---:|---|
| 三节点 Chart 安装与五工作负载双副本 Ready | `59 s` | 通过；Flyway Control/Runtime/Scheduler=`7/3/3` |
| Runtime 所在节点 Drain 后恢复 | `31 s` | 通过，实例形成 `DRAINED` |
| Runtime RollingUpdate | `27 s` | 通过，无 Sticky Session |
| 跨 Namespace Gateway 访问 | `BLOCKED` | Calico 策略通过 |
| MySQL 全量 + Binlog PITR | `9 s` | 只恢复目标两条事实，目标之后事实未出现 |
| Object Archive | SHA-256 一致 | 通过 |
| Qdrant Snapshot | `1` Point | 通过 |
| Redis 重建 | `0` Key | 通过，未恢复权威事实 |
| 快速恢复演练总耗时 | `22 s` | 远低于 60 分钟工程目标 |

受控 PITR 演练在目标 Binlog 关闭后立即恢复，数据丢失为 0，因此本次测试 RPO 为 0；生产能否达到 5 分钟取决于实际 Binlog 归档最大间隔、Object Replication 和 Secret Provider 备份，必须持续监控。快速 Fixture 的 22 秒不是生产 RTO 预测。

## 复现命令

```bash
./tools/production/validate-chart.sh
./tools/production/restore-rehearsal.sh
./tools/production/performance-rehearsal.sh
./tools/production/kind-rehearsal.sh
./tools/production/fault-rehearsal.sh
```

机器可读原始证据位于已忽略的 `.agentark/evidence/phase22/`，不提交 Token、Secret、备份正文或运行数据。
