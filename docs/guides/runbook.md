---
owner: refinex
updated: 2026-08-16
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

## Maven、持久化与四服务验证

Phase 05 已建立四个可执行 Spring Boot JAR。根 Reactor 仍可全量验证；仅验证四服务时必须显式列出子模块，`-pl agentark-services -am` 只会构建聚合 POM，是不充分的假绿。

```bash
./mvnw -version
./mvnw -N validate
./mvnw verify
./mvnw -DskipTests install
./mvnw \
  -pl agentark-services/agentark-gateway-server,agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server \
  -am clean verify

./mvnw \
  -pl agentark-control,agentark-runtime,agentark-scheduling \
  -am clean verify
```

第二条命令需要可用的 Docker daemon，会以 Testcontainers 启动真实 `mysql:8.4.11`，验证三套空库/N-1 迁移、Owner 越权拒绝、字符/时区规则和共享持久化 Contract。测试凭据运行时随机生成；报告不应出现其值。

`verify` 执行 Enforcer、Java License Header、Surefire/Failsafe、JaCoCo Report、第三方许可汇总和 CycloneDX Aggregate SBOM。仓库不在 Maven 生命周期执行自动 Java 格式化；格式变更由评审者依据相邻源码风格检查。

仓库仍无 `agentark-web/package.json` 或 Helm，因此前端和 Kubernetes 不可运行。Control 已实现 IAM、资产、Knowledge 和发布能力；Runtime 已实现中立领域、AgentScope 防腐层与 Phase 13 API/Worker 装配，但真实 Worker 默认关闭且仓库未提供生产 Model/MCP/Sandbox Bean；Scheduler 已实现持久 Job/Trigger/Webhook，Gateway 已实现公共认证、固定路由、限流和 SSE 代理。仓库没有配置真实生产 IdP，健康与 Migration 成功不等于真实模型、跨服务认证或产品工作流已验收。

## Gateway 认证、限流与 SSE

Gateway 默认只允许匿名健康探针和 Scheduler HMAC Webhook 到达目标服务；Security 未启用时普通 Public API 失败关闭。生产部署必须同时提供可信 HTTPS Issuer 或 JWK Set、`agentark-gateway` Audience，并显式启用 Gateway Security。共享 HMAC Secret、固定内部 Token 和匿名 Public API 都不是可用降级方案。

Redis 限流启用后，Readiness 会包含 Redis，配额判定故障使受保护请求返回 `503`，不会静默放行。Redis 未启用时不进入健康状态，Gateway 仍保持 Public API 认证失败默认。API Key 吊销的边缘最坏可见窗口由 `agentark.gateway.api-key-cache-ttl` 决定，配置上限为 30 秒；紧急吊销时不得通过延长缓存缓解 Control 故障。

公共入口不得转发 `/internal/**`。生产 Ingress/Load Balancer 只公开 `/api/v1/**` 和必要 Webhook；`/actuator/info` 需要认证，除健康探针外的 Actuator 路径不得建立公网路由。Compose 已把宿主 Gateway 端口绑定到 `127.0.0.1`，生产网络隔离必须由部署清单和 NetworkPolicy/Firewall 继续落实。

Gateway 使用优雅停机和 20 秒排空窗口。滚动重启前先停止接收新连接，等待存量普通请求；SSE 客户端断开后必须携最后持久 `Last-Event-ID` 重连，连接关闭不取消 Runtime Run。检查 SSE 代理头：

```bash
curl -i -N \
  -H 'Authorization: Bearer <redacted>' \
  -H 'Last-Event-ID: <persisted-session-sequence>' \
  http://127.0.0.1:8080/api/v1/runtime/runs/<run-id>/events:stream
```

预期响应包含 `Content-Type: text/event-stream`、`Cache-Control: no-store` 和 `X-Accel-Buffering: no`。命令中的占位值不得替换为会进入 Shell History 的生产 Token；实际生产验证应使用受控临时终端或 Secret 注入工具。

## 本地 Core/RAG

首次启动 Core 时执行：

```bash
./tools/dev-up.sh
./tools/dev-status.sh
./tools/verify-core.sh
```

`dev-up.sh` 先以 `0600` 生成已忽略的本地 Secret，再验证 Compose、幂等创建 `agentark_identity`、打包四个 JAR、构建非 root 镜像并等待全部容器健康。若已有 MySQL Secret 丢失，脚本将拒绝静默补生。`verify-core.sh` 额外验证七个长期容器、PASSWORD Session、RS256 JWK、四组 Actuator 安全边界、四账号 Schema 隔离以及 Qdrant 未进入 Core。只准备 Secret 与校验配置而不启动时使用：

```bash
./tools/dev-up.sh --prepare-only
```

日常用户从账户菜单进入“修改密码”，必须提供当前密码；成功后全部浏览器会话失效并返回登录页。管理员在“用户与登录”执行“重置密码”时只能取得一次性临时密码，目标用户下次登录必须改密。两条路径都不得直接修改 `identity_password_credential`，只有本机凭据完全遗失且常规管理 API 不可用时才能执行受审 Break-glass 流程。

三个业务 Owner 均从 `V1__phase_06_schema_baseline.sql` 起独立迁移，Gateway Identity 使用自己的 `V1__built_in_identity.sql`；当前 Control 到 V8、Runtime 到 V3、Scheduler 到 V3、Identity 到 V1。若任一 Flyway 校验失败，服务必须保持失败状态；禁止通过关闭 Flyway、修改历史表或启用 `clean` 绕过。确认本地四套独立历史可用：

```bash
./tools/verify-core.sh
```

该脚本验证四个账号只能使用自身 Schema，并分别达到 Control V8/69 表、Runtime V3/13 表、Scheduler V3/9 表、Identity V1/13 表；Migration Checksum 和 N-1 升级由对应 Owner 的 Testcontainers 测试与 Flyway 启动校验共同负责。

显式启动包含 Qdrant 1.18.3 的 RAG Profile：

```bash
./tools/dev-up.sh --profile rag
```

默认启动带 MySQL 内置账号身份的 Core：

```bash
./tools/dev-up.sh
pnpm --dir agentark-web dev
```

浏览器访问 `http://localhost:5173/sign-in` 后直接显示 AgentArk 单列账号登录页。用户名为 `agentark-admin`，也可使用 Identity 表登记的电子邮箱；一次性临时密码只在首次登录时由人工读取：

```bash
cat deploy/compose/.secrets/identity-user-password
```

Gateway 必须在创建完整 Session 前要求立即修改临时密码。验收脚本会在改密成功后将新随机密码原子写回同一 `0600` 文件，值永不输出。账号、Argon2id 摘要、锁定和安全事件保存在 `agentark_identity` MySQL；Redis 丢失只要求重新登录。

验证完整首次改密、Redis Session 和平台管理员账号 API：

```bash
node tools/verify-built-in-identity-login.mjs
```

纯 API、真实 E2E 或已配置外部 IdP 时显式关闭内置 Identity：

```bash
./tools/dev-up.sh --no-identity
./tools/verify-core.sh --no-identity
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
