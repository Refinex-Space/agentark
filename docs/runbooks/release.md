---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Release Runbook

## 前置条件

- `main` 上的候选 Commit 已通过 G0–G9，工作区干净，版本、CHANGELOG、Release Notes 和 Compatibility Matrix 一致。
- 六个容器 Registry 名称、OIDC 发布身份、GHCR/目标 Registry 权限和 Artifact Attestation 权限已批准。
- 目标环境的生产 Values、Secret、备份点、容量和回滚 Digest 已由 Owner 复核。

## 生成候选发布物

```bash
./tools/release/verify-release-readiness.sh
git tag --sign v0.1.0
./tools/release/build-release-artifacts.sh
```

本 Runbook 不授权 Agent 自动创建或推送 Tag。Tag、发布和 Registry 写入必须由发布人员明确执行。

## 校验和发布

1. 在 `target/release/` 验证 `SHA256SUMS`、版本、Commit、Tag、SBOM 与许可证报告。
2. 构建并推送六个镜像，记录每个 Registry Digest；禁止用本地 Image ID 或 Tag 替代 Digest。
3. 对每个 Digest 执行 Trivy、Cosign Keyless 签名和 Provenance Attestation。
4. 使用生产 Values 在隔离环境 Helm Template、Schema 校验和 Server-side Dry Run。
5. 执行 Migration Job、四服务滚动部署、Web 发布、Smoke/E2E、安全和可观测检查。
6. 发布 Release Notes、Known Limitations、Checksum、SBOM、签名与 Provenance 验证说明。

## 停止条件

任一 Gate、Migration、签名、Digest、租户隔离、SSE/HITL、恢复或关键告警失败时停止发布；不要通过更换 Tag、跳过 Job 或放宽 Values Schema 继续。
