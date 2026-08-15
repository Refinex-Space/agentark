---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#阶段执行证据
---

# Phase 02 — 上游机械迁入基线、根工程、BOM 与 CI 门禁

## Status

- Status: DONE
- Started: 2026-08-15
- Completed: 2026-08-15
- Branch: `main`
- AgentArk HEAD at start: `d012f766e37c827c8f505e74312616aa7e15eb1a`
- Mechanical baseline Branch: `refinex/migration-agentscope-service-baseline`
- AgentScope Source Commit: `0c61e7494197ded54eefdeaf9bdeb51807beb752`

## Scope

### Included

- 在独立 Worktree/Branch 建立 AgentScope Service 机械证据基线；
- 补齐许可证来源、文件 Hash、源码一致性和 Java/Go/Frontend 原始测试证据；
- 建立 AgentArk 根 Parent POM、BOM、Maven Wrapper 和全部批准的空模块 POM；
- 锁定 JDK 21、Boot 4.1.0、Cloud 2025.1.2、AgentScope 2.0.2、MyBatis-Plus 3.5.17；
- 建立 Enforcer、Surefire/Failsafe、JaCoCo、License、CycloneDX 和 GitHub Actions 骨架；
- 更新 README、Runbook、知识地图与 Root Parent/BOM 职责说明。

### Excluded

- 不迁入任何 AgentScope Service 业务实现；
- 不创建 Java 业务类型、Controller、Entity、Mapper、AgentScope Adapter 或 Spring Boot Main Class；
- 不创建数据库 Migration、公共 API、前端、Compose、Helm 或部署资源；
- 不提交机械基线或 AgentArk 变更，不合并隔离 Branch，不推送或发布；
- 不修改两套固定上游 Worktree。

## Work Packages

| Work Package | 状态 | 产物 | 证据 |
|---|---|---|---|
| P02-A 机械迁入隔离基线 | DONE | `../agentark-upstream-baseline/upstream-baseline/` | [机械迁入报告](../migration/mechanical-import-report.md)、[文件 Hash](../migration/mechanical-import-files.sha256) |
| P02-B Root Reactor/BOM | DONE | `pom.xml`、`agentark-bom/`、19 个子模块 POM、Wrapper | 20 Project Reactor 已通过 effective POM、validate、verify 和 install |
| P02-C 质量与 CI | DONE | Enforcer/License/JaCoCo/CycloneDX、Backend/Dependency/Docs Workflow | 本地生命周期、Actionlint、知识门禁和固定上游校验通过 |

## Mechanical Baseline

固定源码的 `agentscope-service/` Git Tree 为 `6b295335f84b2dcf2504652e4fe958240db1154c`。机械基线复制 655 个跟踪文件，只额外加入 Apache 官方许可证文本、来源说明和未修改的固定根 POM；658 条 SHA-256 Manifest 的自身摘要为 `92b714370c85a244cb3730b652a9bd43b20420d3fbd2cdb1cf782042b62b081a`。

上游验证结果：

| 区域 | 实际结果 |
|---|---|
| Java 完整 Reactor | PASS；24 Project，3660 tests，0 failures，0 errors，14 skipped |
| Go Aistio | PASS；`go test ./... -count=1` 全部列出 Package 通过 |
| Frontend Install | PASS；142 Packages；Audit 报告 3 moderate + 3 high |
| Frontend Lint | FAIL；上游未声明 ESLint，`eslint: command not found` |
| Frontend Build | FAIL；固定 Commit 缺少两个 `src/features/build` 页面 Module |

失败、明文默认密码日志、长重试和未运行的外部基础设施测试均按原样记录；没有修改上游实现或测试。

## Final Maven Foundation

Root Reactor 精确包含 20 个 Project：Root Parent、BOM、Kernel、6 个 Starter、Foundation Aggregator、Control、Knowledge、Runtime、AgentScope Provider、Scheduling、4 个 Server 和 Services Aggregator。只有两个 Aggregator 与 BOM 使用 `pom` Packaging；其余模块在本阶段构建合法空 Jar。

`agentark-runtime-provider-agentscope` 是唯一声明 `io.agentscope` 依赖的 AgentArk 模块，当前只有 Core/Harness Dependency，没有 Adapter 源码。Framework 源码未进入 AgentArk Reactor。

### 版本与兼容 Pin

| 项目 | 锁定版本/规则 |
|---|---|
| Maven Wrapper | 3.9.12，distribution SHA-256 已锁定 |
| Java | Release 21；Enforcer `[21,22)` |
| Spring Boot | 4.1.0 |
| Spring Cloud | 2025.1.2 |
| AgentScope Core/Harness | 2.0.2 |
| MyBatis-Plus BOM | 3.5.17 |
| OkHttp / Okio | 5.3.2 / 3.16.4，覆盖 Boot 与 AgentScope 的坐标冲突 |

