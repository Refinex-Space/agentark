---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 12 AgentScope 防腐层执行报告

## 结论

Phase 12 在独立 `agentark-runtime-provider-agentscope` 模块中建立了 AgentScope Java 2 防腐层。Provider 只消费 Runtime Internal Contract 返回的不可变 Snapshot，不读取 Control Catalog 或 Control 数据库；编译结果不含 Secret 值和 Session 状态；每个 Run 单独解析 Secret、创建 Model、`HarnessAgent`、`RuntimeContext` 和资源句柄。

AgentScope Message、Event、State 和异常全部在 `space.refinex.agentark.runtime.provider.agentscope` 边界内转换。`agentark-runtime` 仅新增语言中立 `ExecutionSignalSink`，Domain/Application 继续不导入 AgentScope。Provider 不依赖 Control、Persistence Starter、MyBatis、DataSource、Mapper 或任何 `*-server`。

## 上游核对与二进制漂移

源码审计使用固定 Worktree Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`，重点核对 `HarnessAgent` Builder、`RuntimeContext`、`streamEvents(List<Msg>, RuntimeContext)`、Typed Events、Permission/HITL、MCP、Skill、Workspace、Memory、Sandbox、State Store、Sub-Agent、取消和对应测试。

实现过程中发现固定源码与 Maven Central `2.0.2` 发布 JAR 有真实差异：源码中的 `AgentStateStore` 已含 Versioned/CAS API，Builder 已含 `disableTranscript()`；发布 JAR 均没有。运行代码改为严格适配实际二进制，并以反射兼容测试锁定 API。完整证据和升级协议见 [AgentScope 兼容矩阵](../migration/agentscope-compatibility-matrix.md)。

## Snapshot 闭包修正

Phase 10 的 Snapshot 只冻结了 Memory、Workspace 和 Sandbox Profile Version ID，缺少版本内容，Runtime 无法在不回读 Control Catalog 的前提下独立编译。Phase 12 将三个 Profile 的完整配置 JSON 一并冻结到 Snapshot v1，并同步修改 Kernel 值对象、发布解析器、Canonical Serializer、JSON Schema 和 Golden File。

开放 Profile 配置只允许非空键、非 `null`、有限数值、布尔、字符串、数组和对象；构造时执行深层不可变复制。Canonical Serializer 不再使用无顺序保证的 `Map.of` 生成 Hash 字段，并对开放配置对象递归按键排序，避免 Map 实现差异造成 Snapshot Hash 漂移。Snapshot 仍只保存 `SecretRef` 和解析策略，不允许明文凭据。

## Provider Descriptor 与 Compiler

`RuntimeProviderDescriptor` 和 `META-INF/agentark/runtime-provider.json` 声明：

- Provider：`agentscope-java-2`；
- Provider Version：`2.0.2`；
- Compiler Version：`1.0.0`；
- Snapshot Schema：`1`；
- Streaming、Tool、Structured Output、MCP、Skill、Knowledge、Memory、Workspace、Sandbox、State、Permission 和基础 Sub-Agent 能力。

Descriptor 可直接生成 Internal Contract 使用的 Provider、Schema 和 Capability 字段，Control 已有 Internal Snapshot 获取门禁，会校验 Runtime 提交的 Provider/Schema/Capability 是否覆盖 Revision 要求。

`AgentScopeSnapshotCompiler` 先校验 Envelope Provider/Schema，再解析 Canonical JSON、核对 Revision/Snapshot ID、重算排除顶层 `contentHash` 的 SHA-256，并递归拒绝疑似明文凭据字段。Compiler 映射组织、项目、Agent 强类型 ID，以及 Model、Prompt、MCP、Skill、Knowledge、Memory、Workspace、Sandbox、Permission、Limits 和 Sub-Agent 上限；Prompt 内容 Hash、MCP Tool 冲突、MCP Endpoint/Transport、Skill ObjectRef、Profile 明文凭据字段和 Provider Capability 均有独立门禁。物化前再次核对 Session、Run、Snapshot 的租户、Revision、Hash、Provider 和 Compiler Version 归属；所有底层解析失败统一转换为稳定 `ProviderErrorCode`。

## Cache 与 Secret 生命周期

编译缓存键固定为 `Provider + Snapshot Schema + Snapshot Hash + Compiler Version`。缓存内容只有不可变 `AgentScopeCompilationPlan`，不含 Secret 值、Model、Client、Workspace、Session、Run 或 Agent State。并发相同 Snapshot 使用 `CompletableFuture` Single Flight；失败条目立即删除；默认最多保留 1024 个已完成计划，条目可随时清除并从 Snapshot 重建。

Secret 只在 `AgentScopeRuntimeMaterializer` 为当前 Run 创建 Handle 时按 `SecretRef` 解析。`ResolvedSecret` 使用防御性字符数组、固定脱敏 `toString()`，Handle 关闭时主动清零。MCP 绑定保留独立 SecretRef 与 Resolution Policy，但绝不把值写入编译缓存、Redis、磁盘、Event 或异常消息。

## RuntimeHandle、Engine 与状态

物化器强制向 Builder 注入 `AgentScopeStateStoreAdapter`，设置显式 Project/Session `RuntimeContext`，关闭 AgentScope Session Persistence、Tracing Log 和工作区 `tools.json` 自动发现。Model、MCP Client、Workspace/Sandbox 资源和 Secret 均归当前 Handle，终态按逆序释放；多 Session 不共享 Handle 或可变 Context。

`AgentScopeExecutionEngine` 实现中立 `AgentExecutionEngine`：首次执行转换 Runtime Payload 并订阅 `streamEvents`；流使用 32 的有界预取和 Snapshot Timeout；取消按 Run 和当前 Fencing Token 找到 Handle 后调用 `interrupt(RuntimeContext)`；审批恢复只接受同一 Run、当前 Fencing Token 且与已持久化参数 Hash 匹配的待审批 Tool，并发送 `ConfirmResult`。暂停 Handle 保留在当前实例；进程重启后重新物化，并通过 AgentArk State/Checkpoint Port 恢复 ASKING Tool State。进入 Event Stream 前失败会移除并关闭 Handle，避免 Model、Secret 或外部 Client 泄漏。

`AgentScopeStateStoreAdapter` 不创建 AgentScope 表或本地 JSON 文件。所有写入转换成 `runtime_agent_state` 的追加版本、Hash、Commit 可见性和 Fencing Token；主 `agent_state` 提交后再追加 Checkpoint。AgentScope 2.0.2 首次恢复可能传入空 `userId`，单 Run Adapter 仅兼容该空值或当前 Project ID，并始终强制匹配固定 Session；其他非空租户值会被拒绝。Provider 不直接知道 MyBatis、DataSource、表名或 Runtime Mapper。

## Event 与错误防腐

Event Mapper 逐类建立稳定 AgentArk Signal：Lifecycle、Text Delta、Model Call、Tool/MCP、Approval、External Execution、Result、Failure 和 RAG/检索自定义活动。Thinking Start/Delta/End 全部丢弃；Tool Call 参数流只保留长度，Approval 只暴露 Tool ID、Tool 名和规范参数 Hash；未知事件输出 `provider.event.unknown`，只含上游枚举类型和来源，不序列化 AgentScope Event 对象。

Provider 错误按 Snapshot Schema/Provider/Hash、Capability、Secret、Model、MCP、Skill、Input Payload、Resume State、State Persistence 和 Execution 分类。传给 Runtime 的消息不包含 Prompt、Secret、Credential Value 或上游对象转储。

## 组件装配边界

MCP、Skill、Knowledge、Memory、Workspace、Sandbox 和 Permission 先编译为无 AgentScope 类型外泄的 Provider Binding，再通过 `AgentScopeRuntimeComponentFactory` 贡献给 Builder。该 SPI 是 Phase 13 Runtime Server 装配边界，不允许回读 Control Catalog。具体厂商 Model SDK、远程 MCP Auth Header、Qdrant Retriever 和生产 Sandbox 实现没有伪造：Model Provider 按支持清单引入，RAG 属于 Phase 14，Skill/Sandbox 供应链和执行属于 Phase 20。

`SkillArtifactVerifier` 已提供统一的 Hash/Size/Media Type 校验语义；组件实现必须在制品进入 Skill Repository 前调用。零 Skill、零 MCP、零 Knowledge 和零 Sub-Agent 的 Snapshot 已可使用 Fake Model 完整执行；存在这些外部能力时，Runtime Server 必须显式提供对应组件工厂，不能静默退化到 Harness 本地默认。

## 测试与验收范围

Provider 定向测试覆盖 Golden Snapshot、Provider/Schema/Hash、结构化错误、Capability 不匹配、MCP Tool 冲突、Fake MCP 绑定、Skill Hash、缺失 Secret、Secret 清零、Single Flight、缓存上限、Fake Model Streaming、双 Session 隔离、HITL Resume、定向 Cancel、State/Checkpoint 往返、跨 Session State 拒绝、Golden Event、未知 Event、Thinking 过滤和 AgentScope 二进制升级检测。

实际验收结果：

- `./mvnw -pl agentark-runtime-provider-agentscope -am clean verify`：通过；Provider 30、Runtime 单元 10 / MySQL 集成 9、Persistence 单元 8 / MySQL 集成 3、Kernel 83，均为零失败；
- `./mvnw -pl agentark-control,agentark-services/agentark-runtime-server,agentark-runtime-provider-agentscope -am verify`：通过；Control 单元 16 / MySQL 集成 10、Runtime Server 启动 1，并连同依赖模块全部成功；
- Provider Runtime 依赖树只有 `agentark-runtime -> agentark-kernel` 与 AgentScope Core/Harness 及其传递依赖，不含 Control、Persistence Starter、MySQL、MyBatis 或 Server；
- AgentScope Import、Provider 基础设施依赖、SQL/Mapper 资源、缓存敏感字段、Canonical Example Hash、知识门禁、固定上游和 `git diff HEAD --check` 均按本报告命令验证。

构建日志存在 Mockito 动态 Agent 的未来 JDK 兼容性警告，以及 Fake Model 临时 Workspace 缺少 `AGENTS.md` 的预期警告；两者均未造成测试失败。前者属于全仓测试基础后续治理项，后者不代表生产 Workspace 配置已验收。

真实厂商模型、真实远端 MCP、生产 Object Store Skill 解包、Qdrant、生产 Sandbox 和多副本 Runtime Worker 不属于本阶段。Phase 12 验证的是防腐层、编译闭包和执行语义；网络服务和基础设施 E2E 必须分别在 Phase 13、14、20 完成。

## 风险与后续边界

- AgentScope 固定源码和发布 JAR 同版本号存在 API 差异，升级必须按兼容矩阵先失败后适配。
- 发布 JAR 无 Versioned State CAS；平台写安全来自 Runtime 追加版本和 Fencing，不得描述为 AgentScope 原生 CAS。
- 发布 JAR 无 `disableTranscript()`；当前关闭 Session Persistence，但未来升级仍需单独验证 Transcript 行为。
- `AgentScopeRuntimeComponentFactory` 的生产装配尚未进入 Server；Phase 13 必须按配置显式选择实现并在缺失时 Fail Fast。
- Tool Call 参数增量已只保留长度，审批参数只保留摘要；Tool Result/RAG 业务载荷仍可能较大或敏感，Phase 13 持久 Event 前须执行 ObjectRef 外置、内容策略和授权处理。

## 回滚

- 未发布源码、契约和文档按本阶段 Git Diff 精确反向修改，不覆盖 Phase 11 或其他未提交改动。
- 本阶段无 Flyway、数据库数据或外部基础设施变更，不需要数据回滚。
- 若仅回滚 Provider，必须同时回滚 Snapshot Profile Configuration 契约变更，或保留向后兼容读取；不能留下发布端与编译端不一致。
- AgentScope 版本不得单独降级；Maven 版本、Compatibility Matrix、Descriptor Provider Version 和测试必须作为一个原子变更处理。

```bash
./mvnw -pl agentark-runtime-provider-agentscope -am clean verify

rg -n "import io\.agentscope" agentark-runtime/src/main/java && exit 1 || true
rg -n "agentark-control|starter-persistence|agentark-services" \
  agentark-runtime-provider-agentscope/pom.xml && exit 1 || true

rg -n "apiKey|secretValue|credentialValue" \
  agentark-runtime-provider-agentscope/src/main/java \
  -g '**/cache/**' && exit 1 || true

python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```
