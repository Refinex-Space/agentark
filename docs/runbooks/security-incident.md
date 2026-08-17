---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md#安全运维
---

# 安全事件响应 Runbook

## 分级与职责

| 级别 | 示例 | 首要动作 | Owner |
|---|---|---|---|
| SEV-0 | 活跃 Secret 外泄、跨租户读取、Sandbox Escape、供应链签名私钥泄漏 | 立即隔离、吊销、停止发布并保全证据 | Security Incident Commander |
| SEV-1 | MCP SSRF、Internal API 冒用、陈旧 Fencing 写成功、高危制品已部署 | 关闭受影响能力、回滚/隔离、轮换身份 | 对应平面 Owner + Security |
| SEV-2 | 被门禁阻断的高危依赖、Webhook 重放尝试、异常成本耗尽 | 阻断合并、限制额度、调查来源 | Release/Runtime/Scheduler Owner |

任何人发现疑似 Restricted 数据时不得在聊天、Issue、邮件或普通日志中粘贴原值。只记录事件 ID、资源 ID、时间、Hash、状态和证据位置。

## 前 15 分钟

1. 指定 Incident Commander 和记录员，生成不可预测的事件 ID。
2. 判断是否存在持续外传、跨租户访问或供应链发布；有则停止对应入口/Worker/发布流水线。
3. Secret 事件立即 `:disable` 或 `:revoke` Metadata，同时在外部 Provider 吊销版本/身份。
4. API Key 事件吊销摘要记录并清理 Gateway 短 TTL 缓存；OIDC 事件由 IdP 撤销会话/密钥。
5. MCP 事件从部署白名单移除主机并停止受影响 Deployment；Sandbox 事件隔离 Namespace/Node，禁止继续调度。
6. 供应链事件冻结 Release，吊销签名身份，保留 Artifact Digest、SBOM、Attestation 和工作流 Run ID。

## 遏制与证据

- 保留 append-only Audit、Runtime Event、Scheduler Job/Delivery、Gateway Request/Trace ID 和外部 Provider Access Log。
- 导出时先执行租户授权和脱敏；不要修改原始数据库记录或重写历史 Migration。
- 对受影响 Run/Job 使用正式 Cancel/Disable，不直接更新表状态。
- 对陈旧 Worker 依赖 Lease 到期和 MySQL Fencing 拒绝写；如需隔离实例，先停止新外部调用再排空。
- 对镜像/Skill/MCP 固定 Digest、Hash、Key ID、DNS 地址集合和扫描版本；不要只记录标签或主机名。

## 恢复条件

只有同时满足以下条件才恢复：根因已定位；攻击路径关闭；所有暴露身份已轮换；跨租户/Secret/Sandbox 回归测试通过；SCA/Secret/IaC/CodeQL 门禁通过；Audit 连续性验证；业务 Owner 与 Security 双人批准。生产恢复使用新 Revision/Deployment 或已知安全 Revision 的指针回滚，不修改已发布 Snapshot。

## 事件后

在 48 小时内更新 Threat Model 风险状态、检测缺口、Owner 和截止日期。若涉及许可证/品牌/依赖来源，同时更新迁移 Manifest、NOTICE 与 SBOM。若实际控制和本文不一致，以安全失败关闭为先，并在恢复前修正文档和实现。

## 常用验证入口

```bash
./mvnw -T 1C clean verify
pnpm --dir agentark-web lint
pnpm --dir agentark-web test
pnpm --dir agentark-web build
./tools/security/scan-repository.sh
python3 tools/harness/knowledge_gate.py
git diff HEAD --check
```

生产 Kubernetes 还必须执行实际 Egress、Privilege、Admission、mTLS 和镜像签名验证；本地静态清单成功不等价于事件恢复批准。
