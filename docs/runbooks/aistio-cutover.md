---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md#安全运维
---

# Aistio → Java Control Cutover Runbook

## 适用范围与禁止项

本流程用于已有 AgentScope Service/Aistio 部署迁到 AgentArk。AgentArk 新部署没有 Go 依赖，无需启动 Aistio 完成“模拟切换”。

禁止：Java/Go 双写同一聚合；Runtime 双读 Catalog；导出密码摘要、密文、Token 或 Secret 值；活动 Run 中途切 Owner；跳过只读备份、Dry Run、Hash/Reference 和 Shadow Gate；用数据库手工更新替代 API。

## 角色与输入

| 角色 | 职责 |
|---|---|
| Migration Commander | 进入/退出每个 Wave、批准写冻结和 Cutover |
| Go Owner | 只读备份、Route/Write Freeze、活动 Session 排空 |
| Java Control Owner | 目标 Project/Profile/Model/MCP/Skill 映射、迁移 API |
| Runtime/Scheduler Owner | Session Owner、Job 幂等、Worker 排空 |
| Security/Audit | Secret 最小化、权限差异、报告与 Token File |

输入必须包括：固定来源 Commit、Aistio PostgreSQL 只读备份 ObjectRef/SHA-256、目标 Organization/Project、预创建的 Model/Profile/Permission 映射、三 Plane HTTPS/Loopback 地址、未提交 Token File、观察窗口和回滚负责人。

## 0. 预检

```bash
python3 tools/harness/verify_upstreams.py --require-worktrees
python3 -m unittest discover -s tools/migration/tests -v
./mvnw -pl agentark-kernel -Dtest='ContractSchemaTest,AistioCutoverContractTest' test
docker compose -f deploy/compose/docker-compose.yml --profile core config \
  | rg -ni 'aistio|aistiod|golang|postgres:17' && exit 1 || true
```

记录三份 Internal Contract SHA；与 `contracts/migration/aistio-cutover-v1.json` 不一致立即停止，不临时更新 Hash 绕过。

## 1. 备份与只读导出

1. 对 Aistio PostgreSQL 做一致性、只读、可恢复备份，上传受控 Object Store，计算 SHA-256。
2. 把 Product/Scheduler 写入口置为维护或只读；活动 Session/Run 仍由原 Go/Java Data Plane 完成。
3. 使用最小只读数据库账号执行：

```bash
psql "$AISTIO_READ_ONLY_DSN" \
  -v backup_uri='object://migration-backups/<backup-object>' \
  -v backup_checksum='sha256:<backup-sha256>' \
  -f tools/migration/export-aistio.sql \
  > .agentark/migration/aistio-export.ndjson
```

不得把 DSN、Token 或备份凭据写入命令历史；实际生产应从受控 Shell Secret 注入。导出文件位于 `.agentark/`，禁止提交。

## 2. Validate 与 Dry Run

复制 `tools/migration/aistio-cutover.example.json` 到 `.agentark/migration/cutover.json`，填入目标 ID、Principal、不可变 Asset/Profile/Policy Version、已迁外部 SecretRef、Webhook SecretRef 和 Token File 路径。旧 `config_json`、Password/API Key Hash、Ciphertext/Webhook Token 不进入配置或导出。保持 `mode=SHADOW` 或 `JAVA_ONLY`，不要先启用 Fallback。

```bash
python3 tools/migration/aistio_migrate.py validate \
  --export .agentark/migration/aistio-export.ndjson \
  --config .agentark/migration/cutover.json

python3 tools/migration/aistio_migrate.py dry-run \
  --export .agentark/migration/aistio-export.ndjson \
  --config .agentark/migration/cutover.json \
  --plan .agentark/migration/plan.json \
  --report .agentark/migration/dry-run-report.json
```

Gate：Source Count 等于导出清单；复合 Primary Key 与 Foreign Reference 100%；Payload/Snapshot Hash 100%；所有时间转 UTC；所有状态可解释；Principal/Secret/Webhook/Asset 版本映射完整；Secret 为 `REFERENCE_ONLY`；每个大对象都有 Size/Checksum/MediaType；Team/CRD/Hosted Store 只出现已批准的 DEFER/REJECT。

## 3. Shadow Read

