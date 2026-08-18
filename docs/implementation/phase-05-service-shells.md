---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md
---

# Phase 05 四服务骨架与本地 Core 执行报告

## 结论

Phase 05 建立了 Gateway、Control、Runtime 和 Scheduler 四个可独立启动的 Spring Boot 部署单元，以及 MySQL、Redis、MinIO 的本地 Core Profile 和默认关闭的 Qdrant RAG Profile。四服务当前只包含 Web 运行时、Foundation Web/Observability 与 Actuator，不包含业务 API、业务表、Mapper、AgentScope Harness 或 Gateway 业务路由。

## 上游取用结论

| 上游能力 | 取用 | AgentArk 处理 |
|---|---|---|
| Gateway `8080`、WebFlux、Actuator | `ADAPT` | 保留运行栈与健康语义，不复制业务 Route/Filter |
| Dataplane `8082`与健康检查 | `ADAPT` | 仅建立 Runtime WebFlux 空壳，拒绝 JPA、共享数据库、Harness 组装和默认密码 |
| Scheduler `8083`与 Worker 语义 | `ADAPT` | 启用 Spring Scheduling 容器和最小管理 HTTP，不引入 Job/Harness 业务 |
| 上游 Compose/Script 生命周期 | `REFERENCE/ADAPT` | 保留 up/down/status 概念，拒绝共享 Postgres 账号、明文 Secret、自动 DDL 和强制杀端口进程 |

固定 AgentScope 工作视图仍为 `0c61e7494197ded54eefdeaf9bdeb51807beb752`；本阶段未修改两套上游源码。

## 服务边界

| Server | 端口 | 运行栈 | 当前内容 | 显式拒绝 |
|---|---:|---|---|---|
| Gateway | 8080 | Spring Cloud Gateway WebFlux | Actuator、请求/可观测基础 | 业务路由、业务持久化 |
| Control | 8081 | Spring MVC | Actuator、Control/Knowledge 空领域边界 | Controller、Mapper、业务表 |
| Runtime | 8082 | Spring WebFlux/Reactor | Actuator、供应商中立 Runtime 空边界 | Dataplane 业务、AgentScope Harness |
| Scheduler | 8083 | Scheduling Worker + 最小 Spring MVC | Actuator、空调度容器 | Job/Trigger 业务、Harness |

四个 Server 没有相互 Maven 依赖；`application-local.yml` 只通过 `AGENTARK_*_BASE_URL` 配置其他平面地址。只有这四个 Server 包含 `@SpringBootApplication`。

## 健康与信息暴露

- 全部 Server 支持 `/actuator/health`、`/actuator/health/liveness`、`/actuator/health/readiness` 和 `/actuator/info`。
- Actuator 只暴露 `health,info`，健康细节固定为 `never`。
- Info 只允许 Spring Boot Maven Plugin 生成的 Build Info；关闭 Env 与 Java Info 贡献者。
- 四个应用使用优雅停机和 `20s` 关闭阶段上限。

## Core/RAG 基础设施

| 服务 | 镜像 | Profile | 健康检查 | 持久化 |
|---|---|---|---|---|
| MySQL | `mysql:8.4.11` | Core/RAG | `mysqladmin ping` + 文件 Secret | `mysql-data` |
| Redis | `redis:8.10.0` | Core/RAG | 鉴权 `PING` | `redis-data` AOF |
| MinIO | `minio/minio:RELEASE.2025-09-07T16-13-09Z` | Core/RAG | `/minio/health/live` | `minio-data` |
| Qdrant | `qdrant/qdrant:v1.18.3` | 仅 RAG | Phase 14 接线前不作为 Core 就绪依赖 | `qdrant-data` |

四个 Server 镜像固定基于 `eclipse-temurin:21.0.10_7-jre-alpine-3.23`，使用非 root 用户、只读根文件系统和受限 `/tmp` tmpfs。本地 Compose 通过 `deploy/compose/Dockerfile.service.dockerignore` 仅允许四个可执行 JAR 与镜像模板进入 Build Context，排除 `.git`、`.agentark`、本地 Secret、上游 Worktree 和不相关源码；根 `.dockerignore` 继续服务于生产多阶段构建，并排除宿主 Maven 产物。所有宿主端口只绑定 `127.0.0.1`。

