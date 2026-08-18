---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Phase 16：Gateway、公共认证、路由、限流与 SSE 代理

## 结论

Phase 16 将 Gateway 从空服务壳升级为无业务状态的统一公共入口。固定路由覆盖 Control、Runtime、Scheduler Public/Callback，`/internal/**` 在边缘终止；OIDC/JWK Bearer JWT 和 Control 摘要验证的 API Key 完成认证前置，目标服务仍重新验证原始签名凭据并执行资源授权。Gateway 不连接业务数据库、不拥有业务 DTO/Entity/Mapper，也不依赖 Control、Runtime 或 Scheduler 实现模块。

默认配置保持失败关闭：没有生产 IdP 时只有 Health 与 Scheduler HMAC Webhook 路由可达，普通 Public API 不匿名放行。Redis 限流默认关闭以支持无 Redis 的构建和探针；生产显式开启后缺少 `RateLimiter` 会启动失败，运行期 Redis 判定异常返回 `503`，不会静默放行。

## 固定上游审计与取用

审计基于 `.agentark/upstreams/agentscope-java-2.0.2/agentscope-service/service-gateway` 固定 Worktree：

| 上游事实 | 分类 | AgentArk 处置 |
|---|---|---|
| YAML 静态路由、Internal 404、外部 Internal/User Header 清理 | `ADAPT` | Java 固定路由表、`/internal/**` 边缘 404、派生身份 Header 清理 |
| Connect Timeout 5 秒、全局响应超时放宽到一小时 | `REFERENCE/REJECT` | 保留 5 秒连接默认；普通响应 30 秒，仅 SSE 路由禁用响应超时 |
| 未发现 Gateway OIDC/JWK、精确 CORS 或 Rate Limiter | `ADAPT` | Foundation JWT/JWK、精确 Origin CORS、Redis 固定窗口限流 |
| 共享 Internal Token 或客户端身份 Header | `REJECT` | 原始签名凭据下游重新验证；派生身份 Header 不进入信任边界 |
| 业务 DTO、Repository、服务实体 | `REJECT` | Gateway POM 与源码均不依赖业务实现或持久化模块 |

没有复制上游源码、YAML 或第三方资产，上游固定 Worktree 未修改。

## 固定路由与边界

路由按显式优先级匹配，避免 Control Catch-all 抢占 Runtime/Scheduler：

| 优先级 | Route ID | 路径 | 目标与策略 |
|---:|---|---|---|
| `-100` | `internal-path-reject` | `/internal/**` | 本地 `404`，不连接下游 |
| `-20` | `runtime-event-stream` | `/api/v1/runtime/runs/*/events:stream` | Runtime；无普通响应超时、禁用缓存/代理缓冲 |
| `0` | `runtime-public` | `/api/v1/runtime/**` | Runtime；普通请求体和超时限制 |
| `0` | `scheduler-webhook` | `POST /api/v1/scheduler/webhooks/**` | Scheduler；独立 1 MiB 上限 |
| `10` | `scheduler-public` | `/api/v1/scheduler/**` | Scheduler；JWT 管理入口 |
| `20` | `control-public` | `/api/v1/**` | Control Catch-all |

所有目标 URL 由 `GatewayProperties` 校验为无 User Info 的 HTTP(S) 绝对 URI。Gateway 只保存 URL 与边缘保护参数，四平面实现仍通过版本化 HTTP 契约隔离。

## JWT、API Key 与下游重新验证

Security Starter 的 Nimbus Decoder 显式限制非对称 JWS 算法集合，默认仅 `RS256`，并继续校验时间、Issuer、Audience 与 JWK 签名。Gateway 的阻塞 Decoder 包装到 `boundedElastic`，不阻塞 Netty Event Loop。JWT 转换后的请求级 Authentication 不保存原始 Token，但代理请求保留原始 `Authorization`，因此 Control、Runtime 和 Scheduler 必须按自身 Audience 与权限再次验证。

API Key 只允许 Control Public 路由。Gateway 将原始 Key 发送到 `POST /internal/v1/auth/api-keys:verify`；Control 仍用本地摘要、Audience、到期、吊销和 Scope 事实独立认证，返回最小 Principal。Gateway 只缓存成功结果：

