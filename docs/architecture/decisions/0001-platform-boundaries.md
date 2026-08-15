---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# ADR-0001：平台定位与四平面边界

## 状态

Accepted

## 决策

AgentArk 是基于 AgentScope Java 的 Agent Application Platform，不是 AgentScope Service 的长期 Fork。系统固定为 Gateway、Control、Runtime、Scheduler 四个可独立部署平面；领域模块是 Library，不因业务名词自动拆成服务。

- Gateway 只负责公共入口、认证前置、路由、限流和 SSE 代理，不拥有业务数据。
- Control 拥有 IAM、Catalog、Revision、Snapshot、Deployment、Knowledge Metadata 和 Governance。
- Runtime 拥有 Session、Turn、Run、Event、Approval、Checkpoint、持久 Work Queue 与运行状态。
- Scheduler 拥有 Trigger、Job、Attempt、Delivery、Dead Letter 和异步任务编排。

跨平面协作只能使用版本化 API、不可变 Snapshot、Outbox/Event 或显式 Client Port；禁止跨 Schema 查询、Mapper、外键和事务。

## 影响

四个 `*-server` 是部署单元。增加服务、改变数据 Owner 或建立新的跨平面同步调用，必须新增 ADR，并同步架构、契约和 PLAN。

