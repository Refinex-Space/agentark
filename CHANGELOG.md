# Changelog

AgentArk 使用语义化版本记录对外可见变化。首个完整开发基线之前的阶段证据保留在 `PLAN.md` 与 `docs/implementation/`，不在此重复审计流水。

## [0.1.0] - 2026-08-18

### Added

- 建立 Gateway、Control、Runtime、Scheduler 四个独立部署单元及独立数据所有权。
- 建立 IAM、多租户、AI 资产、Knowledge、不可变 Agent Revision/Snapshot、Deployment 与治理模型。
- 建立 Provider 中立 Runtime、AgentScope Java 2.0.2 防腐层、持久 Event、SSE、HITL、Lease/Fencing、恢复与 Scheduler。
- 建立 AgentArk Web 的 Govern → Build → Publish → Deploy → Run → Approve → Observe 产品流程。
- 建立 MySQL/Redis/Object/Qdrant 本地基线、生产容器、Helm、NetworkPolicy、备份恢复、性能与故障演练。
- 建立 OpenAPI、AsyncAPI、JSON Schema、CycloneDX、许可证、镜像签名和 Provenance 门禁。

### Security

- 默认失败关闭 OIDC/JWT、API Key、Service Identity、SecretRef、租户隔离、MCP SSRF、Skill 供应链和 Sandbox 安全边界。
- 增加 Threat Model、Secret 轮换、安全事件和灾难恢复 Runbook。

### Compatibility

- 固定 AgentScope Maven 运行依赖为 `2.0.2`，固定源码审计 Commit 为 `0c61e7494197ded54eefdeaf9bdeb51807beb752`。
- 冻结 Snapshot、Runtime Event、Public/Internal OpenAPI 与迁移契约的 0.1.0 摘要基线。

### Known limitations

- 本版本是完整开发基线，不代表任何具体生产环境已经通过身份、KMS、托管服务、跨区容量或灾难恢复审批。
- 详细限制和发布证据见 `docs/releases/v0-1-0.md`。