- 缓存键是原始 Key 的 SHA-256，不保存明文；
- 缓存值只含非秘密 Principal 与过期时刻；
- 默认 TTL 10 秒，配置硬上限 30 秒；
- 无效 Key、Control 401/403 和依赖错误不缓存；
- Control 不可用且缓存未命中时返回 `503`；
- 原始 API Key 仍转发给 Control Public API 再次摘要验证。

Control 新端点不返回 Key 名称、前缀、摘要、明文、到期、版本或数据库字段；普通用户/服务 JWT 不能用它替代 API Key 验证。

## Header、Tenant、CORS 与 Problem Detail

`GatewayHeaderSanitizationFilter` 删除 Principal、Principal Type、Service ID、Authorities、认证后租户与 Client Certificate 等派生 Header，并使用 Foundation 已校验的 `X-Request-Id` 覆盖客户端值。`traceparent` 继续由 Foundation/Observability 按 W3C 语义传播。

Organization、Project、Environment Header 只作为客户端选择意图保留；下游必须结合已认证 Principal、路径资源归属和本地授权事实判断，不能把 Header 当作授权结果。已有 Control/Runtime/Scheduler 跨租户测试继续承担资源级越权门禁。

CORS 默认空白名单；只接受精确 HTTPS Origin，本地只额外允许 loopback HTTP，拒绝通配、User Info、Query、Fragment 和公网明文 HTTP。允许的方法、请求 Header 与响应 Header 均固定列举。安全响应头包含 CSP `default-src 'none'`、Frame Deny、No Referrer、Permissions Policy 和 Spring Security 默认 No Sniff/Cache 策略。

认证、授权、API Key 依赖故障和限流拒绝统一输出 `application/problem+json` 与稳定 `ARK-GATEWAY-*` 错误码，不回显 Token、API Key、下游异常或租户资源细节。

## Redis 限流与健康

普通路由按已认证 Principal Name，Webhook 按实际远端 Socket 来源使用 Foundation Redis 固定窗口原子计数。两类路由使用独立 Namespace、额度和统一窗口；响应返回剩余额度，被拒绝时返回向上取整的 `Retry-After`。

Health 与边缘 Internal 拒绝路径不消耗公共额度。Redis Starter 与 Health Contributor 跟随同一限流开关：限流关闭时 Redis 不影响 Gateway Health；限流开启后 Redis 故障使 Readiness 失败，同时受保护请求失败关闭。Redis 不是认证、租户或业务事实源。

## SSE、重连与排空

SSE 路由保留 `Last-Event-ID`，不改变 Runtime 已持久化 Event ID；设置 `Cache-Control: no-store` 和 `X-Accel-Buffering: no`，并通过负 Route Response Timeout 禁用普通响应超时。普通 API 仍受 30 秒响应超时约束，不因 SSE 放宽全局策略。

Gateway 使用 Spring Boot Graceful Shutdown 和 20 秒关闭阶段。连接关闭不取消 Run；滚动重启或网络中断后，客户端以最后持久 Event ID 重新连接，由 Runtime 回放后追平实时流。Phase 16 的真实 HTTP 集成测试证明 `Last-Event-ID` 和有限 SSE Body 穿过 Gateway；Phase 22 又验证了真实首事件延迟、20 个并发回放连接、节点 Drain 和滚动升级。更高连接规模仍需目标环境容量测试。

## 配置与部署边界

`application.yml` 提供生产安全默认和全部非秘密环境覆盖，`application-local.yml` 只增加三平面本机 URL 与 `http://localhost:5173` 精确 Origin。Compose Gateway 已补充 Scheduler DNS URL，宿主端口继续只绑定 `127.0.0.1`。

生产必须提供 HTTPS Issuer 或 JWK Set、Gateway Audience、受控平面 URL、精确 Origin，并显式开启 Security 与 Redis Rate Limit。生产 Ingress 只公开公共 API 和必要 Webhook；除 Health 外的 Actuator 不建立公网路由，Info 还要求认证。仓库尚无 Kubernetes/Ingress 清单，因此最终网络隔离必须由 Phase 20 部署资产和环境验收完成，不能由当前应用测试代替。

