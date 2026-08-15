---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 当前仓库 Runbook

## 控制面检查

```bash
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py
git diff HEAD --check
git status --short
```

`verify_upstreams.py` 默认验证两个来源仓库包含固定 Commit；Phase 00 建立 detached Worktree 后使用 `--require-worktrees` 同时校验工作视图 HEAD。

## Maven 与四服务验证

Phase 05 已建立四个可执行 Spring Boot JAR。根 Reactor 仍可全量验证；仅验证四服务时必须显式列出子模块，`-pl agentark-services -am` 只会构建聚合 POM，是不充分的假绿。

```bash
./mvnw -version
./mvnw -N validate
./mvnw verify
./mvnw -DskipTests install
./mvnw \
  -pl agentark-services/agentark-gateway-server,agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server \
  -am clean verify
```

`verify` 执行 Enforcer、Java License Header、Surefire/Failsafe、JaCoCo Report、第三方许可汇总和 CycloneDX Aggregate SBOM。仓库不在 Maven 生命周期执行自动 Java 格式化；格式变更由评审者依据相邻源码风格检查。

仓库仍无 `agentark-web/package.json` 或 Helm，因此前端和 Kubernetes 不可运行。四个 Server 只包含 Actuator 和空业务应用壳，健康不等于 Control/Runtime/Scheduler 业务已实现。

## 本地 Core/RAG

首次启动 Core 时执行：

```bash
./tools/dev-up.sh
./tools/dev-status.sh
./tools/verify-core.sh
```

`dev-up.sh` 先以 `0600` 生成已忽略的本地 Secret，再验证 Compose、打包四个 JAR、构建非 root 镜像并等待全部容器健康。若 `agentark_mysql-data` 已存在但任一 MySQL Secret 丢失，脚本将拒绝生成新凭据；必须恢复原 Secret，或由人工确认数据可丢弃后重置数据卷。`verify-core.sh` 额外验证七个容器、四组 Actuator 安全边界、三账号 Schema 隔离以及 Qdrant 未进入 Core。只准备 Secret 与校验配置而不启动时使用：

```bash
./tools/dev-up.sh --prepare-only
```

显式启动包含 Qdrant 1.18.3 的 RAG Profile：

```bash
./tools/dev-up.sh --profile rag
```

手工检查四个脱敏健康端点：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS http://127.0.0.1:8083/actuator/health
```

停止所有本地 Profile：

```bash
./tools/dev-down.sh
```

停止脚本故意不删除命名卷或 `.secrets/`。仅当确认本地数据可丢弃时，才能由人工另行执行 `docker compose ... down --volumes`；这是破坏性操作，不属于日常 Runbook。

## 回滚

- 文档/控制面变更：使用 Git Diff 精确反向修改，不覆盖用户已有改动。
- 固定上游 Worktree：先运行校验，再用来源仓库的 `git worktree remove <exact-path>`；禁止直接递归删除。
- 已发布 Flyway、Contract、Revision/Event 不允许原地回滚；按所属规范使用 Forward Fix 或兼容迁移。

## Loop 就绪性

当前 Loop 为 `DISABLED`。只有同时满足以下条件才可通过独立变更启用：

1. 目标 Work Package 边界、停止条件和失败升级规则明确；
2. 最小构建/测试命令已真实存在并稳定运行；Phase 02 只满足“构建命令存在”，尚无可循环的业务测试；
3. 无需生产密钥、外部付费调用或不可逆操作；
4. 日志、阶段报告和 Diff 可复查；
5. 最大迭代次数/时间、失败阈值和人工 Checkpoint 已定义。
