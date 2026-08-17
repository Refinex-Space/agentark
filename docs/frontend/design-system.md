---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# AgentArk Web 设计系统

## 目标与边界

AgentArk Web 面向 Agent 构建、发布、运行和治理工作流，采用紧凑但可读的开发者工作台布局。设计语言由 AgentArk 自己拥有；上游只提供层级和交互参考，不复制品牌、Token 数值、Logo、组件源码、截图或文案。

可执行组件基线位于 `/design-system`。它没有业务数据和认证能力，只用于验证 Token、主题、组件状态、键盘行为和响应式布局；Phase 18 产品页在同一语义 Token 和组件基线上组合真实业务状态。

## Token

Token 定义在 `agentark-web/src/app/styles/global.css`，组件只能通过语义 Token 表达颜色和层级：

| 分类 | 语义 |
|---|---|
| Canvas/Surface/Raised | 页面背景、基础表面、浮起面板 |
| Text/Muted/Subtle | 主文本、辅助文本、低优先信息 |
| Border/Strong Border | 普通边界、交互或高层级边界 |
| Accent | 主操作、焦点和选中状态 |
| Success/Warning/Danger | 成功、等待/风险、失败/破坏性状态 |
| Focus Ring | 键盘焦点，不以颜色作为唯一信息 |

浅色、深色和系统主题由 `ThemeProvider` 管理。只有主题偏好可以写入浏览器存储；身份凭据、租户上下文和 Runtime Event 禁止持久化。

## 组件基线

| 范围 | 组件 | 约束 |
|---|---|---|
| 输入与动作 | Button、Input | Disabled、Focus、Danger 均有独立语义 |
| Overlay | Dialog、Popover、Menu | Radix 负责焦点圈定、Escape 和焦点恢复 |
| 导航与数据 | Tabs、Table | 保留原生语义；窄屏表格允许局部滚动 |
| 工作台 | Split Pane、Inspector、Timeline | 分栏支持键盘调整和 ARIA 数值 |
| 技术内容 | Code Viewer、JSON Viewer | 只展示已脱敏、允许暴露的投影 |
| 反馈 | Loading、Skeleton、Empty、Error、Toast | 不只依靠颜色表达状态；Toast 不主动夺取焦点 |

页面级错误由 `AppErrorBoundary` 接管，API 错误统一解析 RFC 9457 Problem Detail；Feature 不自行拼装互不兼容的错误形态。

## 可访问性基线

- 所有交互元素可用键盘访问，并保留清晰 `:focus-visible`；
- Dialog 打开后聚焦内部，关闭后返回触发器；
- Split Pane 暴露 `separator`、当前百分比和方向键调整；
- 导航、主内容、通知和 Inspector 使用正确 Landmark；
- `prefers-reduced-motion` 下关闭非必要动画；
- Chromium E2E 使用 axe 检查 `serious`/`critical` 违规，并验证 390px 窄屏无页面级横向溢出；
- Phase 18 真实 Run 页面重复执行 axe，Operate 页面在 390px 窄屏验证主导航和根节点宽度；
- 颜色与字体变化后必须重新执行浏览器对比度检查，不能只依赖 jsdom。

以上是 WCAG 2.2 AA 工程基线，不等于完整人工无障碍认证。屏幕阅读器矩阵、缩放 200%、高对比模式和多浏览器验证仍需在产品页面形成后补充。

## 变更规则

1. 先新增或调整语义 Token，再修改组件，不在 Feature 中散落品牌色；
2. 新组件必须覆盖正常、加载、空、错误、禁用和键盘状态中实际适用的部分；
3. 危险操作必须同时展示动作、对象和环境，真实确认流程由对应业务 Feature 完成；
4. 不将 Prompt、Secret、完整 Event Payload 或隐藏推理链加入演示 Fixture；
5. 修改共享组件后至少执行 Unit、Playwright 和生产 Build。