## 契约与验证

`internal-control-v1.yaml` 新增真实 API Key 验证路径和最小响应 Schema；契约 Lint 对完整 Internal Control Path 集合做精确比较。单元与集成测试覆盖：

- Security 未配置时 Public API 401、Internal 路径 404、Health 可用；
- 有效/无效 Bearer JWT 的受保护 Actuator 行为；
- API Key 请求级 Authentication、Runtime 路由拒绝、成功正缓存、TTL 到期和无效结果不缓存；
- 派生身份 Header 删除，Authorization、Tenant Intent 与 `Last-Event-ID` 保留；
- 精确 CORS Origin 校验；
- Redis 限流拒绝、`Retry-After`、Health 绕过，并使用 Redis 8.10.0 Testcontainer 验证真实原子窗口计数；
- 真实 SSE 代理、事件正文、`Last-Event-ID` 与 No-buffer Header；
- Control API Key 自省只允许 `API_KEY` Principal 且响应不含秘密字段；
- Security Starter 拒绝 HMAC JWS 算法配置。

## 最终验收记录

2026-08-16 已在本机完成以下验证：

- `./mvnw -pl agentark-services/agentark-gateway-server -am clean verify`：11 个 Reactor 模块全部成功，Gateway 17 项测试零失败（包含 Redis 8.10.0 真实容器限流）；同时通过 Kernel Contract、中文注释门禁与六个 Foundation Starter 回归；
- `./mvnw -pl agentark-control,agentark-services/agentark-control-server -am clean verify`：13 个 Reactor 模块全部成功，包含 Control 18 项单元测试、10 项 MySQL/IAM 集成测试、Knowledge/Qdrant 回归和 Control Server 启动测试；
- `python3 tools/harness/verify_upstreams.py --require-worktrees`：AgentScope `0c61e7494197ded54eefdeaf9bdeb51807beb752` 与 DeepSeek Harness `47f943859bef60e4160492346772ded9b24f765a` 固定视图校验通过；两个固定视图和两个来源仓库均无新增改动；
- `python3 tools/harness/knowledge_gate.py`：48 份 active 文档通过；
- `docker compose -f deploy/compose/docker-compose.yml --profile core config`：配置解析通过；
- Gateway 禁止业务依赖、ORM/Mapper 扫描和 `git diff HEAD --check` 均通过。

构建日志仍存在 macOS Netty Native DNS 缺失、Mockito 动态 Agent 和 Spring Cloud Gateway 内部 `@Valid` 弃用提示，均不是本阶段失败。未接入真实生产 IdP、Ingress/NetworkPolicy，也未执行 Redis 多副本和长时间 SSE 压力测试；这些边界必须在 Phase 20/22 环境验收中完成，不能由本阶段配置测试替代。

## 已知边界

- 仓库没有真实生产 OIDC Provider、TLS/mTLS、Ingress 或 NetworkPolicy；配置和测试只证明应用边界。
- API Key 正缓存是单实例可丢失缓存，吊销最坏窗口等于 TTL；跨实例主动失效属于后续可靠性增强，不能扩大 30 秒上限。
- Redis 固定窗口不是全局严格平滑限流；Phase 22 只完成本机容量和 Redis 重建语义，跨区域与真实突发整形仍需目标环境验证。
- Scheduler Webhook 的 HMAC、时间窗、Nonce 和重放保护仍由 Scheduler 完成；Gateway 只限制方法、请求体和来源速率。
- 前端可靠 SSE Client、指数重连、去重和可见状态属于 Phase 17。

## 回滚

Gateway 无 Flyway、业务数据或不可逆状态。回滚时先停止新入口流量并等待优雅排空，再回退 Gateway JAR、配置和路由；客户端使用持久 `Last-Event-ID` 恢复 SSE。Control API Key 自省是新增兼容端点，回退 Gateway 后可暂时保留；若一并回退，必须确认没有旧 Gateway 副本调用后再移除实现和契约。Redis 限流键可自然 TTL 过期，不需要删除；禁止为回滚清理业务数据库或 API Key 表。
