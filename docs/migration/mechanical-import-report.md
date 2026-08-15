---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Phase 02 AgentScope Service 机械迁入报告

## 1. 结论

AgentScope Service 已在独立 Worktree/Branch 中按固定 Commit 建立机械证据基线。该基线保留原目录、文件模式、源码内容、JDK 17、Spring Boot 4.0.4、JPA、PostgreSQL 和 Go Aistio，不是 AgentArk 最终实现，也不得整体合并到 `main`。

机械复制内容与固定源码逐文件一致；仅额外加入许可文本、来源说明和固定 Commit 的根 POM 作为独立构建路径证据。Java 全 Reactor 测试和 Go 测试通过，Frontend 暴露出上游当前源码缺文件及 lint 工具未声明的问题。没有通过修改、删除或跳过测试来制造成功结果。

## 2. 基线身份

| 项目 | 实际值 |
|---|---|
| AgentArk 基线 HEAD | `d012f766e37c827c8f505e74312616aa7e15eb1a` |
| 隔离 Branch | `refinex/migration-agentscope-service-baseline` |
| 隔离 Worktree | `../agentark-upstream-baseline` |
| 隔离 Worktree HEAD | `d012f766e37c827c8f505e74312616aa7e15eb1a` |
| AgentScope 来源 Commit | `0c61e7494197ded54eefdeaf9bdeb51807beb752` |
| 来源路径 | `agentscope-service/` |
| 来源 Git Tree | `6b295335f84b2dcf2504652e4fe958240db1154c` |
| 迁入方法 | 对固定 Commit 执行 `git archive`，保留跟踪路径和文件模式 |
| 上游跟踪文件 | 655 |
| 基线 Service 文件 | 656，含额外 `LICENSE` |
| Hash Manifest 条目 | 658，含 Service、`SOURCE.md` 和根 POM |
| Manifest SHA-256 | `92b714370c85a244cb3730b652a9bd43b20420d3fbd2cdb1cf782042b62b081a` |

`diff -qr --exclude=LICENSE` 对固定 `agentscope-service/` 与机械基线无输出。最终 AgentArk Worktree 不存在 `upstream-baseline/`。

## 3. 唯一允许的取证调整

| 文件 | 操作 | 原因 | SHA-256 |
|---|---|---|---|
| `upstream-baseline/agentscope-service/LICENSE` | 新增 Apache License 2.0 官方文本 | 固定 Git Tree 和同版本发布物没有打包 LICENSE；补齐隔离证据包的再分发许可文本 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| `upstream-baseline/SOURCE.md` | 新增来源说明 | 固化 Commit、路径、复制方式、文件数、限制和许可证来源 | 见文件级 Manifest |
| `upstream-baseline/pom.xml` | 复制固定 Commit 根 POM，未修改 | Service Parent 使用 `../pom.xml`；用于证明独立构建仍缺 Monorepo 模块 | `ca92eaafa7e1100299c5e99c43ac1c3aaf55c65e4c9ff156d4fd604398fbc6a0` |

没有改包名、模块名、源文件、测试、POM 依赖、JPA、数据库或 Go 实现。基线保持未提交状态，等待用户独立审查；本阶段未获得创建 Commit 的授权。

## 4. 许可补证

固定 Git Tree 的根 POM、README 和文件头声明 Apache-2.0，但不存在根或模块级 `LICENSE`/`NOTICE`。Phase 02 进一步检查了 Maven Central 的同版本 `io.agentscope:agentscope-core:2.0.2` 和 `io.agentscope:agentscope-harness:2.0.2`：

- 两个发布 POM 都声明 Apache License 2.0，并指向 Apache Software Foundation 的许可证 URL；
- Core/Harness 的 binary JAR 和 sources JAR 均未包含 `LICENSE` 或 `NOTICE` 条目；
- 固定 Git Tree、官方 2.0.2 Tag 祖先和上述发布物均未发现 NOTICE 内容；因此不虚构空的上游 NOTICE；
- 机械基线使用 POM 指向的 Apache 官方文本，并记录 SHA-256；AgentArk 自身的 LICENSE/NOTICE 与上游证据包分离。

取证 Artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `agentscope-core-2.0.2.pom` | `7f3fdcc5e12527929141578421790c6291059104a5d4126f28767b6970ef977d` |
| `agentscope-core-2.0.2.jar` | `adacc0ad644b2bb3b43ee9dff57ced655f744c455db0596ef5aa8f365e301a1b` |
| `agentscope-core-2.0.2-sources.jar` | `2122afbb81aa7a8256669c682932ac75c031d45c33d8bd897432f90bd37bd0fc` |
| `agentscope-harness-2.0.2.pom` | `951a859a4af585de9788bf6886270d4478b0d8fa514d2697676285032362f52` |
| `agentscope-harness-2.0.2.jar` | `982a8d31e721c66d2b14f57191be252754899715a6719d4ef3b340999aa98cda` |
| `agentscope-harness-2.0.2-sources.jar` | `9ae54d3a56c4b3087df0cbe10f75b0bd847b191342707736ece4ab5a5b7c908f` |

