---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# AgentScope Java 2 Runtime Provider 兼容矩阵

## 结论

AgentArk Phase 12 的编译目标是 Maven Central 发布的 `io.agentscope:agentscope-core:2.0.2` 与 `io.agentscope:agentscope-harness:2.0.2` 二进制，而源码语义审计固定在只读 Worktree Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`。两者并非完全相同：固定源码中的 `AgentStateStore` 已出现 CAS/Versioned State API，`HarnessAgent.Builder` 也出现 `disableTranscript()`，但同版本坐标的发布 JAR 没有这些方法。

因此，运行代码必须以实际依赖二进制为准，固定源码用于解释行为和准备升级。`AgentScopeCompatibilityTest` 锁定当前已验证方法；任何 AgentScope 版本变化都必须先使兼容测试失败，再显式调整 Adapter，不能靠源码记忆或同版本号假设静默升级。

## 固定输入

| 输入 | 固定值 | 用途 |
|---|---|---|
| AgentScope 只读源码 | `0c61e7494197ded54eefdeaf9bdeb51807beb752` | 定位 Builder、Event、State、HITL、MCP、Workspace、Sandbox 和对应测试 |
| Maven Core | `io.agentscope:agentscope-core:2.0.2` | Agent、Message、Typed Event、RuntimeContext、StateStore 与 Permission 运行类型 |
| Maven Harness | `io.agentscope:agentscope-harness:2.0.2` | `HarnessAgent`、Builder、Workspace、Skill、Memory、Sandbox 与编排 |
| Snapshot Schema | `1` | Provider 可接受的 AgentArk Snapshot 契约 |
| Compiler Version | `1.0.0` | 缓存与运行记录使用的编译语义版本 |
| MCP Java SDK | `mcp-core` + `mcp-json-jackson2` `1.0.0` | 覆盖 AgentScope 2.0.2 的 0.17.0 聚合传递依赖，修复 DNS Rebinding；保持 Jackson 2 二进制边界 |
| Netty | `4.2.16.Final` | 覆盖 Spring Boot 4.1.0 管理的 4.2.15.Final，修复当前 High 拒绝服务漏洞 |

## API 与行为矩阵

| 能力 | 固定源码证据 | 发布 JAR 2.0.2 | AgentArk 决策与门禁 |
|---|---|---|---|
| Harness 构建 | `agentscope-harness/.../HarnessAgent.java` Builder | `name`、`agentId`、`sysPrompt`、`model`、`stateStore`、`defaultSessionId`、`maxIters`、`disableSessionPersistence` 可用 | 只在 Provider 物化器调用；强制注入 AgentArk State Adapter，禁用 Harness Session Persistence 和 Tools Config 自动发现 |
| Typed Event 流 | `HarnessAgent.streamEvents(List<Msg>, RuntimeContext)` | 方法存在并返回 `Flux<AgentEvent>` | 使用显式 Project/Session 上下文；`limitRate(32)`、Timeout 和同步 Signal Sink；不调用已废弃的无上下文入口 |
| 定向取消 | `ReActAgent.interrupt(RuntimeContext)` | 方法存在 | 只中断 `RunId` 对应 Handle 的 userId/sessionId 槽位；取消事实仍由 Runtime Owner 先持久化 |
| HITL Resume | `RequireUserConfirmEvent`、`ConfirmResult`、`Msg.METADATA_CONFIRM_RESULTS` | 类型和 Metadata Key 可用 | 事件只输出 Tool 名、ID 与规范参数 Hash；Resume 必须匹配已批准 Hash，不回显原始参数 |
| Agent State | 固定源码 `AgentStateStore` 含 `supportsVersioning/getVersioned/saveIfVersion` | 发布 JAR 只有经典 `save/get/getList/exists/delete/listSessionIds`，首次恢复可能传入空 `userId` | Adapter 实现发布二进制接口，把写入转换为 AgentArk 追加版本与 Checkpoint；单 Run Adapter 只兼容空值或当前 Project ID，拒绝其他非空租户；预留同名 CAS 方法但不加 `@Override`，升级测试负责显式切换 |
| Transcript | 固定源码 Builder 含 `disableTranscript()` | 发布 JAR Builder 不含该方法 | 当前不能调用；使用 `disableSessionPersistence()`，Runtime API/Event Store 仍是唯一平台事实；升级后再单独评估 Transcript 关闭语义 |
| Message | `Msg`、`UserMessage`、`TextBlock`、`ToolUseBlock` | 已验证 | AgentArk `RuntimePayload` 在边界转换；公共 API、数据库和 Runtime Domain 不保存 AgentScope 类型 |
| Event | Core Typed Events | 已验证文本、模型、工具、审批、结果和失败类型 | 逐类构造 AgentArk Signal；Thinking Start/Delta/End 全部丢弃；未知类型只输出上游枚举名和来源 |
| MCP | Core MCP 与 Harness `McpServerRegistrar` | AgentScope 发布 JAR 原始传递版本为 MCP 0.17.0；AgentArk 排除该聚合包并显式使用 MCP 1.0.0 Core/Jackson 2 | Snapshot 编译为 Transport、Endpoint、SecretRef、解析策略和 Tool 白名单；`McpEndpointGuard` 签发固定地址/超时/大小 Permit；组件工厂禁止自行重解析或从工作区读取 `tools.json`；完整 Provider 测试验证二进制兼容 |
| Skill | Core/Harness Skill Repository | 运行 API 可用 | Snapshot 编译为 `ObjectRef`；进入 Repository 前必须通过 Size、Media Type、SHA-256 校验；实际解包和执行安全门禁仍归 Phase 20 |
| Workspace/Memory/Sandbox | Harness Builder 与对应 Manager | 运行 API可用 | Profile 完整配置已冻结进 Snapshot；组件工厂映射到 Builder；不得从 Control Catalog 回读或采用 Harness 默认本地持久目录 |
| Knowledge/RAG | Core 旧 RAG 与 Extensions | 部分 API 可用 | Phase 12 只映射固定 Knowledge Revision 与 Retrieval Profile；实际 AgentArk Retriever 适配和 Qdrant 属于 Phase 14 |
| Sub-Agent/Team | Harness Subagent/Team | 基础 API 可用 | Snapshot v1 只携带数量上限；零上限时显式关闭，非零只允许 Harness 基础动态能力；版本化 Team 定义仍需后续契约 |

## 状态所有权对照

上游 Dataplane 可使用 AgentScope 自有 Session/State 持久化。AgentArk 生产环境不创建或访问 `agentscope_sessions`，也不允许 Provider 依赖 MyBatis、DataSource、Runtime Mapper 或 Persistence Starter。`AgentScopeStateStoreAdapter` 只调用 `agentark-runtime` 的 `AgentStateStore` 与 `CheckpointStore` Port，最终权威表固定为 `runtime_agent_state` 和 `runtime_checkpoint`。

发布二进制缺少 CAS API 时，AgentArk Adapter 仍以追加版本、Commit 可见性和 Runtime Fencing Token 保证平台状态安全；这不等价于声称 AgentScope 内部已获得 CAS。升级到包含 Versioned State 的二进制后，必须增加真实 `@Override`、冲突重试和并发兼容测试。

## 0.1.0 最终兼容结论

- Provider POM、BOM、源码审计 Commit 和本矩阵共同固定 AgentScope `2.0.2`，禁止动态版本或只改其中一处。
- `AgentScopeCompatibilityTest` 对发布 JAR 的 Builder、`streamEvents`、RuntimeContext、State Store、HITL 和取消入口执行反射/编译契约；Event Mapping Golden 验证 AgentScope 类型不会进入 AgentArk API/DB。
- Snapshot Schema v1 是 0.1.0 的 N；这是首个版本，不存在更早 N-1。下一 Snapshot v2 必须在 Provider 同时验证 v2/v1 后才能移除 v1。
- Runtime Event Schema v1 与 AsyncAPI v1 同步冻结；未知 AgentScope Event 只映射受控来源名称，不导致流崩溃，也不暴露隐藏推理链。
- 滚动升级只允许使用相同 AgentScope 2.0.2 和 Snapshot/Event v1 的 N/N 节点混部；AgentScope 或 Schema 升级必须作为独立兼容变更处理。

## 升级协议

1. 先修改 AgentScope Maven 版本和固定 Worktree Commit，不修改 Adapter 以绕过失败。
2. 执行 `AgentScopeCompatibilityTest`、Golden Snapshot、Golden Event、State Recovery、Cancel/Resume 和多 Session 测试。
3. 对发布 JAR 执行 `javap`，将新增、删除和签名变化更新到本矩阵。
4. 若 Provider 行为或编译输出变化，递增 `compilerVersion`；若 Snapshot 字段变化，新增 Schema 版本，禁止覆写 v1。
5. 确认全仓只有 Provider 包和测试导入 `io.agentscope`，再允许升级进入 Runtime Server。

## 可复现检查

```bash
javap -classpath "$HOME/.m2/repository/io/agentscope/agentscope-core/2.0.2/agentscope-core-2.0.2.jar" \
  io.agentscope.core.state.AgentStateStore

javap -classpath "$HOME/.m2/repository/io/agentscope/agentscope-harness/2.0.2/agentscope-harness-2.0.2.jar:$HOME/.m2/repository/io/agentscope/agentscope-core/2.0.2/agentscope-core-2.0.2.jar" \
  'io.agentscope.harness.agent.HarnessAgent$Builder'

./mvnw -pl agentark-runtime-provider-agentscope -am clean verify
```