初次全 Reactor Validate 正确失败：Boot 4.1.0 将 `com.squareup.okhttp3:okhttp` 管理到 4.12.0，而 AgentScope 2.0.2 同时使用 `okhttp-jvm:5.3.2`，造成 Okio 版本不收敛和 OkHttp 重复类。`agentark-bom` 现把 `okhttp`/`okhttp-jvm` 统一为 5.3.2、`okio`/`okio-jvm` 统一为 3.16.4；修正后 Dependency Convergence、Upper Bound 和 Duplicate Classes 门禁通过。任一 Boot 或 AgentScope 升级都必须重验该 Pin。

### 质量生命周期

| Gate | 生命周期 | 当前行为 |
|---|---|---|
| Enforcer | `validate` | JDK/Maven、Release Deps、Convergence、Upper Bound、重复依赖/类、根 LICENSE/NOTICE |
| License Header | `validate` | Java Main/Test 文件必须具备 Apache-2.0 Header |
| Surefire | `test` | Unit Test；当前无源码/测试 |
| Failsafe | `integration-test` + `verify` | Integration Test；当前无源码/测试 |
| JaCoCo | `initialize` + `verify` | 准备 Agent 并生成 Report；当前明确跳过缺失的执行数据 |
| Third-party Report | `verify` | 生成 `target/generated-resources/licenses/THIRD-PARTY.txt` |
| CycloneDX | `verify` | 生成 Aggregate `target/bom.json`；当前 1.6 SBOM 含 63 Components |

CI Action 使用完整 Commit SHA 固定；Backend 使用 JDK 21 执行 `./mvnw verify`，Dependency/License 执行无测试 Verify 并上传 SBOM 与 Third-party Report，既有 Knowledge Gate 继续负责文档和控制面一致性。

## Verification

### 已执行

```text
mvn -B -ntp -N validate                                PASS（生成 Wrapper 前）
./mvnw -version                                        PASS（Maven 3.9.12 / JDK 21.0.10）
./mvnw -B -ntp -DskipTests validate                    PASS（20 Project）
./mvnw -B -ntp -DskipTests install                     PASS（20 Project）
Third-party Report                                     PASS（44 项第三方依赖）
CycloneDX Aggregate SBOM                               PASS（Schema 1.6 / 63 Components）
```

```text
./mvnw -B -ntp -DskipTests help:effective-pom            PASS（非空 Effective POM）
./mvnw -B -ntp verify                                    PASS（20 Project；当前无源码/测试）
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12 PASS（三个 Workflow）
python3 -m py_compile tools/harness/knowledge_gate.py     PASS
python3 tools/harness/knowledge_gate.py                   PASS（28 份 Active 文档）
python3 tools/harness/verify_upstreams.py --require-worktrees PASS
机械 Manifest `sha256sum -c`                             PASS（658/658）
机械源码 `diff -qr --exclude=LICENSE`                    PASS（无输出）
```

最终根工程无 `upstream-baseline/`，批准模块下除 `target/` 外只有 POM，没有 Java/业务源码。为上游测试创建的临时完整 Monorepo Worktree 已在确认状态干净后移除；固定 detached 证据 Worktree 和独立机械基线 Worktree 均保留且状态符合预期。

## Risks

- `space.refinex.agentark` 按已接受架构作为内部坐标使用；首次公开发布前仍须完成 `refinex.space` 命名空间控制权确认；
- AgentScope 2.0.2 发布 JAR 没有内置 LICENSE/NOTICE，最终 AgentArk 分发不能只依赖 JAR 内容；
- OkHttp/Okio Pin 是真实的 Boot/AgentScope 兼容层，升级任一 BOM 时都可能漂移；
- 当前构建成功只证明工程政策、依赖闭包和空模块可构建，不证明服务启动、业务行为、数据库、前端或部署可用；
- CycloneDX Schema Validator 输出未知 Meta Keyword 警告，但生成并验证了 BOM；需要随插件升级复核，不应被误报为失败。

## Rollback

AgentArk 主 Worktree 的新增 POM、Wrapper、模块目录、CI 和文档均未提交，可按 Git Diff 精确反向删除/修改，不影响业务数据或已发布契约。机械基线是独立 Worktree/Branch，可先核对来源 Commit 和状态，再通过 AgentArk 根仓库执行精确 `git worktree remove ../agentark-upstream-baseline`；不得整体合并或直接递归删除。两套固定上游无需回滚。

## Next

Phase 02 验收完全通过并标记 DONE 后，Phase 03 才能在 `agentark-kernel` 和 `contracts/` 建立第一批真实源码与测试。Phase 03 必须消费 Provider 隔离、无巨型 Common、语言中立契约和已锁定 Maven Gate，不能提前创建服务或数据库实现。
