---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# ADR-0003：Runtime Domain 与 AgentScope Provider 模块隔离

## 状态

Accepted

## 决策

Runtime 中立领域和 AgentScope Provider 使用两个 Maven Library：

```text
agentark-runtime
└── Domain / Application / Ports / Web / Persistence / Redis / Control Client

agentark-runtime-provider-agentscope
└── Snapshot Compiler / Harness Engine / Event Mapping / AgentState Adapter
```

`agentark-runtime` 不依赖 AgentScope。Provider 模块依赖 `agentark-runtime` 的 Port 和语言中立 Contract；只有 `space.refinex.agentark.runtime.provider.agentscope` 包可以导入 AgentScope Runtime 类型。Runtime Server 负责组合二者。

## 原因

仅靠同一模块内的包隔离，会让 Session、Run、Event 和持久化在 Maven 层绑定 AgentScope，未来新增 Provider 时被迫依赖带有 `agentscope` 名称的模块或整体重构。拆分后保持一个 Runtime 服务，同时建立真实防腐层。

## 验证

ArchUnit 和 Maven 依赖门禁必须证明：Domain/Application 无 AgentScope Import；Control、Scheduler、Gateway 不依赖 Runtime 实现或 Provider；Provider 不读取 Control DB。