这足以解除“建立内部机械证据基线”的许可阻断，但不能把上游发布物缺少许可证文件的包装问题表述为已修复。AgentArk 最终分发仍必须携带自身和第三方许可清单，并对实际 Runtime Classpath 生成 SBOM/License Report。

## 5. 构建与测试证据

### 5.1 Java

直接在机械 Service 基线执行了两次未改源码的可行性检查：

```bash
mvn -B -ntp -Drevision=2.0.2 test
mvn -B -ntp test
```

第一次失败：Service Parent `io.agentscope:agentscope-parent:${revision}` 无法在孤立目录解析。加入未修改的固定根 POM 后，第二次仍失败：`service-dataplane` 使用的 `agentscope-extensions-aistio` 没有版本，且发布 BOM 不管理该模块。结论是 Service 不是可独立构建的上游子树；不能用修改依赖版本伪造机械基线。

随后从同一来源仓库、同一固定 Commit 创建临时可写的完整 Monorepo Worktree，执行：

```bash
mvn -B -ntp \
  -pl agentscope-service/service-common,\
agentscope-service/service-gateway,\
agentscope-service/service-dataplane,\
agentscope-service/service-scheduler \
  -am test
```

结果：`BUILD SUCCESS`，24 个 Reactor Project；468 份 Surefire XML，共 3660 tests、0 failures、0 errors、14 skipped，总耗时 5 分 48 秒。上游测试过程仍暴露以下风险：

- POM 对 `maven-jar-plugin` 和 `maven-resources-plugin` 缺少显式版本；
- `WaitAsyncResultsTool` 测试本身等待 120 秒；
- Scheduler Context 测试连接本机 Control 失败后重试 12 次，耗时约 60.9 秒；
- Dataplane/Scheduler 开发初始化日志打印默认管理员明文密码 `admin`，Vault 也发出开发默认配置警告；这类行为在 AgentArk 中明确拒绝；
- macOS 出现 Netty native DNS resolver 警告，不影响本次测试结论。

### 5.2 Go Aistio

环境为 `go version go1.26.6 darwin/arm64`。在同一临时完整 Worktree 的 `agentscope-service/aistio` 执行：

```bash
go test ./... -count=1
```

结果：PASS；全部列出 Package 通过，覆盖源码树中的 44 个 `*_test.go`。没有运行依赖本地 Kubernetes envtest Asset 的 `make test-integration`，也没有运行真实 PostgreSQL、Kubernetes、模型或 Service Stack E2E。

### 5.3 AgentScope Service Frontend

在同一临时完整 Worktree 的 `agentscope-service/frontend` 执行：

```bash
npm ci
npm run lint
npm run build
```

实际结果：

- `npm ci` 成功，安装 142 个 Package；`npm audit` 报告 6 个漏洞（3 moderate、3 high）；
- `npm run lint` 失败，退出码 127：`eslint: command not found`。Package 定义了 lint Script，但没有声明 ESLint Dev Dependency；
- `npm run build` 失败，退出码 2：`src/features/build/` 缺失，`src/main.tsx` 和 `src/pages/AgentsHubPage.tsx` 引用的 Deployment/Agent 页面无法解析；
- 上游 Frontend 没有测试 Script/测试文件，无法形成单元或 E2E 成功证据。

这些是固定 Commit 的真实上游缺口，不在机械基线中修复。AgentArk Web 后续只消费功能语义，并采用独立实现和测试体系。

## 6. 隔离与清洁度

- 固定 detached Worktree 全程保持只读和干净；
- 测试在临时可写完整 Worktree 执行，生成物均被上游忽略，测试后 `git status --short` 无输出；
- 机械基线只存在于独立 Branch/Worktree，状态仅为 `?? upstream-baseline/`；
- AgentArk `main` 仅保存本报告和 Hash Manifest，不保存机械源码；
- DeepSeek Harness 固定 Worktree 未修改；Phase 02 不需要运行它的构建，因为机械迁入对象只有 AgentScope Service。

## 7. 复查命令

```bash
git -C ../agentark-upstream-baseline status --short
git -C ../agentark-upstream-baseline log -1 --oneline
diff -qr \
  --exclude=LICENSE \
  .agentark/upstreams/agentscope-java-2.0.2/agentscope-service \
  ../agentark-upstream-baseline/upstream-baseline/agentscope-service
sha256sum -c docs/migration/mechanical-import-files.sha256
```

最后一条命令必须从隔离 Worktree 的父目录或按 Manifest 中记录的相对路径执行；Manifest 是主分支保存的可审计快照，不是构建输入。
