# Security Policy

## 支持范围

`0.1.x` 是当前受支持的开发基线。它提供安全架构和验证工具，但不构成生产环境认证；部署方仍须完成自己的身份、Secret Manager、网络、镜像、托管数据服务和灾难恢复审批。

## 报告漏洞

不要在公开 Issue、日志、截图或测试数据中披露漏洞利用细节、凭据或租户数据。请使用 GitHub 仓库的 Private Vulnerability Reporting 或 Security Advisory 私下提交：

- 受影响版本、Commit 与组件；
- 可复现条件和最小验证步骤；
- 潜在的租户、Secret、数据完整性、可用性或供应链影响；
- 已知缓解方式，但不要附带真实生产凭据或数据。

维护者会先确认接收并评估严重度、受影响版本和临时缓解，再决定修复、公告与发布窗口。公开时间应在修复和受影响部署方获得合理升级窗口后协调确定。

## 安全边界

安全设计、威胁模型和应急流程分别位于：

- `docs/security/security-architecture.md`
- `docs/security/threat-model.md`
- `docs/runbooks/security-incident.md`
- `docs/runbooks/secret-rotation.md`

任何绕过认证、租户过滤、Fencing、审批参数 Hash、MCP SSRF、Skill 校验、Sandbox 或发布签名的变更都不被接受。
