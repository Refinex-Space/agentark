---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 编码与模块标准

- 保持 `adapter -> application -> domain`，Application 通过 Port 使用外部能力。
- `agentark-kernel` 不依赖 Spring、ORM、Redis、HTTP Client、AgentScope 或厂商 SDK。
- Domain 不使用 MyBatis/Jackson/Web 注解；持久化对象使用 `*DO`，DTO 不反向成为领域模型。
- 业务模块不得依赖 `*-server`；Server 只装配、配置和暴露入口。
- 异常保留资源、操作和稳定错误码上下文，不吞异常，不把 Provider 异常原样公开。
- 并发写显式处理事务、乐观锁、幂等、Lease/Fencing、重试预算、超时和资源释放。
- 不创建 giant common、隐式全局状态、无界缓存/队列或以 `Map<String,Object>` 代替稳定模型。
- 新增依赖前检查 BOM、许可证、安全和现有能力；依赖与插件版本集中锁定。
- 测试名称表达行为与边界；禁止跳过真实失败、永真 Mock 或仅验证实现细节。

具体模块边界以 [系统架构第 9 章](../architecture/overview.md) 为准。
