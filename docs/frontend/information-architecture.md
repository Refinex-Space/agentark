---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# AgentArk Web 信息架构

## 产品结构

最终控制台沿 Agent 生命周期组织，而不是照搬任一上游页面树：

```text
Overview
├── Build：Agent、Prompt、Model、MCP、Skill、Knowledge、Profile、Policy
├── Release：Validation、Revision、Deployment、Environment
├── Operate：Session、Run、Event、Approval、Job、Channel、Webhook
└── Govern：Organization、Project、Member、Role、Secret、Audit、Usage、Setting
```

Organization、Project、Environment 是全局上下文，但它们只是前端选择。资源归属和权限始终由 Gateway 后的 Owner 服务验证，Header 不能形成授权事实。

## Phase 17 已落地路由

| 路径 | 访问边界 | 目的 |
|---|---|---|
| `/` | Route Guard | 产品总览外壳和上下文状态 |
| `/runtime` | Route Guard | Runtime 工作区布局与连接状态预览 |
| `/design-system` | 公开、无业务数据 | 组件、主题和可访问性基线 |
| `/sign-in` | 公开 | 身份外壳；开发预览只在 Development 暴露 |
| `*` | 公开 | 稳定 Not Found 页面 |

Phase 17 没有虚构未实现的 Agent/资产/发布 CRUD 页面。它们应在 Phase 18 基于真实 Public OpenAPI、权限和状态机逐项实现。

## 工程分层

```text
src/
├── app       # Router、Provider、Layout、Theme、Error Boundary
├── features  # 用户意图与 Query/Mutation 编排
├── entities  # Auth Session、Tenant Context 等 UI 可消费实体
├── shared    # 生成 Client、HTTP/SSE、安全工具和通用 UI
└── widgets   # App Shell、跨 Feature 组合区
```

依赖方向为 `app/widgets/features -> entities/shared`。`shared/api/generated` 是契约生成边界，不拥有 UI 语义；Feature 将生成请求/响应映射为页面需要的状态。页面不得绕过 Feature 直接建立跨平面调用。

## 全局状态

| 状态 | Owner | 生命周期 |
|---|---|---|
| Server State | TanStack Query | 按 Query Key 和失效策略管理 |
| Bearer/API Key | Auth Session | 仅当前页面内存 |
| Organization/Project/Environment | Tenant Context | 仅当前页面内存 |
| Theme | Theme Provider | 可持久化非敏感偏好 |
| Runtime Event | SSE Client 有界 Store | 当前 Run/页面会话，不持久化 |
| 表单草稿 | 对应 Feature | 离开流程按产品规则处理 |

## API 与流式事件

Control、Runtime、Scheduler 的 Public OpenAPI 分别生成隔离 Fetch Client。统一 HTTP 层负责：

- 内存凭据、Tenant Intent、ETag/If-Match、Idempotency-Key；
- 同源 Cookie 策略和 Problem Detail 解析；
- 错误正文大小上限与敏感字段过滤；
- Cursor 参数传递，Feature 负责翻页状态。

Runtime SSE Client 使用 Fetch Stream，而不是浏览器 `EventSource`，从而显式携带认证和 `Last-Event-ID`。它先校验 Runtime Event v1，再去重并进入有界内存；页面隐藏时中断网络读取，恢复后从持久 Event ID 回放。关闭页面连接不等于取消 Run。

## 后续实现约束

- Phase 18 的页面必须沿 Build → Release → Operate → Govern 主链路落位；
- 路由可见性不是权限控制，按钮隐藏也不能替代服务端授权；
- 发布、部署、审批和重试不做危险乐观更新；
- Timeline/Inspector 展示稳定 AgentArk Event 投影，禁止暴露隐藏推理链或序列化 Provider Event；
- 对 Session、Approval 和 Job 的实时更新优先从持久事件恢复，不把浏览器内存当事实源。
