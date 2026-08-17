# AgentArk Third-party Notices

This file defines the notice-generation and review boundary for AgentArk. It is not a frozen copy of a moving dependency graph. Every distribution must regenerate the resolved reports and package the license or notice texts required by that exact graph.

## Runtime and Web dependencies

- AgentScope Java Core and Harness are consumed as Maven dependencies at the fixed version recorded in the root POM and upstream baseline. Their upstream project declares Apache License 2.0. AgentArk does not copy AgentScope public types into its contracts.
- Java dependency attributions are generated during Maven `verify` at `target/generated-resources/licenses/THIRD-PARTY.txt`; the resolved CycloneDX graph is `target/bom.json`.
- Web dependencies are fixed by `agentark-web/pnpm-lock.yaml`. The current production closure uses permissive SPDX licenses recorded by the Phase 17/20 license evidence; release packaging must regenerate the lockfile license report.
- Web and non-Maven repository components are recorded in `target/security/agentark-repository.cdx.json` by `tools/security/generate-sbom.sh`; Java remains authoritative in the Maven aggregate BOM.

## Security and build tooling

Trivy, Cosign, GitHub Actions and CodeQL are build/security tooling and are not bundled into AgentArk runtime artifacts by this repository. Workflow references are fixed to container digests or Action commits. If a downstream distribution embeds any of these tools, it must include that tool's applicable license and notices independently.

## Referenced upstream projects

DeepSeek Harness is used only as a fixed-commit visual and interaction reference. AgentArk does not distribute DeepSeek logos, names, plugin runtime, source files, screenshots, special-license payloads or its generated third-party notice file. AgentScope Service/Framework source is likewise reference evidence unless a file-level migration manifest explicitly says otherwise.

## Release gate

Before publishing a distribution, run:

```bash
./mvnw -B -ntp verify
pnpm --dir agentark-web licenses list --prod --json
./tools/security/generate-sbom.sh
./tools/security/scan-repository.sh
```

Review unknown, source-available, copyleft or `SEE LICENSE` results before release. Do not infer that a repository license grants trademark or image rights.
