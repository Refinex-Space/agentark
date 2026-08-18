import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const sessionPath = resolve(process.argv[2] ?? "agentark-web/test-results/e2e-session.json");
const outputPath = resolve(
  process.argv[3] ?? ".agentark/evidence/phase22/performance-seed.json",
);
const session = JSON.parse(readFileSync(sessionPath, "utf8"));
const suffix = `${Date.now().toString(36)}-${process.pid.toString(36)}`;
const baseUrl = "http://127.0.0.1:8080";

/** 返回带真实 JWT 与租户选择意图的请求头。 */
function headers(extra = {}) {
  return {
    Authorization: `Bearer ${session.userToken}`,
    "X-AgentArk-Organization-Id": session.organizationId,
    "X-AgentArk-Project-Id": session.projectId,
    "X-AgentArk-Environment-Id": session.environmentId,
    ...extra,
  };
}

/** 调用真实 Gateway API，并在失败时只输出状态与脱敏响应。 */
async function api(path, options, expectedStatus) {
  const response = await fetch(`${baseUrl}${path}`, options);
  if (response.status !== expectedStatus) {
    const detail = (await response.text())
      .replace(/(token|secret|password)(\s*[=:]\s*)\S+/gi, "$1$2<redacted>")
      .slice(0, 1000);
    throw new Error(`${options.method} ${path} returned ${response.status}: ${detail}`);
  }
  return expectedStatus === 204 ? undefined : response.json();
}

/** 创建稳定资产及一个不可变 PUBLISHED 版本。 */
async function createAsset(kind, key, metadata, payload) {
  const asset = await api(
    `/api/v1/projects/${session.projectId}/catalog/${kind}`,
    {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        key,
        name: `Phase22 ${key}`,
        description: "Phase 22 本机容量基线资产",
        metadata,
      }),
    },
    201,
  );
  const version = await api(
    `/api/v1/projects/${session.projectId}/catalog/${kind}/${asset.id}/versions`,
    {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({ payload, status: "PUBLISHED" }),
    },
    201,
  );
  return { ownerId: asset.id, versionId: version.id };
}

const secretKey = `perf-model-${suffix}`;
await api(
  `/api/v1/projects/${session.projectId}/secrets`,
  {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      key: secretKey,
      name: "Phase 22 Performance Credential Metadata",
      provider: "LOCAL_FILE",
      externalPath: `/phase22/${secretKey}`,
      scope: "PROJECT",
    }),
  },
  201,
);
const secretRef = `secret://project/${session.projectId}/${secretKey}`;

const prompt = await createAsset("prompt", `perf-prompt-${suffix}`, {}, {
  template: "You are a deterministic Phase 22 capacity probe.",
  variableSchema: {},
  purpose: "SYSTEM",
});
const model = await createAsset(
  "model-provider",
  `perf-model-${suffix}`,
  { providerType: "OPENAI_COMPATIBLE", descriptor: { endpoint: "performance-fixture" } },
  {
    modelName: "performance-model",
    capabilities: ["TOOL", "STREAMING"],
    parameters: { temperature: 0, maxTokens: 128 },
    credentialSecretRef: secretRef,
  },
);
const mcp = await createAsset("mcp-server", `perf-mcp-${suffix}`, {}, {
  transport: "STDIO",
  commandName: "phase22-mcp",
  transportConfig: {},
  ssrfPolicy: {
    denyPrivateNetworks: true,
    denyCloudMetadata: true,
    resolveAndPinDns: true,
  },
  tools: [
    {
      name: "phase22.verify",
      argumentSchema: { type: "object" },
      accessMode: "READ",
      riskLevel: "MEDIUM",
      idempotency: "IDEMPOTENT",
      permissionMetadata: {},
    },
  ],
});

const artifactBody = new FormData();
artifactBody.append(
  "file",
  new Blob(["# Phase 22 Performance Skill\n"], { type: "text/markdown" }),
  "SKILL.md",
);
const artifact = await api(
  `/api/v1/projects/${session.projectId}/skill-artifacts`,
  { method: "POST", headers: headers(), body: artifactBody },
  201,
);
const skill = await createAsset("skill", `perf-skill-${suffix}`, {}, {
  artifact,
  sourceUri: `urn:agentark:phase22-skill-${suffix}`,
  license: "Apache-2.0",
  compatibility: { runtimeProvider: "agentscope-java-2" },
});
const memory = await createAsset("memory-profile", `perf-memory-${suffix}`, {}, {
  strategy: "session",
});
const workspace = await createAsset("workspace-profile", `perf-workspace-${suffix}`, {}, {
  isolation: "session",
});
const sandbox = await createAsset("sandbox-profile", `perf-sandbox-${suffix}`, {}, {
  mode: "restricted",
});
const policy = await createAsset("permission-policy", `perf-policy-${suffix}`, {}, {
  defaultDecision: "DENY",
  rules: [{ resource: "tool:phase22.verify", decision: "ASK" }],
  scopes: [],
  approvalPolicy: { mode: "required" },
});

const draft = {
  runtimeProvider: "agentscope-java-2",
  requiredCapabilities: ["tool-calling", "streaming"],
  model: { providerId: model.ownerId, profileId: model.versionId },
  prompts: [{ promptId: prompt.ownerId, versionId: prompt.versionId, role: "SYSTEM" }],
  mcpServers: [
    { serverId: mcp.ownerId, versionId: mcp.versionId, allowedTools: ["phase22.verify"] },
  ],
  skills: [{ skillId: skill.ownerId, versionId: skill.versionId }],
  knowledge: [],
  profiles: {
    memoryId: memory.ownerId,
    memoryVersionId: memory.versionId,
    workspaceId: workspace.ownerId,
    workspaceVersionId: workspace.versionId,
    sandboxId: sandbox.ownerId,
    sandboxVersionId: sandbox.versionId,
  },
  permissionPolicy: { policyId: policy.ownerId, versionId: policy.versionId },
  limits: { turnTimeoutSeconds: 120, maxToolCalls: 8, maxSubAgents: 2 },
};
const agent = await api(
  `/api/v1/projects/${session.projectId}/agents`,
  {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      key: `perf-agent-${suffix}`,
      name: `Phase 22 Performance Agent ${suffix}`,
      description: "Phase 22 本机容量基线",
      draft,
    }),
  },
  201,
);
const currentDraft = await api(
  `/api/v1/projects/${session.projectId}/agents/${agent.id}/draft`,
  { method: "GET", headers: headers() },
  200,
);
const revision = await api(
  `/api/v1/projects/${session.projectId}/agents/${agent.id}/publish`,
  {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      idempotencyKey: `phase22-publish-${suffix}`,
      expectedDraftVersion: currentDraft.version,
    }),
  },
  201,
);
const deployment = await api(
  `/api/v1/projects/${session.projectId}/environments/${session.environmentId}/deployments`,
  {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      agentId: agent.id,
      revisionId: revision.id,
      trafficPolicy: "FULL",
      canaryPercent: 0,
    }),
  },
  201,
);

writeFileSync(
  outputPath,
  JSON.stringify({
    organizationId: session.organizationId,
    projectId: session.projectId,
    environmentId: session.environmentId,
    deploymentId: deployment.id,
  }),
  { mode: 0o600 },
);
console.info("Phase 22 performance fixture is ready.");
