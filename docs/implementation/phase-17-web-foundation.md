---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# Phase 17：AgentArk Web 工程基础、设计系统与 API/SSE Client

## 结论

Phase 17 已建立可独立安装、测试和构建的 `agentark-web`，但没有伪造 Phase 18 的完整业务页面。当前产物包含应用路由/Provider/错误边界、身份和租户上下文、AgentArk 独立设计系统、三套业务 Public OpenAPI Fetch Client、可靠 Runtime SSE Client、前端 CI 以及真实 Chromium 可访问性基线。2026-08-19 Errata 已将仅内存生产 Bearer 外壳替换为 Gateway BFF 会话恢复、动态身份登录和 CSRF 注入；E2E Bearer 仍只在 E2E Mode 存在。

上游只作固定版本参考：AgentScope Service Frontend 提供功能语义，DeepSeek Harness 提供工作台视觉和交互经验。没有复制上游源码、Logo、favicon、图片、品牌文案、Token 数值或 Plugin Runtime，也没有把上游 Package 加入依赖闭包。

## 工具链与可重复构建

| 能力 | 固定结果 |
|---|---|
| Node.js | `.node-version` 为 `24.14.1`，Engine 限制 24.x |
| pnpm | `packageManager=pnpm@11.22.0`，Lockfile 入库 |
| UI | React 19.2.8、Tailwind CSS 4.3.3、Radix UI、Lucide |
| Language/Build | TypeScript 6.0.3、Vite 8.2.1 |
| State/Router | TanStack Query 5.101.4、React Router 7.18.2 |
| Test | Vitest 4.1.10、Testing Library、Playwright 1.62.1、axe-core |

`.github/workflows/frontend.yml` 在 Web、Public Contract 或 JSON Schema 变化时执行 Frozen Install、生成漂移检查、Lint/Format、Typecheck、Unit、Build 和 Chromium E2E。Action 固定到 Commit、权限只读，并限制并发和超时。

## 应用外壳与状态边界

- Browser Router 使用 Lazy Route；Dashboard 与 Runtime Workspace 经过 Route Guard；
- `AppProviders` 装配 Query、Auth、Tenant、Feature Flag、Theme 与 Toast；
- `AppErrorBoundary` 和统一 Problem Detail 解析提供稳定错误投影；
- OIDC Token 只在 Gateway Redis Session，浏览器 JavaScript 不可见；CSRF、E2E Bearer 和 Tenant Selection 只在内存；
- Development Sign-in 在 Identity Overlay 下提供真实账号密码 OIDC 跳转，否则只提供受控预览；
- `/design-system` 提供无业务数据的 Story/测试页，Phase 18 再建立真实产品流程。

## Design System 与浏览器验证

设计系统提供浅色/深色主题、语义 Surface/Border/Status、Button/Input/Dialog/Popover/Menu/Tabs/Table、Split Pane/Inspector/Timeline/Code/JSON Viewer、Loading/Empty/Error/Skeleton 和 Toast。Radix 负责 Overlay 的基础可访问性，AgentArk 负责 Token、视觉和产品语义。

Unit 与 Chromium E2E 覆盖 Theme 持久化、Dialog 焦点恢复、Split Pane 键盘调整、390px 导航和页面横向溢出。axe-core 验证没有 `serious`/`critical` 违规；实际浏览器复核曾发现命令快捷键对比度不足和窄屏导航缺少名称，均在验收前修正。最终浏览器控制台为 0 Error / 0 Warning，favicon 使用 AgentArk 自有 SVG。

该证据不等于完整 WCAG 认证；屏幕阅读器、多浏览器、高对比和 200% 缩放仍需随 Phase 18 的真实页面继续验证。

## OpenAPI Client

`orval.config.ts` 将 Control、Runtime、Scheduler 三套 Public OpenAPI 生成到隔离目录。生成器只允许契约显式引用的仓库内 JSON Schema，不解析远程引用；`api:check` 在临时目录重新生成、使用同一 Prettier 格式化并逐文件比较，发现缺失、冗余或内容漂移即失败。

`shared/api/generated` 不作为 UI Domain。统一 HTTP 层负责：

- BFF CSRF、E2E 内存认证 Header 与 Tenant Intent；
- `If-Match`、`Idempotency-Key` 和 Cursor 参数；
- `same-origin` 凭据策略；
- RFC 9457 Problem Detail 解析、错误正文上限和 Secret 字段过滤。