准备只读 Case，覆盖每个 Tenant/Cohort、空列表、分页边界、权限拒绝、归档状态和错误码。Go/Java 使用不同 Token File。每个 DTO 差异必须配置字段语义投影；安全 Case 禁止忽略 Role/Permission/Scope/Owner/Tenant/Secret 字段。

```bash
python3 tools/migration/aistio_shadow.py compare \
  --config .agentark/migration/cutover.json \
  --cases .agentark/migration/shadow-cases.json \
  --report .agentark/migration/shadow-report.json
```

Gate：Canonical Match ≥99.9%，5xx ≤0.1%，Java/Go p95 ≤1.20，Permission/Security/Secret Redaction 100%，任何 Java 权限放宽或任一侧出现 Secret 字段立即阻断。报告只含 Hash 和 JSON Pointer，不含响应值。

## 4. 迁移 Apply 与 Resume

先把 Control/Runtime/Scheduler Token 放到 `0600` 普通文件，配置仅引用路径。工具拒绝符号链接及组/其他用户权限。执行：

```bash
python3 tools/migration/aistio_migrate.py apply \
  --export .agentark/migration/aistio-export.ndjson \
  --config .agentark/migration/cutover.json \
  --checkpoint .agentark/migration/checkpoint.json \
  --report .agentark/migration/apply-report.json
```

失败后不删除 Checkpoint，修复根因后使用完全相同的 Export/Config/Plan 重跑。Plan Hash 或 Source Hash 变化必须重新 Dry Run 和审批。`IN_FLIGHT` 先按稳定 Key Reconcile，不能盲目重试。

验证：

- 每个旧 Agent Version 都有目标 Revision/Snapshot/Hash 映射；
- Deployment 指向请求的来源版本，而不是默认 Head；
- Cron/Webhook 进入 Scheduler Trigger，不留在 Control Deployment；
- 活动 Session 为 `GO_UNTIL_TERMINAL`；终态为 `ARCHIVE_ONLY`；
- Runtime Command `replayed=false`；
- Runtime Instance `requiresReregistration`；
- 大对象搬运完成后重新校验目标 ObjectRef Hash。

## 5. 灰度与 Java Primary

1. `SHADOW`：Go 返回，Java 对比；只读。
2. 按 Tenant/Capability Allowlist 进入 `JAVA_PRIMARY`：Java 返回，Go 只读对比。
3. 新 Session 只走 Java Deployment/Runtime；Go 活动 Session 继续原 Owner。
4. Scheduler 保持既有 `/internal/v1/runtime/turns` Client。
5. 监控 Error/Latency/Data/Audit/Quota/SSE Gap，达到观察窗口后进入 Java-only。

临时代理只绑定 Loopback：

```bash
python3 tools/migration/aistio_shadow.py serve \
  --config .agentark/migration/cutover.json \
  --checkpoint .agentark/migration/checkpoint.json \
  --report .agentark/migration/proxy-shadow-report.json
```

代理拒绝所有写请求。写流量灰度必须在 Gateway Cohort Route 完成，不经过代理。

## 6. 最终 Cutover

同时满足后切换：

- Go 写已停止，增量导出/Apply 和 Count/Hash/Reference 全通过；
- 无未解释 `IN_FLIGHT/FAILED`；
- 活动 Go Session 数为 0，或有逐 Session Owner/结束时间清单；
- Shadow Gate 连续通过整个批准窗口；
- Java Control/Runtime/Scheduler/Gateway 健康和 Audit 完整；
- Security Owner 确认 Secret/权限无放宽。

设置 `mode=JAVA_ONLY`，删除 Gateway 默认 Go Route、Go Service/Deployment、共享 HMAC/Internal Token 和 PostgreSQL写账号。AgentArk 默认 Compose 不需要修改；它已经 Java-only。

## 7. 观察与归档

观察窗口内保留：只读 PostgreSQL、备份 ObjectRef、Export、Plan、Checkpoint、Migration Report、Shadow Report、Gateway Route 变更、批准记录。Go Fallback 默认关闭；紧急启用时必须设置未来 24 小时内绝对 UTC 到期时间。

观察完成后：吊销 Go 写账号和共享 Token；归档临时代理配置；保留报告和工具版本；不删除 AgentArk 不可变 Revision/Snapshot/Event；PostgreSQL 删除按独立数据保留审批执行。
