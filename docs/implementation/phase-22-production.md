---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Phase 22：Kubernetes、HA、备份恢复、性能与故障演练

## 结论

Phase 22 已把四个 Java 部署单元和独立 Web 落到可验证的生产容器与 Helm 拓扑，并用真实三节点 kind/Calico、MySQL PITR、Qdrant Snapshot、k6 和定向故障测试验证关键质量属性。Chart 只连接外部 MySQL、Redis、Object Store、Qdrant 与 IdP，不把数据库或向量服务伪装成生产内置依赖；默认 Java-only，不包含 Go Aistio、Nacos、Service Mesh、Kafka、Elasticsearch 或 Neo4j。

## 容器与供应链

- `Dockerfile.service` 只允许四个 Server 模块，多阶段 Maven/JRE 构建，固定基础镜像 Digest，运行 UID/GID `65532`，只读根兼容并提供 OCI Label、Build Info 和健康检查。
- `Dockerfile.web` 使用固定 Node 24 和 Nginx Unprivileged Digest，运行 UID `101`；Nginx 提供 SPA Fallback、安全响应头和无状态健康端点。
- `Dockerfile.migrations` 从三个 Owner 模块提取真实 Flyway SQL，运行时必须显式选择单一 Location 和最小权限账号。
- 既有 `supply-chain.yml` 继续生成 CycloneDX、Artifact Attestation、镜像扫描、Cosign Keyless 签名和 Provenance；生产只接受 Registry Digest。

## Helm 与 Kubernetes

`deploy/helm/agentark` 提供五个无状态 Deployment、Service、Ingress、独立 ServiceAccount/RBAC、ConfigMap、ExternalSecret、NetworkPolicy、SecurityContext、PDB、HPA/KEDA、Topology Spread、Affinity、资源限制、Startup/Readiness/Liveness、PreStop、Flyway Hook Job、Object PVC、Sandbox Namespace/RuntimeClass 和可选 OTel Collector。

Runtime 和 Scheduler 不依赖 Sticky Session；Readiness 与 `preStop` 配合 Drain，MySQL 中的 Runtime Instance 状态仍是是否可 Claim 的权威事实。Sandbox 使用独立 Namespace、默认拒绝网络、ResourceQuota/LimitRange 和专用 RuntimeClass，平台必须安装真正的隔离运行时后才允许开启。

生产 Values Schema 与模板门禁要求镜像 Digest、HTTPS OIDC、MySQL `VERIFY_IDENTITY`、非临时 Object Store、Secret 引用和显式存储类。`helm lint` 与 kubeconform 对默认/生产共 114 个资源严格校验通过。

## 真实演练暴露并修正的问题

1. Spring Boot 4.1 对空 Map 环境绑定失败：移除 Control YAML 中多余的空 `trusted-skill-signing-keys`，保留 Properties 默认空 Map。
2. Alpine JRE 缺少默认 LXM Random Provider：Scheduler 退避随机源改用 JDK 基础 `Random`，保持注入接口与测试确定性。
3. Secret 文件尾换行被 Kubernetes 原样注入：kind 演练用无换行随机文件，避免数据库口令错配。
4. NetworkPolicy 与 Pod 标签漂移：所有应用、Migration、OTel Pod 显式带 `part-of=agentark`，实际进入默认拒绝范围；同 Release 内访问允许，外部 Namespace 阻断。
5. Scheduler Trigger Outbox 契约漂移：应用写 `aggregate_type='trigger'`，V2 CHECK 未包含该值。规范模型先更新，再新增不可变历史之后的 V3 前向迁移，并用 MySQL 8.4 测试允许 Trigger、拒绝未知值。
6. Redis/Vault 生产 Values 未真正传递到应用：Gateway/Runtime 现显式接收 Spring Redis TLS 开关，Control 现接收 Vault KV v2 地址/Mount/Namespace；生产门禁会拒绝明文 Redis、HTTP/占位 Vault、HTTP IdP、占位 Registry 和全网/元数据出口。

## 验证证据

