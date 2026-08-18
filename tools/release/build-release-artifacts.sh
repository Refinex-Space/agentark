#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 发布输出只写入 Maven clean 管理的 target 目录。
readonly RELEASE_DIRECTORY="${REPOSITORY_ROOT}/target/release"

cd "${REPOSITORY_ROOT}"

# 发布构建必须使用项目固定的 Node.js 24 与 pnpm 11.22.0，不能只接受 Engine 警告。
readonly NODE_VERSION="$(node --version)"
readonly PNPM_VERSION="$(pnpm --dir agentark-web --version)"
if [[ ! "${NODE_VERSION}" =~ ^v24\.[0-9]+\.[0-9]+$ ]]; then
  echo "release artifacts require Node.js 24, found ${NODE_VERSION}" >&2
  exit 1
fi
if [[ "${PNPM_VERSION}" != "11.22.0" ]]; then
  echo "release artifacts require pnpm 11.22.0, found ${PNPM_VERSION}" >&2
  exit 1
fi

# Source Artifact 必须只代表一个提交，拒绝把未提交内容静默混入发布物。
if [[ -n "$(git status --porcelain)" ]]; then
  echo "release artifacts require a clean Git worktree" >&2
  exit 1
fi

readonly VERSION="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)"
readonly REVISION="$(git rev-parse HEAD)"
readonly BUILD_DATE="$(git show -s --format=%cI HEAD)"
readonly EXPECTED_TAG="v${VERSION}"

# 正式发布必须由与 Maven 版本一致的精确 Tag 触发；本地演练可显式关闭 Tag 检查。
if [[ "${AGENTARK_RELEASE_REQUIRE_TAG:-true}" == "true" ]]; then
  actual_tag="$(git describe --tags --exact-match HEAD 2>/dev/null || true)"
  if [[ "${actual_tag}" != "${EXPECTED_TAG}" ]]; then
    echo "release commit must have exact tag ${EXPECTED_TAG}" >&2
    exit 1
  fi
fi

./mvnw -B -ntp -T 1C clean verify
pnpm --dir agentark-web install --frozen-lockfile
pnpm --dir agentark-web api:check
pnpm --dir agentark-web lint
pnpm --dir agentark-web typecheck
pnpm --dir agentark-web test
pnpm --dir agentark-web build
./tools/security/generate-sbom.sh target/security

mkdir -p "${RELEASE_DIRECTORY}"

# Git Archive 保证 Source Artifact 只包含当前提交中的受版本控制内容。
git archive --format=tar --prefix="agentark-${VERSION}/" HEAD \
  | gzip -n >"${RELEASE_DIRECTORY}/agentark-${VERSION}-source.tar.gz"

# Maven 分发包保留全部模块 JAR、POM、法律文本、许可证报告和 CycloneDX。
readonly MAVEN_STAGE="$(mktemp -d)"
readonly WEB_STAGE="$(mktemp -d)"
cleanup() {
  rm -rf "${MAVEN_STAGE}" "${WEB_STAGE}"
}
trap cleanup EXIT

mkdir -p "${MAVEN_STAGE}/artifacts" "${MAVEN_STAGE}/licenses" "${MAVEN_STAGE}/sbom"
while IFS= read -r artifact; do
  relative="${artifact#${REPOSITORY_ROOT}/}"
  destination="${MAVEN_STAGE}/artifacts/${relative}"
  mkdir -p "$(dirname "${destination}")"
  cp "${artifact}" "${destination}"
done < <(find "${REPOSITORY_ROOT}" \
  -path '*/target/*.jar' -type f \
  -not -path '*/original-*' \
  -not -path '*/.agentark/*' \
  | LC_ALL=C sort)
cp LICENSE NOTICE THIRD_PARTY_NOTICES.md "${MAVEN_STAGE}/licenses/"
cp target/generated-resources/licenses/THIRD-PARTY.txt "${MAVEN_STAGE}/licenses/"
cp target/bom.json "${MAVEN_STAGE}/sbom/maven.cdx.json"

jar --create --file "${RELEASE_DIRECTORY}/agentark-${VERSION}-maven.zip" \
  --date="${BUILD_DATE}" -C "${MAVEN_STAGE}" .

# Web 分发包只包含生产静态资源、前端许可证闭包和仓库法律文本。
cp -R agentark-web/dist "${WEB_STAGE}/dist"
pnpm --dir agentark-web licenses list --prod --json \
  >"${WEB_STAGE}/web-licenses.json"
cp LICENSE NOTICE THIRD_PARTY_NOTICES.md "${WEB_STAGE}/"
jar --create --file "${RELEASE_DIRECTORY}/agentark-web-${VERSION}.zip" \
  --date="${BUILD_DATE}" -C "${WEB_STAGE}" .

cp target/bom.json "${RELEASE_DIRECTORY}/agentark-maven-${VERSION}.cdx.json"
cp target/security/agentark-repository.cdx.json \
  "${RELEASE_DIRECTORY}/agentark-repository-${VERSION}.cdx.json"
cp docs/releases/v0-1-0.md "${RELEASE_DIRECTORY}/RELEASE_NOTES.md"

cat <<EOF >"${RELEASE_DIRECTORY}/release-manifest.txt"
version=${VERSION}
revision=${REVISION}
tag=${EXPECTED_TAG}
source=agentark-${VERSION}-source.tar.gz
maven=agentark-${VERSION}-maven.zip
web=agentark-web-${VERSION}.zip
mavenSbom=agentark-maven-${VERSION}.cdx.json
repositorySbom=agentark-repository-${VERSION}.cdx.json
checksums=SHA256SUMS
containerDigests=由 Registry 推送结果逐镜像记录
signatures=由 .github/workflows/supply-chain.yml 使用 GitHub OIDC 生成
provenance=由 GitHub Artifact Attestation 绑定提交和制品摘要
EOF

# Checksums 覆盖所有离线发布物和 Manifest；签名与 Provenance 绑定同一摘要。
(
  cd "${RELEASE_DIRECTORY}"
  find . -maxdepth 1 -type f ! -name SHA256SUMS -print \
    | LC_ALL=C sort \
    | while IFS= read -r file; do
        shasum -a 256 "${file}"
      done \
    | sed 's#  \./#  #' >SHA256SUMS
)

echo "${RELEASE_DIRECTORY}"
