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

## 中文注释

- 手工维护的 Java 类、接口、记录、枚举、构造器、方法、字段、常量、枚举值和记录组件必须具有准确的中文 Javadoc；每个具名类型必须且只能声明 `@author refinex`，测试代码执行同一标准。
- 方法注释按实际契约说明参数、返回值、单位、不变量、副作用、异常、安全、并发和空值边界，禁止只复述标识符或使用无信息量模板。
- 重写方法也必须说明当前实现的中文契约，不能只写 `{@inheritDoc}`；License Header 不能代替代码注释。
- 手工维护的 XML、YAML 配置块和属性必须就近使用中文注释说明用途、范围、取值或所有者；生成物和机器维护文件不手工补注释。
- JSON 等不支持注释的格式必须使用合法的 `title`、`description`、`$comment` 等标准元数据表达含义，禁止为满足注释要求破坏格式。

## 代码格式

- 仓库不提供自动 Java 格式化脚本，也不在 Maven 生命周期绑定格式化插件。
- 修改代码时保持相邻源码的缩进、换行、Import 和 Javadoc 风格，禁止借任务之机批量重排无关文件。
- IDE 或 Agent 不得自动调用任何全仓 Java 格式化器；提交前仍须执行编译、测试、知识门禁和 `git diff HEAD --check`。

具体模块边界以 [系统架构第 9 章](../architecture/overview.md) 为准。
