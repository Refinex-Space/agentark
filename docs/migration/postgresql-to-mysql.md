---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md
---

# PostgreSQL 到 MySQL 8.4 类型映射与风险

## 范围

AgentScope Service Java/JPA 和 Go Aistio 的 PostgreSQL Schema 只提供业务含义，不是 AgentArk DDL 来源。AgentArk 以三个 MySQL 逻辑模型为准重新设计表、约束和查询；禁止机械替换方言后迁入共享表。

## 类型映射

| PostgreSQL/上游语义 | MySQL 8.4 目标 | 风险与处理 |
|---|---|---|
| `uuid` | `BINARY(16)` UUIDv7 | 使用统一网络字节序 TypeHandler；不混用字符 UUID 或 `UUID_TO_BIN(...,1)` |
| `timestamptz` | `TIMESTAMP(6)` + UTC `Instant` | MySQL 时间范围、会话时区和微秒精度不同；纯本地时间单独建模 |
| `timestamp` | 只有语义明确时使用 `DATETIME(6)` | 不得用它代替跨时区业务时刻；必须记录时区解释 |
| `jsonb` | `JSON` | 二进制布局、比较、索引和操作符不同；高频字段外提或使用生成列/函数索引 |
| `bytea` / Large Object | `VARBINARY` 小值或 Object Storage | 大 State/文档/结果进入对象存储；表保存 Ref、Hash、Size、Media Type |
| `text` | 有界 `VARCHAR` 或必要时 `TEXT` | MySQL 索引长度与排序规则影响唯一性；稳定 Key 禁止无界 Text |
| `serial` / sequence | UUIDv7 | 不依赖单库自增顺序表达跨平面身份；业务序号单独约束 |
| `boolean` | `BOOLEAN` / `TINYINT(1)` | 数据写入仍需约束为 0/1，不把任意整数当布尔值 |
| PostgreSQL enum | `VARCHAR` 稳定代码 | 拒绝数据库 ENUM 和 Java ordinal，支持兼容增量状态 |
| array | 关系子表或受版本控制 JSON | 需要 Join/过滤/唯一性时必须规范化，不机械迁为 JSON |
| `numeric` | `DECIMAL(p,s)` | 逐字段确定精度、舍入和上限；成本累计禁止浮点 |
| `inet` / 自定义类型 | 规范化 `VARBINARY`/`VARCHAR` + 应用校验 | 先定义协议、规范化和索引语义，不依赖隐式字符串比较 |

## SQL 与约束差异

| PostgreSQL 能力 | MySQL 风险 | AgentArk 规则 |
|---|---|---|
| `ON CONFLICT ... RETURNING` | MySQL `ON DUPLICATE KEY` 的触发条件、影响行数和返回能力不同 | 幂等优先使用唯一约束 + 显式读回；逐语句测试影响行数 |
| Partial Index | MySQL 无等价通用 Partial Index | 使用状态前导复合索引、生成列或重塑唯一性；不能静默放宽约束 |
| Expression/GIN/GiST Index | MySQL JSON/函数索引能力与代价不同 | 先从真实查询设计生成列或关系字段，并用 Explain 验证 |
| Deferrable Constraint | MySQL 不支持同等延迟约束 | 调整事务写入顺序；不能依赖提交时才校验 |
| Transactional DDL | MySQL DDL 可能隐式提交 | Flyway 使用 Forward Fix；DDL 与数据回填拆分，不宣称可事务回滚 |
| `SELECT ... FOR UPDATE SKIP LOCKED` | 两端都有但锁范围、隔离级别和执行计划不同 | 在 MySQL 8.4 多实例 Contract Test 验证 Claim、饥饿和批量上限 |
| Advisory Lock | 语义与故障恢复不可直接迁移 | 使用 Redis/数据库 Lease + 单调 Fencing Token，业务写校验 Token |
| `ILIKE` 与默认排序 | Collation 可能默认大小写/重音不敏感 | Key/Hash 使用二进制或大小写敏感规则；用户搜索语义单独定义 |
| `NULLS FIRST/LAST` | MySQL 排序语法与默认行为不同 | 使用显式布尔表达式 + 列排序，并增加唯一 Tie-breaker |
| `RETURNING` | MySQL DML 支持差异 | 不依赖 ORM 自动回填；UUIDv7 在应用侧生成，必要结果显式查询 |

## 事务与并发风险

- 默认隔离级别、Gap Lock、Next-Key Lock 和死锁选择与 PostgreSQL 不同。队列 Claim、唯一键竞争、分页和高并发发布必须在 MySQL 8.4 上验证，不能以原 JPA 测试替代。
- MySQL `TIMESTAMP` 自动初始化/更新特性不得作为审计隐式默认；Flyway 明确列定义，应用统一写入 UTC 时刻。
- 乐观锁和 Fencing 以条件更新影响行数为准；不能只依赖 Java 内存对象版本。
- 跨 Schema Foreign Key、事务和 Join 一律拒绝。上游共享数据库中的关系改为版本化 Internal Contract、Snapshot ID 或 Event。

## 数据迁移门禁

Phase 06 不迁移生产数据。后续 Cohort 迁移必须为每张表提供：源/目标字段映射、总量与 Hash 校验、无效值处理、双读/影子读窗口、增量同步水位、停写/切换条件、Forward Fix、回退流量路径和审计证据。

Go Aistio 的 PostgreSQL Migration 不得直接转写为 MySQL；Control 资源语义按 [Aistio 绞杀计划](aistio-strangler.md) 分 Cohort 迁移，最终表结构以 [Control Schema](../database/control-schema.md) 为准。

