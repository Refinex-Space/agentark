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

## Adapted Web source

The AgentArk React sign-in and required-password-change surfaces adapt the shadcn/ui `login-05` registry block resolved with `shadcn@4.18.0`. Acme branding, example email, Apple/Google provider artwork and placeholder legal links are not distributed.

MIT License

Copyright (c) 2023 shadcn

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Release gate

Before publishing a distribution, run:

```bash
./mvnw -B -ntp verify
pnpm --dir agentark-web licenses list --prod --json
./tools/security/generate-sbom.sh
./tools/security/scan-repository.sh
```

Review unknown, source-available, copyleft or `SEE LICENSE` results before release. Do not infer that a repository license grants trademark or image rights.
