---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 配置参考

## 当前状态

仓库尚未创建应用模块、运行 Profile、`.env.example` 或部署清单，因此当前没有可声明为已实现的 Runtime 配置键。Phase 05 创建四服务骨架时，必须在实现配置的同一变更中补充本文件。

## 规范

- 配置由所属 `*-server` 读取；Library 不直接读取进程环境。
- 环境变量、Spring 属性、默认值、是否必填、敏感级别、适用 Profile 和重载方式必须逐项记录。
- Production 不提供默认密码、共享 JWT Secret、长期服务 Token 或宽松 CORS。
- Secret 配置只保存 Provider 引用，不记录值；示例使用明显的非秘密占位符。
- Control、Runtime、Scheduler 各自只能配置所属 Schema 的最小权限账号。
- 端口、对象存储、Redis、Qdrant 和外部 Provider 的超时、重试、连接池与 TLS 不能依赖未记录默认值。
- 删除或重命名配置先提供兼容窗口、弃用日志、迁移步骤和回滚方式。

## 上游取证变量

`PLAN.md` 定义 `AGENTSCOPE_REPO`、`AGENTSCOPE_SOURCE_COMMIT`、`AGENTSCOPE_ROOT`、`DEEPSEEK_HARNESS_REPO`、`DEEPSEEK_HARNESS_SOURCE_COMMIT`、`DEEPSEEK_HARNESS_ROOT`。这些变量只用于固定上游源码视图，不进入产品运行配置。
