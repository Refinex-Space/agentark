---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# AgentArk Web 上游参考边界

## 固定证据

| 来源 | 固定 Commit | 实际入口 | 分类 |
|---|---|---|---|
| AgentScope Java | `0c61e7494197ded54eefdeaf9bdeb51807beb752` | `agentscope-service/frontend/src/main.tsx`，Package `agentscope-service-frontend` | `REFERENCE` |
| DeepSeek Harness | `47f943859bef60e4160492346772ded9b24f765a` | `apps/web/src/main.ts`，Package `@deepseek-ai/dsh-web-frontend`；Shell 位于 `packages/client/web*` | `REFERENCE` |

审计只读取 `.agentark/upstreams/` 的 detached Worktree。Phase 17–18 没有修改固定视图，也没有把上游源码、资源、品牌或运行时依赖迁入 `agentark-web`。

## AgentScope Service Frontend

取用范围：

- Agent、Environment、Session、Event、HITL、Team 的产品语义；
- API Client 与 Event 投影需要覆盖的字段和错误场景；
- TanStack Query、Router、Radix、Tailwind 的工程组织经验。

不取用范围：

- React 页面、组件、样式、状态管理和 API Client 源码；
- 上游 Entity/DTO 作为 AgentArk UI Domain；
- 上游品牌、页面结构或 Dataplane 私有协议。

AgentArk Client 只从自己的 Public OpenAPI 生成；Runtime Event 只接受 AgentArk v1 Envelope。

## DeepSeek Harness

取用范围：

- Sidebar/Header/Command/Panel 的工作台层级；
- 深色主题、终端感、Timeline、Inspector、Editor/Split Pane 的交互意图；
- pnpm、TypeScript、Vitest 和薄 Web Entry/独立 Shell 的工程经验。

明确拒绝：

- DeepSeek Logo、名称、商标、favicon、社区图片和产品文案；
- `--dsw-*` Token 整套数值、React/CSS 组件源码、测试 Snapshot；
- Cordis 或 everything-is-a-plugin 应用内核；
- Native、Python、Plugin Runtime、官方平台 Payload；
- 许可未核对的字体、Glyph、图标和图片。

Phase 17–18 的 favicon、Token、布局、文案、组件和产品 Feature 均为 AgentArk 独立实现，图标来自锁定的 Lucide npm 依赖。Phase 18 只沿既有 `REFERENCE` 边界借鉴工作台层级、Timeline/Inspector 意图和上游功能语义，没有复制页面、组件、样式、测试 Snapshot 或品牌资产。

## 依赖与许可

`pnpm-lock.yaml` 是 AgentArk Web 自己的依赖闭包。2026-08-17 对生产依赖执行 `pnpm --dir agentark-web licenses list --prod --json`，出现的 SPDX 分类为 `0BSD`、`Apache-2.0`、`ISC`、`MIT`；这只是当前锁文件的工程证据，不替代发布时生成 Web SBOM/第三方 NOTICE。

任何候选从 `REFERENCE` 提升到 `ADAPT/REUSE`，必须先更新 `docs/migration/migration-manifest.md` 和 `docs/migration/license-and-notice.md`，记录源文件、目标路径、版权/许可证和不可替代性，再实施代码迁入。