## MySQL 隔离与 Secret

MySQL 空数据卷首次启动时创建 `agentark_control`、`agentark_runtime`、`agentark_scheduler` 三个 `utf8mb4/utf8mb4_0900_ai_ci` Schema，分别使用同名独立账号。每个账号只授权自身 Schema，不建立跨 Schema Foreign Key、SQL 或共享账号。

`tools/dev-up.sh` 使用 OpenSSL 首次生成六个 256 bit 本地 Secret，文件保存在已忽略的 `deploy/compose/.secrets/`。脚本不覆盖已有 Secret，不输出值，并强制每个值恰好为 64 个十六进制字符；Compose 通过文件挂载传递，`.env.example` 只包含非敏感端口。如果 MySQL 卷已存在但任一 MySQL Secret 丢失，脚本会在生成前拒绝继续，避免新文件密码与库内账号失配。

## 执行中纠偏

PLAN 原验收命令 `./mvnw -pl agentark-services -am clean verify` 只选中聚合 POM，实测在约 2 秒内假绿，不会编译或测试四个 Server。PLAN、Runbook 和 `dev-up.sh` 已统一改为显式列出四个子模块。首次容器验收还发现 MinIO 默认入口点会把 `/bin/sh` 解释为 MinIO 子命令；Compose 已显式覆盖入口点，然后从文件 Secret 启动真实 MinIO 进程。

## 已完成的验证

```bash
./mvnw \
  -pl agentark-services/agentark-gateway-server,agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server \
  -am clean verify

./tools/dev-up.sh --prepare-only
docker compose -f deploy/compose/docker-compose.yml --profile core config --quiet
docker compose -f deploy/compose/docker-compose.yml --profile rag config --quiet
./tools/verify-core.sh
sh -n tools/dev-up.sh tools/dev-down.sh tools/dev-status.sh tools/verify-core.sh deploy/compose/mysql/init/01-agentark-schemas.sh
```

Maven 真实构建了 18 个 Reactor 项目，四个 Server 上下文测试各 1 条通过，且均生成可执行 JAR 与 Build Info。Core/RAG Compose 配置解析通过；六个 Secret 文件为 `0600` 且 Git 忽略生效。

容器级验收启动了 MySQL、Redis、MinIO 和四个 Server 共 7 个容器，全部达到 `healthy`。四个端口的 Health/Liveness/Readiness 均返回 `UP`，Info 只返回 artifact/name/time/version/group Build Info，`/actuator/env` 均为 `404`。三个 MySQL 账号均能访问自身 Schema、无法访问另两个 Schema，三库的字符集/排序规则均为 `utf8mb4/utf8mb4_0900_ai_ci`。上述检查已固化为不打印 Secret 的 `tools/verify-core.sh`。完整 `down → up -d → healthy → down` 重启验收通过，重启后账号隔离保持；最终运行容器数为 0，MySQL/Redis/MinIO 命名卷保留。Core Profile 未创建 Qdrant 容器或卷。

## 回滚

- 四个 Server 壳：精确反向本阶段的 POM、Java 与 YAML Diff，不覆盖其他未提交改动。
- 运行中容器：执行 `./tools/dev-down.sh`，该操作保留命名卷和 Secret。
- 本地数据与 Secret：只有明确接受丢失三个 Schema、Redis AOF 和对象后，才可成对删除命名卷与 `.secrets/`；属于破坏性人工操作。

## 后续边界

Phase 06 才能为 Control、Runtime、Scheduler 接入各自 DataSource、MyBatis-Plus、Flyway、Redis 和 Testcontainers，并创建独立 Migration History。不得因 Core 容器已存在而跳过 Phase 06 的数据库基线验收。
