---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#阶段执行证据
---

# Phase 00 执行基线与仓库规范化

## 结论

Phase 00 于 2026-08-15 完成。AgentArk 根目录、两套上游来源、固定 SHA、detached Worktree、工具链、文档路径和忽略规则均已核验。本阶段没有迁移源码、创建业务模块、引入依赖或修改上游源码。

本阶段纠正了一个会使 Phase 01 立即失败的基线错误：原 AgentScope 2.0.1 Commit `35f52181fb37eed97cf0adacf2d1c13a63bbfb7d` 不包含 `agentscope-service/`。固定基线因此提升为远端可达、包含完整 Service 树的 2.0.2 合并提交 `0c61e7494197ded54eefdeaf9bdeb51807beb752`。

## AgentArk 阶段前基线

| 项目 | 实际值 |
|---|---|
| Git 根目录 | 当前目录与 `git rev-parse --show-toplevel` 一致 |
| Branch | `main` |
| HEAD | `187aa8111d41e951bbe7ae9c262996b08f11152a` |
| Upstream | `origin/main` |
| Ahead / Behind | `+2 / -0` |
| 阶段前工作区 | 干净 |

阶段执行产生的文档和控制面变更保持在 AgentArk 工作区中；未执行暂存、提交或推送。

## 上游来源仓库基线

| 来源 | 来源 Branch | 来源 HEAD | Tracking | HEAD Tag | 阶段前状态 | 固定 Commit | 固定 Commit Tag |
|---|---|---|---|---|---|---|---|
| `$AGENTSCOPE_REPO` | `main-agentark` | `f11a826eac2dd94a80f4c12f8562097d9ef70a8d` | 无 | 无 | 干净 | `0c61e7494197ded54eefdeaf9bdeb51807beb752` | 无 |
| `$DEEPSEEK_HARNESS_REPO` | `master` | `47f943859bef60e4160492346772ded9b24f765a` | `origin/master` | 无 | 干净 | `47f943859bef60e4160492346772ded9b24f765a` | 无 |

AgentScope 固定 Commit 是来源仓库 `origin/main` 可达的 `release/2.0.2` 合并提交。未选择来源仓库本地 HEAD，因为该 Commit 没有远端引用。DeepSeek Harness 来源 HEAD 已等于固定 Commit。

本阶段只检查了上游 Git 元数据和顶层树。未读取或迁移 Phase 01 所属的详细源码。

## 固定 Worktree

| 固定视图 | HEAD | 模式 | 必需顶层目录 | 状态 |
|---|---|---|---|---|
| `.agentark/upstreams/agentscope-java-2.0.2` | `0c61e7494197ded54eefdeaf9bdeb51807beb752` | detached | `agentscope-service/`、`agentscope-harness/` | 干净 |
| `.agentark/upstreams/deepseek-harness` | `47f943859bef60e4160492346772ded9b24f765a` | detached | `apps/`、`packages/` | 干净 |

AgentArk 根仓库整体忽略 `.agentark/`。当前阶段没有 `pom.xml`、Maven Wrapper、根 `package.json` 或 `agentark-web/` 构建入口，现有 Harness 校验只读取固定视图的 Git HEAD 和状态。因此 AgentArk 当前构建/格式化范围不会覆盖两个 Worktree。未来引入构建和格式化工具时仍必须显式保留该排除边界。

## 工具链

| 工具 | 实际版本 | 目标对照 | 结论 |
|---|---|---|---|
| JDK | Azul Zulu OpenJDK `21.0.10` LTS | JDK 21 LTS | 匹配 |
| Maven | `3.9.12`，运行于 JDK 21.0.10 | Maven Wrapper 3.9.x | 匹配；Wrapper 由 Phase 02 创建 |
| Node.js | `v24.14.1` | Node.js 24 LTS | 匹配 |
| pnpm | `10.33.0` | pnpm 11.x | 不匹配；Phase 17 前必须通过锁定版本纠正 |
| Docker | Client `29.4.2` / Server `29.4.2` | Docker / Compose | 可用 |
| Git | `2.50.1 (Apple Git-155)` | Git | 可用 |

