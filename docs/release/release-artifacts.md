---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 0.1.0 发布物、校验与 Provenance

## 离线发布物

`tools/release/build-release-artifacts.sh` 只接受干净 Git 工作区，并默认要求 HEAD 精确标记为与 Maven 版本一致的 `v0.1.0`。它生成：

| 发布物 | 内容 |
|---|---|
| `agentark-0.1.0-source.tar.gz` | 当前 Tag 的 `git archive`，不含构建输出、上游 Worktree 或本地数据 |
| `agentark-0.1.0-maven.zip` | 全部模块 JAR、法律文本、Maven 许可证报告和聚合 CycloneDX |
| `agentark-web-0.1.0.zip` | Vite 生产静态资源、Web 生产依赖许可证和法律文本 |
| `agentark-maven-0.1.0.cdx.json` | Maven 实际解析的运行依赖 SBOM |
| `agentark-repository-0.1.0.cdx.json` | JavaScript、容器与 IaC 视角的仓库 SBOM |
| `SHA256SUMS` | 所有离线发布文件的 SHA-256 |
| `release-manifest.txt` | 版本、Commit、Tag、文件与签名/Provenance Owner |

## 容器发布物

六个容器 Owner 为 Gateway、Control、Runtime、Scheduler、Web 和 Flyway Migration。生产引用必须使用 Registry 返回的 `repository@sha256:<digest>`，不得使用可变 Tag。`tools/production/build-images.sh` 负责本地构建；Registry 推送、Trivy 扫描、Cosign Keyless 签名和 Provenance 由固定 Commit 的供应链工作流绑定到镜像摘要。

## 校验

离线校验：

```bash
cd target/release
shasum -a 256 -c SHA256SUMS
```

GitHub Artifact Attestation 与镜像签名校验必须使用发布仓库和 OIDC 身份约束；验证方应同时确认 Tag、Commit、SBOM Subject、镜像 Digest 与 Release Notes 一致，不能只验证 Tag 名。

`.github/workflows/release.yml` 在精确版本 Tag 上重跑静态门禁和构建脚本，上传全部离线文件并签发 GitHub OIDC Build Provenance；它不自动创建 GitHub Release，也不获得 Registry 写权限。六个容器仍由 `.github/workflows/supply-chain.yml` 对人工提供的 Registry Digest 逐一扫描、Cosign Keyless 签名和证明。

## 本地演练边界

收官阶段可在一次性干净临时 Git 仓库中设置 `AGENTARK_RELEASE_REQUIRE_TAG=false` 验证打包逻辑，但该输出不得发布。正式发布始终要求原仓库工作区干净、精确 Tag、CI OIDC Attestation 和 Registry Digest。
