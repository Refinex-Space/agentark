---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# Phase 18 前端交互与截图证据

## 产品主链路

AgentArk Web 以真实 Gateway Public API 为唯一浏览器业务入口，主链路固定为：

```text
Govern → Build → Release → Runtime → Approval → Operate
```

| 环节 | 用户动作 | 页面反馈 | 服务端事实 |
|---|---|---|---|
| Govern | 选择 Organization、Project、Environment，管理成员、角色、Service Account、API Key 和 Secret 元数据 | 权限拒绝使用稳定 Problem Code/Trace ID；API Key 明文只展示一次 | IAM、授权和租户隔离仍由 Control 验证，Tenant Header 只表达选择意图 |
| Build | 配置版本化 Prompt、Model、MCP、Skill、Profile、Policy 和 Knowledge，编辑 Agent Draft | Draft 与 Query Cache 分离；引用选择器只显示可访问的可用版本 | Draft 可修改，资产版本不可变，Skill Artifact 由 Object Store 保存 |
| Release | 验证、发布、检查 Snapshot/Diff、创建 Deployment、Promote 或 Rollback | 发布与生产环境操作必须确认；乐观锁冲突不做静默覆盖 | Revision/Snapshot 不可变，Deployment 只移动期望 Revision 指针 |
| Runtime | 从 Deployment 创建 Session/Turn，观察 Timeline、消息流、调用树、Usage 和 Artifact | SSE 显示连接/重连状态；关闭流不等于取消 Run | Session 固定 Revision/Snapshot，Event 先持久化后推送 |
| Approval | 检查 Tool、参数摘要 Hash、Risk 和 Policy 后 Approve/Reject | 重复、过期或无权限决策显示稳定错误；可返回关联 Run | Approval 终态由 Runtime 保存；统一 Audit 检索归 Phase 19 |
| Operate | 查看 Trigger、Job、Retry、Dead Letter、Webhook/Channel、Knowledge Ingestion 和 Runtime/Deployment 摘要 | Redrive、取消和写操作以服务端结果为准 | Scheduler 拥有 Job，Runtime 拥有 Run，Control 拥有 Knowledge/Deployment |

## 视觉与交互证据

真实 E2E 在每次运行时生成以下本地截图；这些图片位于忽略目录，不作为产品资产或稳定像素快照提交：

| 本地输出 | 证明范围 |
|---|---|
| `agentark-web/output/playwright/phase-18-run.png` | Session/Run 固定信息、持久 Event Timeline、Message Streaming、Approval 恢复、调用树和 Usage 入口 |
| `agentark-web/output/playwright/phase-18-operate.png` | Trigger/Job、Dead Letter/Redrive、Webhook/Channel 投影、Knowledge Ingestion 和 Runtime/Deployment 摘要 |

截图由 `pnpm --dir agentark-web test:e2e:real` 重建。测试使用临时 MySQL/Redis、四个真实 Spring Boot 服务、RS256 JWT 和确定性测试执行引擎；成功或失败后都按 Manifest 精确清理临时进程、容器、日志和会话文件。

## 可访问性与响应式检查

- 真实 Run 页面运行 axe，`serious`/`critical` 违规必须为零；
- 390 × 844 窄屏下主导航保持可访问，页面根节点不得横向溢出；
- 发布 Dialog 用 Escape 关闭并恢复焦点，工作台保留清晰 `focus-visible`；
- Timeline、表格、状态和错误不只依靠颜色传达信息；
- Reduced Motion、屏幕阅读器、多浏览器和 200% 缩放仍需后续专项人工验收，当前证据不等于完整 WCAG 认证。

## 安全边界

- 生产构建不包含 E2E 临时身份入口、Token、Mock 或测试会话；
- Secret 值不回显，API Key 明文仅在创建响应中短暂显示；
- Event Inspector 只展示稳定 AgentArk 投影，不展示隐藏推理链；
- 浏览器不能访问 Internal API、数据库或 Provider 私有类型；
- 截图、日志和测试会话均为本地运行产物，不作为凭据或事实存储。
