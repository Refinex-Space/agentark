{{/* 返回稳定的 Chart 名称。 */}}
{{- define "agentark.name" -}}
agentark
{{- end -}}

{{/* 返回带 Release 的资源前缀。 */}}
{{- define "agentark.fullname" -}}
{{- printf "%s-agentark" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* 返回组件完整资源名。 */}}
{{- define "agentark.componentName" -}}
{{- printf "%s-%s" (include "agentark.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* 生成不可变 Digest 镜像；只有非生产本地演练允许 Tag。 */}}
{{- define "agentark.image" -}}
{{- if and (not .root.Values.global.productionValidation) .image.tag -}}
{{- printf "%s:%s" .image.repository .image.tag -}}
{{- else -}}
{{- printf "%s@%s" .image.repository .image.digest -}}
{{- end -}}
{{- end -}}

{{/* 生成通用标签。 */}}
{{- define "agentark.labels" -}}
app.kubernetes.io/name: {{ include "agentark.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/part-of: agentark
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | quote }}
{{- end -}}

{{/* 生成 Pod 选择标签。 */}}
{{- define "agentark.selectorLabels" -}}
app.kubernetes.io/name: {{ include "agentark.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/* 返回组件 ServiceAccount 名称。 */}}
{{- define "agentark.serviceAccountName" -}}
{{- if .root.Values.serviceAccount.create -}}
{{- include "agentark.componentName" . -}}
{{- else -}}
default
{{- end -}}
{{- end -}}

{{/* 返回 Secret 名称。 */}}
{{- define "agentark.secretName" -}}
{{- if .Values.secretManagement.existingSecret -}}
{{- .Values.secretManagement.existingSecret -}}
{{- else -}}
{{- printf "%s-secrets" (include "agentark.fullname" .) -}}
{{- end -}}
{{- end -}}

{{/* 生产模式在渲染阶段拒绝占位、单副本和不安全依赖配置。 */}}
{{- define "agentark.validateProduction" -}}
{{- if .Values.global.productionValidation -}}
  {{- if and (not .Values.secretManagement.externalSecret.enabled) (not .Values.secretManagement.existingSecret) -}}
    {{- fail "production requires secretManagement.existingSecret or externalSecret.enabled" -}}
  {{- end -}}
  {{- $identityMode := .Values.global.external.identity.mode -}}
  {{- if not (has $identityMode (list "builtin" "oidc")) -}}
    {{- fail "production identity mode must be builtin or oidc" -}}
  {{- end -}}
  {{- $issuerUri := ternary .Values.global.external.identity.issuerUri .Values.global.external.oidc.issuerUri (eq $identityMode "builtin") -}}
  {{- $issuer := urlParse $issuerUri -}}
  {{- $vault := urlParse .Values.secretManagement.vault.address -}}
  {{- if or (hasSuffix ".invalid" .Values.global.external.mysql.host) (has .Values.global.external.mysql.host (list "localhost" "127.0.0.1" "::1")) -}}
    {{- fail "production requires a real non-loopback MySQL endpoint" -}}
  {{- end -}}
  {{- if or (hasSuffix ".invalid" .Values.global.external.redis.host) (has .Values.global.external.redis.host (list "localhost" "127.0.0.1" "::1")) -}}
    {{- fail "production requires a real non-loopback Redis endpoint" -}}
  {{- end -}}
  {{- if or (ne $issuer.scheme "https") (hasSuffix ".invalid" $issuer.host) -}}
    {{- fail "production requires a real HTTPS identity issuer" -}}
  {{- end -}}
  {{- if eq $identityMode "builtin" -}}
    {{- $jwkSet := urlParse .Values.global.external.identity.jwkSetUri -}}
    {{- if or (ne $issuer.host .Values.ingress.webHost) (ne $jwkSet.scheme "https") (ne $jwkSet.host .Values.ingress.webHost) -}}
      {{- fail "production built-in identity issuer and JWK Set must use the HTTPS Web host" -}}
    {{- end -}}
    {{- if or (not .Values.global.external.mysql.identitySchema) (not .Values.global.external.mysql.usernames.identity) -}}
      {{- fail "production built-in identity requires an isolated MySQL schema and account" -}}
    {{- end -}}
    {{- range $name, $value := dict "identityDatabasePassword" .Values.secretManagement.keys.identityDatabasePassword "identityBootstrapPassword" .Values.secretManagement.keys.identityBootstrapPassword "identityPasswordPepper" .Values.secretManagement.keys.identityPasswordPepper "identitySigningPrivateKey" .Values.secretManagement.keys.identitySigningPrivateKey -}}
      {{- if not $value -}}
        {{- fail (printf "production built-in identity requires the %s secret key reference" $name) -}}
      {{- end -}}
    {{- end -}}
  {{- else -}}
    {{- if not .Values.global.external.oidc.bffEnabled -}}
      {{- fail "production OIDC mode requires Gateway OIDC BFF" -}}
    {{- end -}}
    {{- if not .Values.global.external.oidc.clientId -}}
      {{- fail "production requires an OIDC confidential client id" -}}
    {{- end -}}
    {{- range $name, $value := dict "redirectUri" .Values.global.external.oidc.redirectUri "postLoginRedirectUri" .Values.global.external.oidc.postLoginRedirectUri "postLogoutRedirectUri" .Values.global.external.oidc.postLogoutRedirectUri -}}
      {{- $uri := urlParse $value -}}
      {{- if or (ne $uri.scheme "https") (ne $uri.host $.Values.ingress.webHost) -}}
        {{- fail (printf "production OIDC %s must use the HTTPS Web host" $name) -}}
      {{- end -}}
    {{- end -}}
    {{- if not .Values.secretManagement.keys.oidcClientSecret -}}
      {{- fail "production requires the OIDC client secret key reference" -}}
    {{- end -}}
  {{- end -}}
  {{- if not .Values.global.external.redis.tls -}}
    {{- fail "production Redis TLS must be enabled" -}}
  {{- end -}}
  {{- if or (not .Values.secretManagement.vault.enabled) (ne $vault.scheme "https") (hasSuffix ".invalid" $vault.host) -}}
    {{- fail "production requires an enabled real HTTPS Vault endpoint" -}}
  {{- end -}}
  {{- if ne .Values.global.external.mysql.sslMode "VERIFY_IDENTITY" -}}
    {{- fail "production MySQL sslMode must be VERIFY_IDENTITY" -}}
  {{- end -}}
  {{- if .Values.global.external.mysql.allowPublicKeyRetrieval -}}
    {{- fail "production MySQL must not enable allowPublicKeyRetrieval" -}}
  {{- end -}}
  {{- if empty .Values.global.externalEgressCidrs -}}
    {{- fail "production requires explicit externalEgressCidrs" -}}
  {{- end -}}
  {{- range $cidr := .Values.global.externalEgressCidrs -}}
    {{- if has $cidr (list "0.0.0.0/0" "::/0" "169.254.0.0/16" "169.254.169.254/32") -}}
      {{- fail (printf "production external egress CIDR %s is forbidden" $cidr) -}}
    {{- end -}}
  {{- end -}}
  {{- if and (not .Values.objectStorage.controlExistingClaim) (not .Values.objectStorage.storageClassName) -}}
    {{- fail "production Control ObjectStore requires an existing claim or explicit storageClassName" -}}
  {{- end -}}
  {{- if and (not .Values.objectStorage.runtimeExistingClaim) (not .Values.objectStorage.storageClassName) -}}
    {{- fail "production Runtime ObjectStore requires an existing claim or explicit storageClassName" -}}
  {{- end -}}
  {{- if not (has "ReadWriteMany" .Values.objectStorage.accessModes) -}}
    {{- fail "production multi-replica ObjectStore requires ReadWriteMany" -}}
  {{- end -}}
  {{- if .Values.objectStorage.ephemeral -}}
    {{- fail "production ObjectStore must not use ephemeral volumes" -}}
  {{- end -}}
  {{- if or (not .Values.sandbox.runtimeClassName) (not .Values.sandbox.runtimeHandler) -}}
    {{- fail "production Sandbox requires runtimeClassName and runtimeHandler" -}}
  {{- end -}}
  {{- range $name, $service := .Values.services -}}
    {{- if lt (int $service.replicas) 2 -}}
      {{- fail (printf "production service %s requires at least two replicas" $name) -}}
    {{- end -}}
    {{- if eq $service.image.digest "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" -}}
      {{- fail (printf "production service %s requires a real image digest" $name) -}}
    {{- end -}}
    {{- if contains ".invalid" $service.image.repository -}}
      {{- fail (printf "production service %s requires a real image repository" $name) -}}
    {{- end -}}
    {{- if $service.image.tag -}}
      {{- fail (printf "production service %s must not use an image tag" $name) -}}
    {{- end -}}
  {{- end -}}
  {{- if eq .Values.migrations.image.digest "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" -}}
    {{- fail "production migrations require a real image digest" -}}
  {{- end -}}
  {{- if contains ".invalid" .Values.migrations.image.repository -}}
    {{- fail "production migrations require a real image repository" -}}
  {{- end -}}
{{- end -}}
{{- end -}}