六项工具均存在。pnpm 版本差异不阻塞取证阶段，但不能在后续前端阶段被误报为目标版本已满足。

## README、架构与知识路由核对

- README 未保留已废弃的控制面架构文档链接，无需机械修改旧路径；
- README、PLAN 和文档索引均指向 `docs/architecture/overview.md`；
- README 与架构文档的目标模块树一致，Provider 模块统一为 `agentark-runtime-provider-agentscope`，后端启动单元统一为四个 `*-server`；
- AgentScope 基线已统一为 2.0.2 / `0c61e7494197ded54eefdeaf9bdeb51807beb752`，DeepSeek Harness 基线统一为 `47f943859bef60e4160492346772ded9b24f765a`；
- 架构、五个 ADR、三个数据库逻辑模型和本报告均可从 `docs/README.md` 直达。

## `.gitignore` 核对

| 类别 | 已验证规则 |
|---|---|
| Secret / 本地配置 | `.env`、`.env.*`、`application-secret.*`、`.secrets/`、`secrets/local/`、私钥与 KeyStore 后缀 |
| 构建与依赖 | `target/`、`node_modules/`、`dist/`、`build/`、Python `__pycache__/`、覆盖率与测试缓存 |
| 运行数据 | `.agentark/`、`.runtime/`、`runtime-data/`、`workspace-data/`、数据库与 Compose volume 目录 |
| 可提交模板 | `.env.example`、`.env.*.example`、`deploy/compose/.env.example` 保持可跟踪 |

忽略规则只能阻止常见本地文件误入库，不能替代后续 Phase 的 Secret Scan 和代码评审。

## 上游取用与迁移映射

| 来源 | 本阶段分类 | 目标路径 | 处理 |
|---|---|---|---|
| AgentScope Java | `DEFER` | 无 | 仅创建固定视图；Phase 01 再逐路径分类和建立迁移清单 |
| DeepSeek Harness | `REFERENCE` | 无 | 仅固定视觉、交互和工程参考，不复制源码、品牌或资产 |

本阶段没有许可证文件复制、包名修改、源码迁入或行为重写。

## 验收证据

以下检查在最终文档和 PLAN 状态更新后执行：

```text
python3 tools/harness/verify_upstreams.py --require-worktrees  PASS
python3 tools/harness/knowledge_gate.py                       PASS
git diff HEAD --check                                         PASS
Markdown 仓库内相对链接检查                                  PASS（由 knowledge_gate.py 执行）
旧架构路径扫描                                                PASS
上游来源与固定 Worktree 前后 git status                      PASS（均无新增改动）
```

Phase 00 没有业务构建入口，未运行 Maven、pnpm、Compose 或 Helm 构建；这不是对后续阶段构建门禁的豁免。

## 风险与后续约束

- pnpm 10.33.0 低于目标 11.x；Phase 17 必须精确锁定并验证，不允许使用未锁定全局版本生成 Lockfile；
- detached Worktree 不是文件系统级只读挂载；每个 Phase 必须在读取前后运行上游校验，发现改动立即停止；
- AgentScope 2.0.2 是本阶段纠错后的新兼容基线，Phase 01 必须建立 Service/Core/Harness 的实际行为和许可证清单，不能仅凭版本号推断兼容；
- 来源仓库本地 HEAD 比固定基线更新，后续不得从移动工作区混读能力或复制文件。

## 回滚

文档变更可通过本阶段 Git Diff 逐文件恢复。两个固定视图可使用来源仓库的 `git worktree remove <exact-path>` 移除后，按本报告 SHA 重建；不得直接递归删除 `.agentark/upstreams/`。本阶段未改变上游 Branch、HEAD 或源码，因此不需要上游代码回滚。

## 下一阶段进入条件

Phase 01 只能读取本报告记录的两个固定 Worktree，并在读取前后运行 `python3 tools/harness/verify_upstreams.py --require-worktrees`。其首要任务是核实 AgentScope 2.0.2 的模块、许可证、行为与 AgentArk 目标边界，形成逐路径迁移分类，不能直接进入机械复制。

推荐 Commit Message：`docs(baseline): 完成 Phase 00 执行基线固化`