- `./mvnw -pl agentark-services/agentark-control-server,agentark-services/agentark-scheduler-server -am clean verify`：15 个 Reactor 模块成功；
- `./mvnw -T 1C clean verify`：20 个 Reactor 模块在 3 分 04 秒内全部成功；新增大文档批次测试随后以定向 Reactor 再次通过；
- Web 的冻结安装、生成 Client 漂移检查、ESLint/Prettier、TypeScript、7 个测试文件/12 个用例和 Vite 生产构建全部通过；
- Scheduler V1→V3/空库迁移与 Outbox 约束：9 个 MySQL 8.4 测试通过；
- Helm/kubeconform：114 个资源有效，0 Invalid、0 Error；
- kind：Kubernetes v1.35.0、Calico v3.32.1、五个工作负载各 2 副本，Control/Runtime/Scheduler Flyway 为 7/3/3，安装 59 秒、节点 Drain 恢复 31 秒、Runtime 滚动升级 27 秒、`runtime_drained=DRAINED`、跨 Namespace 阻断；
- 恢复：MySQL PITR 9 秒、Qdrant 1 Point、Redis 空重建、总计 22 秒；
- k6：6,210 请求、0 失败、6,111 检查全成功；读/写/Turn/Event/Scheduler P95 分别为 36.84/633.10/259.82/376.65/1,920 ms；真实 SSE 首事件为 205.97 ms，并保持 20 个并发回放连接 3 秒；
- 故障回归：Runtime Lease/Fencing/Recovery/SSE/Approval、AgentScope Provider 429/Timeout、Scheduler Worker/Cron、Qdrant Testcontainers 和 OTel 后端不可用边界全部通过；
- 大文档摄取回归：4,096 个 Chunk 严格拆成 64 个、每批 64 个的 Embedding 请求，未形成无界 Provider Batch；Snapshot Compiler 的 Single Flight、缓存淘汰和可重建行为继续由 Provider 契约测试覆盖；
- `production.yml` 已把 Helm/Values/Kubernetes Schema、Compose 和演练脚本语法接入最小只读 CI；镜像 SBOM、扫描、签名和 Provenance 继续由固定 Action Commit 的供应链工作流承担；
- 详细容量、RPO/RTO 边界见 [容量报告](../operations/phase-22-capacity-rpo-rto.md)。

## 已知边界

- 本机快速 Fixture 不等价于生产容量、跨区网络、KMS、托管数据库或真实业务数据恢复；生产上线必须重复演练并审批。
- Helm 默认 OTel Collector 是接收/转发骨架，后端地址、TLS 与凭据由部署方提供。
- Qdrant、Elasticsearch、Neo4j 只提供外部连接槽位；默认未部署 ES/Neo4j。
- KEDA 默认关闭，启用前必须批准低基数 Prometheus 查询和后端故障策略。
- Sandbox RuntimeClass 在 kind 中只静态校验；真实 gVisor/Kata 等隔离运行时和节点池属于平台验收。

## 回滚

应用与 Chart 可回滚到支持当前 Snapshot/Event/DB Schema 的旧镜像 Digest。已应用 Flyway 不降级，Scheduler V3 如需收紧只能新增前向迁移。节点/恢复演练只删除精确命名的临时资源；生产恢复失败时保留只读实例并从不可变备份重建，不覆盖原始证据。
2026-08-19 Errata：生产 Ingress 在 Web Host 上将 `/api`、`/oauth2` 和 `/login/oauth2` 同源路由到 Gateway，以承载 HttpOnly Session、可选 Authorization Code Callback 和 Token Relay；API Host 继续服务 SDK/机器调用。

2026-08-21 Built-in Identity Errata：Chart 新增 `global.external.identity.mode=builtin|oidc` 互斥门禁。默认 Built-in 模式使用第四个 Identity Flyway Hook Job、独立 MySQL Schema/账号、Redis Session，以及从 ExistingSecret/ExternalSecret 注入的数据库密码、初始随机密码、Pepper 和 PKCS#8 RSA 私钥；Issuer 与 JWK Set 必须使用 Web HTTPS Host。OIDC 模式不运行 Identity Job，并继续要求 Confidential Client Secret、HTTPS Callback 和 BFF。
