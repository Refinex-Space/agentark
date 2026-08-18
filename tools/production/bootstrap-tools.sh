#!/usr/bin/env bash

set -euo pipefail

# 根据脚本位置解析仓库根目录。
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# 工具只安装到已忽略目录，不修改系统 Homebrew 或 `/usr/local/bin`。
readonly BIN_DIRECTORY="${REPOSITORY_ROOT}/.agentark/bin"
# 下载缓存位于已忽略目录。
readonly CACHE_DIRECTORY="${REPOSITORY_ROOT}/.agentark/cache/phase22-tools"
# 固定 Helm 4 稳定版本。
readonly HELM_VERSION="4.2.0"
# 固定 kind 版本。
readonly KIND_VERSION="0.31.0"
# 固定 Kubernetes Schema 校验器版本。
readonly KUBECONFORM_VERSION="0.7.0"
# 固定 k6 负载工具版本。
readonly K6_VERSION="2.1.0"

# 只支持 CI 和开发机使用的四种平台组合。
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)
    readonly PLATFORM="darwin-arm64"
    readonly K6_PLATFORM="macos-arm64"
    readonly K6_ARCHIVE="zip"
    readonly HELM_SHA="f13f959015447b6bc309f9fd506509926543988a39035c088b52522ec95e2acb"
    readonly KIND_SHA="88bf554fe9da6311c9f8c2d082613c002911a476f6b5090e9420b35d84e70c5c"
    readonly KUBECONFORM_SHA="b5d32b2cb77f9c781c976b20a85e2d0bc8f9184d5d1cfe665a2f31a19f99eeb9"
    readonly K6_SHA="7388e449816fdb98afe65f349f3983304ba5e14f73b6bee0d7f96c4e3e6b8942"
    ;;
  Darwin-x86_64)
    readonly PLATFORM="darwin-amd64"
    readonly K6_PLATFORM="macos-amd64"
    readonly K6_ARCHIVE="zip"
    readonly HELM_SHA="1376ea697140e4db316736e760d5a47d12afc1524dce704476ef06fd7fdeddc6"
    readonly KIND_SHA="a8b3cf77b2ad77aec5bf710d1a2589d9117576132af812885cad41e9dede4d4e"
    readonly KUBECONFORM_SHA="c6771cc894d82e1b12f35ee797dcda1f7da6a3787aa30902a15c264056dd40d4"
    readonly K6_SHA="a600f44ad411ad5f5f7d178405d9956dac34c43563341396f1017ae7f79221a9"
    ;;
  Linux-x86_64)
    readonly PLATFORM="linux-amd64"
    readonly K6_PLATFORM="linux-amd64"
    readonly K6_ARCHIVE="tar.gz"
    readonly HELM_SHA="97dbeb971be4ac4b27e3839976d9564c0fb35c6f3b1da89dd1e292d236af4096"
    readonly KIND_SHA="eb244cbafcc157dff60cf68693c14c9a75c4e6e6fedaf9cd71c58117cb93e3fa"
    readonly KUBECONFORM_SHA="c31518ddd122663b3f3aa874cfe8178cb0988de944f29c74a0b9260920d115d3"
    readonly K6_SHA="295d961ebfca306f295f1133068dcd403a8171c87f387928f5f30b0fbcff858a"
    ;;
  Linux-aarch64|Linux-arm64)
    readonly PLATFORM="linux-arm64"
    readonly K6_PLATFORM="linux-arm64"
    readonly K6_ARCHIVE="tar.gz"
    readonly HELM_SHA="1f8de130dfbd04de64978e7b852a7a547be1404956a366608276d2520b678670"
    readonly KIND_SHA="8e1014e87c34901cc422a1445866835d1e666f2a61301c27e722bdeab5a1f7e4"
    readonly KUBECONFORM_SHA="cc907ccf9e3c34523f0f32b69745265e0a6908ca85b92f41931d4537860eb83c"
    readonly K6_SHA="191fa8d89512a4e5083f3fabcb4c3828af9f5b9eee016de8443f6473c029ffb5"
    ;;
  *)
    echo "unsupported Phase 22 tool platform" >&2
    exit 2
    ;;
esac

# 创建受控安装和缓存目录。
mkdir -p "${BIN_DIRECTORY}" "${CACHE_DIRECTORY}"

# 下载文件并校验固定 SHA-256。
download() {
  local url="$1"
  local output="$2"
  local expected_sha="$3"
  curl --fail --silent --show-error --location "${url}" --output "${output}"
  local actual_sha
  actual_sha="$(shasum -a 256 "${output}" | awk '{print $1}')"
  if [[ "${actual_sha}" != "${expected_sha}" ]]; then
    echo "checksum mismatch for ${url}" >&2
    exit 1
  fi
}

# 安装 Helm。
download \
  "https://get.helm.sh/helm-v${HELM_VERSION}-${PLATFORM}.tar.gz" \
  "${CACHE_DIRECTORY}/helm.tar.gz" \
  "${HELM_SHA}"
tar -xOzf "${CACHE_DIRECTORY}/helm.tar.gz" "${PLATFORM}/helm" > "${BIN_DIRECTORY}/helm"

# 安装 kind。
download \
  "https://github.com/kubernetes-sigs/kind/releases/download/v${KIND_VERSION}/kind-${PLATFORM}" \
  "${BIN_DIRECTORY}/kind" \
  "${KIND_SHA}"

# 安装 kubeconform。
download \
  "https://github.com/yannh/kubeconform/releases/download/v${KUBECONFORM_VERSION}/kubeconform-${PLATFORM}.tar.gz" \
  "${CACHE_DIRECTORY}/kubeconform.tar.gz" \
  "${KUBECONFORM_SHA}"
tar -xOzf "${CACHE_DIRECTORY}/kubeconform.tar.gz" kubeconform > "${BIN_DIRECTORY}/kubeconform"

# 安装 k6。
download \
  "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/k6-v${K6_VERSION}-${K6_PLATFORM}.${K6_ARCHIVE}" \
  "${CACHE_DIRECTORY}/k6.${K6_ARCHIVE}" \
  "${K6_SHA}"
if [[ "${K6_ARCHIVE}" == "zip" ]]; then
  unzip -p "${CACHE_DIRECTORY}/k6.zip" "k6-v${K6_VERSION}-${K6_PLATFORM}/k6" > "${BIN_DIRECTORY}/k6"
else
  tar -xOzf "${CACHE_DIRECTORY}/k6.tar.gz" "k6-v${K6_VERSION}-${K6_PLATFORM}/k6" > "${BIN_DIRECTORY}/k6"
fi

# 工具仅对当前用户可执行。
chmod 0755 \
  "${BIN_DIRECTORY}/helm" \
  "${BIN_DIRECTORY}/kind" \
  "${BIN_DIRECTORY}/kubeconform" \
  "${BIN_DIRECTORY}/k6"

# 输出无敏感信息的实际版本。
"${BIN_DIRECTORY}/helm" version --short
"${BIN_DIRECTORY}/kind" version
"${BIN_DIRECTORY}/kubeconform" -v
"${BIN_DIRECTORY}/k6" version

