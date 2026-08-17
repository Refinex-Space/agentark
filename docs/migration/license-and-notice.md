---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Phase 01 许可证、NOTICE 与第三方资产清单

## 1. 结论

| 来源 | 固定证据 | 当前结论 |
|---|---|---|
| AgentScope Java | POM/README 声明 Apache-2.0；Java/Go/SQL 源文件保留 Apache-2.0 Header；Phase 02 已补齐同版本发布物与官方许可证文本证据 | 可作为依赖与行为参考；内部机械证据基线的许可 Gate 已解除，最终分发仍必须补齐自身 SBOM/License Report |
| DeepSeek Harness | 根 `LICENSE` 为 MIT；`THIRD_PARTY_NOTICES.md` 为生成清单；Vendored Cordis 等各自保留 LICENSE | 只作视觉/交互参考；不复制品牌、Logo、社区资产、插件内核或受特殊条款约束的官方负载 |

本文件不是法律意见。它定义 AgentArk 工程门禁：许可不明确时降级为 `REFERENCE/DEFER/REJECT`，不先复制再补材料。

## 2. AgentScope 证据缺口

固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752` 中：

- 根 `pom.xml` `<licenses>` 声明 Apache License 2.0；
- `README.md` 声明 “released under Apache License 2.0” 并链接 `./LICENSE`；
- Java、Go、SQL、POM 等检查到的源码头均使用 2024–2026 original author(s) + Apache-2.0 Header；
- `.licenserc.yaml` 和 CI License Check 存在；
- `git ls-tree` 未找到根或模块级 `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES`；
- `git show HEAD:LICENSE` 失败，README 的 `./LICENSE` 链接在该 Commit 内失效。

这不推翻上游的 Apache-2.0 声明，但使“完整再分发许可包”证据不足。Phase 02 若要机械迁入源码，必须先从同一上游发布物或 Maintainer 确认渠道取得与该版本匹配的许可证文本，记录来源和校验值，并确认是否存在 NOTICE 内容。不得从不相关 Commit 悄悄拿一份文件当作固定版本证据。

## 3. AgentScope 文件级处理规则

| 操作 | 必须执行 |
|---|---|
| Maven 依赖 | 锁定 2.0.2 与仓库；生成依赖清单/SBOM；检查实际 JAR 的 `META-INF/LICENSE*`/`NOTICE*`；记录传递依赖许可 |
| `REUSE` 单文件 | 保留完整版权与 Apache Header；在 `docs/migration/` 记录源 Commit、源路径、目标路径、改动；把完整 LICENSE/NOTICE 纳入发布物 |
| `ADAPT` 重写 | 保留行为来源记录；若表达性实现来自原文件，按衍生作品处理并保留必要 Header，不用“重命名”规避义务 |
| `REFERENCE` | 不复制表达性源码、注释、测试 Fixture 或文档大段文本；只实现由事实/API/行为得到的独立代码 |
| 生成代码/CRD/OpenAPI | 检查生成器和源文件 Header；生成物不能自动视为无许可要求 |
| 图片/文档 | 单独核对来源和授权；README 截图不进入 AgentArk 资产 |

候选 `REUSE` 目前只有纯映射/纯键类，仍然是“待许可 Gate 的候选”，不是复制授权。

## 4. AgentScope 第三方依赖风险面

AgentScope POM/BOM 覆盖 Reactor、Jackson、MCP、OkHttp、OpenTelemetry、Redis/Jedis/Redisson、MySQL/PostgreSQL、对象存储、Kubernetes、Qdrant/Milvus/Elasticsearch、A2A、Model Provider SDK 等。`agentscope-extensions` 还会扩大运行闭包。

因此后续依赖策略必须：

1. 仅引入实际使用的 Core/Harness/单个 Extension，不依赖整个 Extensions 聚合；
2. 以构建出的实际 Runtime Classpath 生成 SBOM 和 License Report；
3. 将测试/开发依赖与分发 Runtime 依赖区分；
4. 对 Model/Sandbox/RAG/Channel Provider 的 SDK 条款逐个审查；
5. 禁止用 BOM 声明代替最终 Artifact 许可检查。

## 5. DeepSeek Harness 许可事实

固定 Commit `47f943859bef60e4160492346772ded9b24f765a` 的根 `LICENSE` 是 MIT，Copyright `(c) 2026 DeepSeek`。`apps/web` 与 `packages/client/ui-primitives` 等 Package Manifest 也声明 MIT。

`THIRD_PARTY_NOTICES.md` 由 `scripts/gen-third-party-notices.ts` 生成并要求 `pnpm run verify-third-party-notices` 验证。它记录：

- `vendor/` 中 Cordis/Cosmokit/Schemastery 等为 MIT，且各目录保留 LICENSE；
- Runtime npm 依赖含 MIT、Apache-2.0、BSD、ISC 等多种许可；
- 开发工具包含 LGPL-3.0-only 与 MPL-2.0，但清单声明它们只用于开发、不进入分发物；
- `@anthropic-ai/claude-agent-sdk` 与平台 Payload 的 declared license 为 `SEE LICENSE ...`，上游的项目所有者授权不自动转授给 AgentArk；
- First-party Landlock Package 为 BSD-3-Clause。

AgentArk 只借鉴视觉/交互，不复制 DeepSeek 源码，因此不继承其整个 Third-party Runtime Closure。若后续决定复制任何 MIT 代码，仍必须保留 MIT Copyright/Permission Notice，并重新计算 AgentArk 自己的分发闭包。

## 6. DeepSeek 品牌与资产边界

| 资产/实现 | 结论 | 原因 |
|---|---|---|
| `FishLogo.tsx`、`BrandWordmark.tsx`、DeepSeek 名称 | REJECT | 品牌/标识不因代码 MIT 就自动成为 AgentArk 视觉资产 |
| `assets/community-*.png` 三张社区图片 | REJECT | 与 AgentArk 产品无关，包含社区/品牌传播内容 |
| `apps/web/public/favicon.svg` | REJECT | 上游产品标识 |
| `ui-primitives` Glyph/Icon | REJECT 直接复制 | 上游 README 明示部分图标是从不可导出字体字形手工近似重绘，来源链不适合作为 AgentArk 图标授权依据 |
| `--dsw-*` Token 名与整套数值 | REFERENCE | 可研究层级/密度；AgentArk 使用自有 Token 命名和视觉规范 |
| CSS/React 组件代码 | REFERENCE | 只复建交互意图；避免形成 DeepSeek UI Fork |
| Browser Snapshot/Fixture | REJECT 复制 | 可能固化上游品牌、文案、像素资产和内部协议 |
| 官方 Claude Platform Payload | REJECT | 特殊条款和身份范围授权不转移；AgentArk 当前也不需要分发 |

## 7. AgentArk 发布物门禁

在任何可分发 Artifact 形成前必须具备：

- 根 `LICENSE` 与 AgentArk 自身版权策略；
- `NOTICE` 或 Third-party Notices（即使最终为空也要有生成规则和审计证据）；
- Java SBOM/依赖许可报告、Web Lockfile 许可报告、Container SBOM；
- 每个源码迁入文件的 Source Commit/Path/Target/Classification 记录；
- 品牌和图片 Asset Inventory，含来源、作者、许可、修改与用途；
- 对非宽松、source-available、`SEE LICENSE`、未知许可的显式拒绝或风险接受；
- CI 中的 Notice 漂移、Header、Secret 和 SBOM Gate。

## 8. Phase 02 许可 Go/No-Go

Phase 02 机械迁入开始前同时满足才是 Go：

1. 固定 AgentScope 2.0.2 的完整 LICENSE 文本来源已确认并校验；
2. NOTICE 是否存在/是否为空已有上游或发布物证据；
3. 迁入文件级 Manifest 已列出 Header 与目标路径；
4. 机械迁入隔离 Worktree 的发布物能携带许可证包；
5. AgentScope Maven 依赖实际 JAR 的 `META-INF` 和传递许可已检查。

任一项不满足时，允许继续依赖/行为测试/独立重写，但不允许复制 AgentScope 源文件。

### Phase 02 补证结论

截至 2026-08-15，上述 Gate 对“独立、内部、不可整体合并的机械证据基线”为 **GO**：

1. Maven Central 同版本 Core/Harness POM 明确声明 Apache License 2.0，并指向 Apache Software Foundation 官方文本；机械基线携带该文本及 SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`；
2. 固定 Git Tree、官方 2.0.2 Tag 祖先、Core/Harness 的 POM、binary JAR 和 sources JAR 均未发现 NOTICE，因此不伪造空的上游 NOTICE；
3. 658 个证据文件已写入 `mechanical-import-files.sha256`，源 Commit、路径、Tree、调整和测试结果见 [机械迁入报告](mechanical-import-report.md)；
4. 隔离基线携带许可证文本，且与 AgentArk 最终实施 Worktree 分离；
5. 实际 Core/Harness JAR 已检查，确认它们没有打包 `LICENSE`/`NOTICE`，这被记录为上游发布包装风险而非静默忽略。

