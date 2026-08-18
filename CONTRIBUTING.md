# Contributing to AgentArk

## 开始之前

先阅读 `AGENTS.md`、`docs/architecture/overview.md`、相关 ADR，以及当前改动 Owner 对应的数据库、契约或配置规范。`PLAN.md` 保留历史执行基线，但不能覆盖更高优先级的架构和数据所有权规则。

开发环境使用 JDK 21、Maven Wrapper、Node.js 24、pnpm 11、Docker 与 Git。初始化和基础验证命令为：

```bash
./mvnw -version
./mvnw -N validate
pnpm --dir agentark-web install --frozen-lockfile
```

## 变更规则

- 保持 Gateway、Control、Runtime、Scheduler 四平面边界，不引入跨 Schema SQL、Mapper、外键、事务或共享账号。
- Runtime 只执行不可变 Snapshot；AgentScope Runtime 类型只能存在于专用 Provider，Knowledge RAG 类型只能存在于指定 Adapter。
- 公共 API、Event、Snapshot、数据库 Owner、配置或安全边界变化必须先更新规范文档，并在需要时提交 ADR。
- 已发布 Flyway 不得改写，只能新增前向迁移；迁移必须包含中文表/字段注释和真实 MySQL 测试。
- 不提交明文 Secret、Token、证书、Cookie、连接串、运行数据、构建输出或上游 Worktree 改动。
- 不通过删除测试、禁用测试、降低阈值或引入永真替身使门禁通过。
- Java、XML、YAML 和 Flyway 注释遵循 `AGENTS.md` 的中文注释规范；仓库不执行自动 Java 格式化。

## 验证

后端或跨模块变更至少执行：

```bash
./mvnw -T 1C clean verify
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```

前端或公共契约变更还需执行：

```bash
pnpm --dir agentark-web api:check
pnpm --dir agentark-web lint
pnpm --dir agentark-web typecheck
pnpm --dir agentark-web test
pnpm --dir agentark-web build
pnpm --dir agentark-web test:e2e
pnpm --dir agentark-web test:e2e:real
```

部署、安全或发布变更还需按影响运行 `tools/production/`、`tools/security/` 与 `tools/release/` 下的对应门禁。

## 提交与评审

提交信息使用 Conventional Commits。Pull Request 应说明变更目的、Owner/边界、契约或迁移影响、实际测试、剩余风险和回滚方式。架构、公共 API、数据库、认证、Secret、CI/CD、部署和基础设施变更必须获得相应 Owner 评审。
