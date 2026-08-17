# AgentArk Web

AgentArk Web 是独立构建的产品控制台。Phase 17 建立应用外壳、设计系统、认证与租户上下文、公共 OpenAPI Client 和 Runtime SSE Client；Phase 18 已基于真实 Gateway API 建立 Govern → Build → Release → Runtime → Approval → Operate 产品主链路。

## 工具链

- Node.js：`.node-version` 锁定的 24 LTS；
- pnpm：`package.json#packageManager` 精确锁定；
- React、TypeScript、Vite、Tailwind CSS、Radix UI、TanStack Query 与 React Router：版本以 `package.json` 和 `pnpm-lock.yaml` 为准；
- Vitest、Testing Library 与 Playwright：分别验证组件/Client 和真实 Chromium 交互。

## 本地命令

从仓库根目录执行：

```bash
pnpm --dir agentark-web install --frozen-lockfile
pnpm --dir agentark-web dev
pnpm --dir agentark-web lint
pnpm --dir agentark-web typecheck
pnpm --dir agentark-web test
pnpm --dir agentark-web build
pnpm --dir agentark-web test:e2e
pnpm --dir agentark-web test:e2e:real
```

公共契约变化后重新生成 Client：

```bash
pnpm --dir agentark-web api:generate
pnpm --dir agentark-web api:check
```

`src/shared/api/generated/` 只由 `contracts/openapi/public-*-v1.yaml` 和其仓库内 JSON Schema 生成，不手工修改。Feature 层必须封装 Query/Mutation，不能把生成模型直接当作 UI Domain。

## 安全边界

- Bearer Token 与 API Key 只保存在当前页面内存，不进入 Local Storage、Session Storage、URL 或日志；
- Organization、Project、Environment 选择仅表达客户端意图，服务端仍独立完成授权；
- SSE 使用 Fetch 流以携带认证和 `Last-Event-ID`，本地 Event Store 有界且不持久化；
- Vite 仅将 `/api` 代理到配置的 Gateway，浏览器不直接访问 Control、Runtime 或 Scheduler 内部接口；
- `/design-system` 是无业务数据的公开组件基线页，不代表真实身份提供商已经接入。

进一步阅读：

- [信息架构](../docs/frontend/information-architecture.md)
- [设计系统](../docs/frontend/design-system.md)
- [上游参考边界](../docs/frontend/source-reference.md)
- [Phase 17 执行证据](../docs/implementation/phase-17-web-foundation.md)
- [Phase 18 执行证据](../docs/implementation/phase-18-web-features.md)
- [Phase 18 交互与截图证据](../docs/frontend/phase-18-interactions.md)