该 GO 不等于发布审批。AgentArk 可分发 Artifact 仍必须对实际 Runtime Classpath 生成 SBOM/License Report，并随发布物携带 AgentArk 与第三方适用的 LICENSE/NOTICE。Phase 02 只锁定 Core/Harness 依赖，不引入整个 Extensions 聚合。

## 9. Phase 17 Web 许可基线

Phase 17 没有复制 AgentScope Service Frontend 或 DeepSeek Harness 的源码、组件、Token、品牌、Logo、favicon、图片、Glyph、Snapshot 或产品文案。AgentArk favicon、CSS Token、React 组件和页面 Fixture 均为独立实现；Lucide 图标通过 AgentArk 自己锁定的 npm 依赖使用。

2026-08-17 对 `agentark-web/pnpm-lock.yaml` 的生产依赖执行 `pnpm --dir agentark-web licenses list --prod --json`，当前 SPDX 分类为 `0BSD`、`Apache-2.0`、`ISC`、`MIT`，`pnpm --dir agentark-web peers check` 无 Peer Dependency 问题。该结果只证明当前锁文件闭包，不是法律意见，也不替代 Phase 23 的 Web SBOM、第三方 NOTICE 和实际静态发布物检查。

若后续把任一上游前端候选从 `REFERENCE` 提升为 `ADAPT/REUSE`，必须在迁入前记录源 Commit、源路径、目标路径、文件版权、许可证文本和修改范围；品牌或来源不明确的资产继续 `REJECT`，不能因代码仓库使用宽松许可证就推断商标或图片可复用。

## 10. Phase 20 供应链与 Notice 收官

Phase 20 新增根 `THIRD_PARTY_NOTICES.md`，它不手抄会漂移的依赖闭包，而是固定发布物必须重新生成和复核的证据位置：Maven License Report、Maven CycloneDX、Web Lockfile License Report 和 Trivy 仓库级 CycloneDX。发布流程通过 GitHub OIDC 签发 SBOM/Build Attestation；内容寻址镜像先扫描再由 Cosign Keyless 签名。

安全工具本身只在构建/扫描流程使用，不进入 AgentArk Runtime 分发物。Trivy 容器固定官方多架构 Digest，GitHub Action 固定 Commit SHA；升级任一引用时必须重新核对上游发布和安全公告。若下游把扫描器或签名工具嵌入产品，必须另行携带其许可证与 Notice。

本阶段再次核对固定 DeepSeek `THIRD_PARTY_NOTICES.md` 和品牌边界：没有复制其 Logo、商标、插件 Runtime、第三方源码、截图或特殊许可 Payload。AgentScope Permission/Sandbox/MCP/Skill 只作行为与限制参考，新增防腐和安全实现均为 AgentArk 独立代码，没有新增文件级 `REUSE`。
