---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md
---

# Phase 06 MySQL 持久化基线执行报告

## 结论

Phase 06 为 Control、Runtime、Scheduler 建立了独立 DataSource 模板、Flyway Location、Migration History 起点和真实 MySQL 8.4 迁移测试。公共 Persistence Starter 提供 UUIDv7、UTC Instant、JSON、分页、乐观锁、条件化 Tenant 防御和脱敏语句遥测；没有创建生产业务表、共享 Mapper、JPA/Hibernate 依赖或跨 Schema SQL。

## Schema 与迁移所有权

| Owner | 生产模块 | Schema | Flyway Location | Phase 06 Migration |
|---|---|---|---|---|
| Control | `agentark-control` | `agentark_control` | `classpath:db/migration/control` | `V1__phase_06_schema_baseline.sql` |
| Runtime | `agentark-runtime` | `agentark_runtime` | `classpath:db/migration/runtime` | `V1__phase_06_schema_baseline.sql` |
| Scheduler | `agentark-scheduling` | `agentark_scheduler` | `classpath:db/migration/scheduler` | `V1__phase_06_schema_baseline.sql` |

三个 V1 文件只包含迁移边界说明；Flyway 自己创建各自的 `flyway_schema_history`，不存在 AgentArk 元数据表或业务表。Control/Runtime/Scheduler Server 分别只读取所属 Location 和无默认值的 `AGENTARK_*_DB_URL/USERNAME/PASSWORD`。

## Persistence Starter

- MyBatis-Plus 固定 MySQL 分页上限、关闭 Overflow，并启用乐观锁。
- UUIDv7 以标准网络字节序写入 `BINARY(16)`；非 v7 值拒绝。
- `Instant` 与 `TIMESTAMP(6)` 通过 UTC 连接约束往返；JSON 使用 Jackson 3 `JsonNode` TypeHandler。
- Tenant Interceptor 只有在 Owner 提供 `TenantLineHandler` 时才加入；Phase 06 不虚构 IAM Tenant 来源。
- MyBatis SQL Logger 使用 `NoLoggingImpl`。遥测只记录安全 Statement ID、Operation、Outcome、Duration，Metric 只有低基数 Operation/Outcome Tag。
- 测试 Fixture 以 Tests JAR 供三个 Owner 复用；容器口令运行时随机生成，不进入源码或资源。

## JPA 行为迁移

固定上游的 Entity/Repository 只作 `REFERENCE`。Phase 06 Contract Test 使用测试专用表证明 JPA 迁移后必须保留的最低语义：Scope 内稳定分页排序、UUIDv7/Instant/JSON 往返、`@Version` 对应的陈旧写拒绝、唯一约束异常和 Spring 事务回滚。详细映射见 [JPA 到 MyBatis-Plus](../migration/jpa-to-mybatis-plus.md)。

上游 PostgreSQL 类型、方言、Partial Index、Large Object、Advisory Lock 和 DDL 事务差异记录于 [PostgreSQL 到 MySQL](../migration/postgresql-to-mysql.md)，没有机械复制 JPA Annotation 或 Go Migration。

## 自动门禁

`tools/harness/knowledge_gate.py` 对三个 Owner 执行以下检查：

- V1 Baseline 文件存在且非空；
- Owner `src/main` 不含另两个 Schema 名称；
- 生产源码不写 `agentark_*.` 限定 Schema SQL；
- Server 配置必须使用所属 URL/Username/Password 无默认占位符；
- `default-schema`、`schemas`、Location、`clean-disabled`、`create-schemas` 与 Owner 一致。

## 执行中纠偏

1. 三个领域 POM 原先未导入 AgentArk BOM，新增测试依赖无法解析版本；已改为显式导入 `agentark-bom`，并由根 Enforcer 继续验证收敛。
2. 事务 Contract Fixture 最初未被测试应用扫描；已通过测试配置显式导入，避免扩大生产扫描范围。
3. 首版迁移测试试图使用远程 root 管理三个 Schema；MySQL 镜像拒绝该权限。最终测试在容器启动后用临时 root 初始化账号，实际迁移和清理由 Owner 最小权限账号执行。
4. 固定测试口令与仓库安全边界冲突；已删除该 Fixture，所有容器与 Owner 口令改为测试运行时随机生成。
5. Phase 05 没有 Flyway Version，不能把目标版本设为 `0`；N-1 框架改为测试专用 Baseline `0` 后升级到生产 V1。
6. 服务 `test` Profile 不加载生产 DataSource 模板，但数据库自动配置仍会触发；三个 Smoke Test 显式排除 DataSource/Flyway/MyBatis 自动配置，生产启动不受影响。
7. Testcontainers 2 的 MySQL 类型已迁到新包且不再泛型；一次干净编译发现构造器残留 Diamond，修正后从头重跑验收，未沿用增量构建结果。
8. Hikari 时间属性直接绑定毫秒型 `long`，首轮 Core 启动因使用 Duration 文本而失败；三服务改为明确毫秒整数后，重新构建并全部健康。
9. 根 `.gitignore` 的 `out/` 会误忽略六边形包 `adapter/out`；规则已收窄为 `/out/`，三个 Migration Test 现可被 Git 正常追踪。

## 验证证据

已完成以下收官验证：

```bash
./mvnw -pl agentark-control,agentark-runtime,agentark-scheduling -am clean verify
./mvnw -pl agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server -am -DskipITs test
./mvnw -pl agentark-control,agentark-runtime,agentark-scheduling dependency:tree
./tools/dev-up.sh
./tools/verify-core.sh
./tools/dev-down.sh
python3 tools/harness/knowledge_gate.py
git diff HEAD --check
```

三 Owner 干净构建共 7 个 Reactor 项目通过；Kernel 46 条、Persistence Starter 8 条单元测试、MySQL 持久化 Contract 3 条、三平面 Migration 各 4 条均无失败。依赖树未发现 Hibernate、Spring Data JPA 或 `jakarta.persistence`。

本地 Core 二次构建后七个容器全部健康；四个 Health/Liveness/Readiness 为 `UP`，Info 保持脱敏且 `/actuator/env` 隐藏。三个 Owner 账号均确认 `flyway_schema_history` 最新为成功 V1、业务表数为 0、另两个 Schema 访问被拒绝；Qdrant 未进入 Core。最终 `dev-down` 后 Compose 容器为 0，命名卷和本地 Secret 保留。

## 回滚

- 代码与文档尚未发布时，按本阶段 Git Diff 精确反向修改；不要覆盖其他未提交改动。
- 本地 Compose 使用 `./tools/dev-down.sh` 停止，保留 Secret 和命名卷。
- V1 一旦进入共享环境不得删除或改写；修正必须增加新 Migration 进行 Forward Fix。Phase 06 V1 没有业务表，不涉及业务数据回填。

## 后续边界

Phase 07 才能在 Control 创建 IAM 表；Phase 11 才能创建 Runtime 业务表；Phase 15 才能创建 Scheduler 业务表。后续每个 Flyway 必须先匹配所属逻辑模型，并复用空库、N-1、类型往返、约束和 Owner 权限测试。
