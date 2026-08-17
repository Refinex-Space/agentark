---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md#安全运维
---

# Secret 轮换与吊销 Runbook

## 适用范围

本流程只处理 Secret Metadata、外部 Provider 版本和工作负载 Vault Token。操作人员不得读取、复制、粘贴或记录 Secret 值。任何示例都只使用资源标识和版本号。

## 常规轮换

1. 在 Vault 或实际 Secret Manager 创建新的不可变版本，保留旧版本用于短窗口回滚。
2. 确认新版本满足 Provider 自身访问策略、到期时间和区域要求，不通过 AgentArk Public API读取值。
3. 读取 Secret Metadata 当前 `version`，调用 `POST /api/v1/projects/{projectId}/secrets/{secretMetadataId}:rotate`，只提交 `externalVersion` 和 `expectedVersion`。
4. 若返回 `409`，重新读取 Metadata 并判断是否已有其他操作者完成轮换；禁止盲目覆盖乐观锁。
5. 验证新 Run 能解析新版本，旧 Run 是否继续使用旧值由 Snapshot 的 `resolutionPolicy` 决定。
6. 在 Audit 中确认 `secret.metadata.rotate` 和后续 `secret.value.resolve` 成功记录；Audit 不应包含路径、版本或值。
7. 观察一个完整业务窗口后，在外部 Provider 禁用旧版本；不要在确认新版本前删除。

## Vault 工作负载 Token 轮换

Vault Token 文件由工作负载身份或 Sidecar 原子替换。Resolver 每次请求重新读取，因此无需重启服务。替换后验证文件是专用绝对路径、普通文件、非符号链接、内容无空白且不超过 8 KiB。不要在 Shell 输出 Token，也不要将 Token 作为环境变量或命令参数传入诊断工具。

## 紧急禁用

1. 调用 `:disable` 并提供当前 `expectedVersion`；新解析请求立即失败关闭。
2. 吊销外部 Provider 权限或版本，撤销关联工作负载身份。
3. 取消或隔离仍持有短生命周期值的 Run；不要假设禁用 Metadata 能擦除已在外部 SDK 内存中的值。
4. 检查 Audit、Runtime Event、Trace 和日志脱敏门禁；若发现值，立即转入安全事件流程。
5. 修复后可以调用 `:enable`。已 `REVOKED` 的 Metadata 不能重新启用。

## 永久吊销

确认该逻辑引用不再允许恢复后调用 `:revoke`。随后吊销外部版本、删除绑定、安排安全删除与保留审计。`REVOKED` 是终态；需要重新启用时创建新的 Metadata 稳定身份，不修改历史记录。

## 回滚

只有尚未吊销且仍受 Provider 保留策略保护的旧版本可以作为回滚目标。再次调用 `:rotate` 指向旧版本，并使用最新乐观锁版本。不得回滚 Git 配置来恢复 Secret 值，也不得启用 Local Provider 代替生产 Provider。

## 验证

```bash
./mvnw -pl agentark-control -Dtest='VaultKvV2SecretResolverTest,*Secret*' test
rg -n "secret|token|credential" target agentark-*/target -g '*.log' -g '*.json'
./tools/security/scan-repository.sh
```

第二条命令只用于定位需要人工复核的字段名，不能把匹配结果当作值泄漏；若输出疑似真实凭据，立即停止复制日志并按事件流程处置。