Feature 层已有 Organization Query 封装，用于证明生成 Client 没有越过 UI Domain 边界。

## Runtime SSE Client

SSE 使用 Fetch Stream，以支持认证 Header 和 `Last-Event-ID`。Client 实现：

- Runtime Event v1 Schema 校验与版本拒绝；
- 任意字节分块、多行 Data、Heartbeat 和服务端 Retry 解析；
- Event ID 去重、有限容量内存 Store、终态停止；
- 指数退避、抖动、断点重连和页面隐藏/恢复；
- 无效单条 Event 隔离，未知或错误 Event 不拖垮整个流；
- 关闭 SSE 只关闭消费连接，不向 Runtime 发送 Cancel。

测试覆盖首次连接、重连 `Last-Event-ID`、重复 Event、终态、有界淘汰和非法 Event 后继续消费。

## 许可与上游边界

完整来源决策见 [Web 上游参考边界](../frontend/source-reference.md)。当前生产依赖锁文件的许可分类只有 `0BSD`、`Apache-2.0`、`ISC` 和 `MIT`；未复制任何固定上游资产。发布阶段仍必须生成 Web SBOM 和 Third-party Notice，不能用本阶段的本地许可清单替代发布审批。

## 最终验收记录

2026-08-17 在本机执行：

- `pnpm --dir agentark-web install --frozen-lockfile`：通过；
- `pnpm --dir agentark-web api:check`：临时重生成与已提交 Client 无差异；
- `pnpm --dir agentark-web lint`、`typecheck`、`build`：通过；
- `pnpm --dir agentark-web test`：7 个 Test File、12 项测试通过；
- `pnpm --dir agentark-web test:e2e`：2 项 Chromium E2E 通过；
- Playwright CLI 对 `/design-system` 做语义树、Console 和截图复核：0 Error / 0 Warning；
- `pnpm --dir agentark-web peers check`：无 Peer Dependency 问题；
- `pnpm --dir agentark-web licenses list --prod --json`：仅 `0BSD`、`Apache-2.0`、`ISC`、`MIT`；
- 固定上游校验、知识门禁、品牌/Plugin Runtime 扫描和 `git diff HEAD --check`：通过。

2026-08-19 BFF Errata 追加验证：

- `pnpm --dir agentark-web lint`、`typecheck`、`build` 和 `api:check` 全部通过；
- Vitest 增至 9 个 Test File、15 项测试，新增 BFF Session 恢复、CSRF Header、匿名失败关闭和无生产 Token 文案覆盖；
- 真实 Chromium 验证当时的本地 Keycloak 与组织身份 BFF；该本地实现已由 2026-08-21 Built-in Identity Errata 替代。

2026-08-21 Built-in Identity Errata：默认登录改为 Gateway 同源“用户名或电子邮箱 + 密码”，支持随机初始管理员、首次强制改密和 `/govern/users` 账号治理；MySQL 保存 Argon2id 摘要和安全事实，Redis 只保存 Session/限流，浏览器不保存密码或 Token。外部 OIDC BFF 仅在部署方显式切换组织身份模式时启用。

## 已知边界

- 本地 Docker 验证只证明 Built-in Identity、Redis Session 和账号治理主链路，不代表目标企业 IdP、生产 TLS、邮件找回、MFA/Passkey 或目标环境 Secret Manager 已验收；
- 当前 E2E 只执行 Chromium，Firefox/WebKit 和屏幕阅读器矩阵尚未覆盖；
- 初始公共 UI 与应用入口 gzip 分别约 80 KiB 和 86 KiB，已按路由拆分，但 Phase 18 引入业务 Feature 时仍需建立预算；
- API Client 只来自当前 Public Contract；业务页面必须尊重后端实际 Endpoint 和权限，不能由生成类型推断不存在的流程；
- Web 静态部署、CSP/反向代理生产装配和发布 Artifact 归 Phase 20/23。

## 回滚

本阶段没有数据库迁移、后端 API 修改或业务数据写入。回滚可删除 `agentark-web` 和前端 CI，撤销 README、AGENTS、架构/迁移文档、知识地图、`.gitignore` 与 PLAN 的 Phase 17 更新。生成 Client 可由 Lockfile 和 Public Contract 随时重建，不需要清理数据库、Redis 或对象存储。
