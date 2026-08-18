import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Page,
} from "@playwright/test";

if (process.env.AGENTARK_REAL_E2E !== "1") {
  throw new Error("真实四服务 E2E 必须通过 test:e2e:real 入口执行");
}
const require = createRequire(import.meta.url);
const axePath = require.resolve("axe-core/axe.min.js");

/** 真实 E2E 后端写出的临时会话；文件和 Token 均不进入 Git。 */
interface E2eSession {
  userToken: string;
  platformToken: string;
  organizationId: string;
  projectId: string;
  environmentId: string;
  seededJobId: string;
  serviceToken: string;
}

/** 读取权限为 0600 的临时 E2E 会话。 */
function session(): E2eSession {
  return JSON.parse(readFileSync(resolve("test-results/e2e-session.json"), "utf8")) as E2eSession;
}

/** 校验真实 API 状态并解析 JSON，错误时不输出 Token。 */
async function json<T>(response: APIResponse, expectedStatus: number): Promise<T> {
  if (response.status() !== expectedStatus) {
    expect(response.status(), await response.text()).toBe(expectedStatus);
  }
  if (expectedStatus === 204) return undefined as T;
  return (await response.json()) as T;
}

/** 使用当前 E2E Bearer 和精确租户选择调用 Gateway。 */
function headers(value: E2eSession) {
  return {
    Authorization: `Bearer ${value.userToken}`,
    "X-AgentArk-Organization-Id": value.organizationId,
    "X-AgentArk-Project-Id": value.projectId,
    "X-AgentArk-Environment-Id": value.environmentId,
  };
}

/** 创建稳定资产和一个 PUBLISHED 版本。 */
async function createAsset(
  request: APIRequestContext,
  auth: E2eSession,
  kind: string,
  key: string,
  metadata: Record<string, unknown>,
  payload: Record<string, unknown>,
) {
  const asset = await json<{ id: string }>(
    await request.post(`/api/v1/projects/${auth.projectId}/catalog/${kind}`, {
      headers: headers(auth),
      data: { key, name: `E2E ${key}`, description: "真实后端 E2E 资产", metadata },
    }),
    201,
  );
  const version = await json<{ id: string }>(
    await request.post(`/api/v1/projects/${auth.projectId}/catalog/${kind}/${asset.id}/versions`, {
      headers: headers(auth),
      data: { payload, status: "PUBLISHED" },
    }),
    201,
  );
  return { ownerId: asset.id, versionId: version.id };
}

/** 创建一个可由 Knowledge Revision 固定引用的 PUBLISHED Profile。 */
async function createKnowledgeProfile(
  request: APIRequestContext,
  auth: E2eSession,
  kind: "parser" | "chunk" | "embedding" | "retrieval",
  key: string,
  config: Record<string, unknown>,
  credentialSecretRef?: string,
) {
  return json<{ id: string }>(
    await request.post(`/api/v1/projects/${auth.projectId}/knowledge-profiles/${kind}`, {
      headers: headers(auth),
      data: {
        key,
        config,
        credentialSecretRef: credentialSecretRef ?? null,
        status: "PUBLISHED",
      },
    }),
    201,
  );
}

/** 有界轮询最终一致状态；超时不会输出认证信息或响应正文。 */
async function eventually<T>(
  load: () => Promise<T>,
  accepted: (value: T) => boolean,
  timeoutMs = 30_000,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let latest = await load();
  while (!accepted(latest) && Date.now() < deadline) {
    await new Promise((resolvePromise) => globalThis.setTimeout(resolvePromise, 250));
    latest = await load();
  }
  expect(accepted(latest), JSON.stringify(latest)).toBe(true);
  return latest;
}

/** 通过 E2E-only 登录表单建立只驻留页面内存的 Bearer 会话。 */
async function signIn(page: Page, auth: E2eSession) {
  await page.goto("/sign-in");
  await page.getByLabel("临时 E2E Bearer").fill(auth.userToken);
  const organizationResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/v1/organizations") && response.request().method() === "GET",
  );
  await page.getByRole("button", { name: "进入真实 API E2E" }).click();
  expect((await organizationResponse).status()).toBe(200);
  await expect(page.getByText("已选择项目")).toBeVisible({ timeout: 15_000 });
}

test.describe("真实后端核心产品流程", () => {
  test.describe.configure({ mode: "serial" });

  test("Build → Publish → Deploy → Run → Approve → Observe → Promote → Rollback", async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000);
    const auth = session();
    const suffix = Date.now().toString(36);

    const childProject = await json<{ id: string }>(
      await request.post(`/api/v1/organizations/${auth.organizationId}/projects`, {
        headers: { Authorization: `Bearer ${auth.platformToken}` },
        data: { slug: `e2e-child-${suffix}`, name: `E2E Child ${suffix}` },
      }),
      201,
    );
    await json(
      await request.post(`/api/v1/projects/${childProject.id}/environments`, {
        headers: { Authorization: `Bearer ${auth.platformToken}` },
        data: { key: "test", name: "E2E Test Environment" },
      }),
      201,
    );

    const secretKey = `model-${suffix}`;
    const secretMetadata = await json<{ id: string; version: number }>(
      await request.post(`/api/v1/projects/${auth.projectId}/secrets`, {
        headers: headers(auth),
        data: {
          key: secretKey,
          name: "E2E Model Credential",
          provider: "LOCAL_FILE",
          externalPath: `/e2e/${secretKey}`,
          scope: "ENVIRONMENT",
        },
      }),
      201,
    );
    const secretRef = `secret://environment/${auth.environmentId}/primary-model`;
    await json(
      await request.post(
        `/api/v1/projects/${auth.projectId}/environments/${auth.environmentId}/secret-bindings`,
        {
          headers: headers(auth),
          data: { secretMetadataId: secretMetadata.id, bindingKey: "primary-model" },
        },
      ),
      201,
    );
    const prompt = await createAsset(
      request,
      auth,
      "prompt",
      `prompt-${suffix}`,
      {},
      {
        template: "You are an E2E operations agent.",
        variableSchema: {},
        purpose: "SYSTEM",
      },
    );
    const model = await createAsset(
      request,
      auth,
      "model-provider",
      `model-${suffix}`,
      { providerType: "OPENAI_COMPATIBLE", descriptor: { endpoint: "e2e" } },
      {
        modelName: "e2e-model",
        capabilities: ["TOOL", "STREAMING"],
        parameters: { temperature: 0.1, maxTokens: 256 },
        credentialSecretRef: secretRef,
      },
    );
    const mcp = await createAsset(
      request,
      auth,
      "mcp-server",
      `mcp-${suffix}`,
      {},
      {
        transport: "STDIO",
        commandName: "e2e-mcp",
        transportConfig: {},
        ssrfPolicy: { denyPrivateNetworks: true, denyCloudMetadata: true, resolveAndPinDns: true },
        tools: [
          {
            name: "e2e.echo",
            argumentSchema: { type: "object" },
            accessMode: "READ",
            riskLevel: "MEDIUM",
            idempotency: "IDEMPOTENT",
            permissionMetadata: {},
          },
        ],
      },
    );
    const artifact = await json<{ uri: string; checksum: string; size: number; mediaType: string }>(
      await request.post(`/api/v1/projects/${auth.projectId}/skill-artifacts`, {
        headers: headers(auth),
        multipart: {
          file: {
            name: "SKILL.md",
            mimeType: "text/markdown",
            buffer: Buffer.from("# E2E Skill\n"),
          },
        },
      }),
      201,
    );
    const skill = await createAsset(
      request,
      auth,
      "skill",
      `skill-${suffix}`,
      {},
      {
        artifact,
        sourceUri: `urn:agentark:e2e-skill-${suffix}`,
        license: "Apache-2.0",
        compatibility: { runtimeProvider: "agentscope-java-2" },
      },
    );
    const memory = await createAsset(
      request,
      auth,
      "memory-profile",
      `memory-${suffix}`,
      {},
      { strategy: "session" },
    );
    const workspace = await createAsset(
      request,
      auth,
      "workspace-profile",
      `workspace-${suffix}`,
      {},
      { isolation: "session" },
    );
    const sandbox = await createAsset(
      request,
      auth,
      "sandbox-profile",
      `sandbox-${suffix}`,
      {},
      { mode: "restricted" },
    );
    const policy = await createAsset(
      request,
      auth,
      "permission-policy",
      `policy-${suffix}`,
      {},
      {
        defaultDecision: "DENY",
        rules: [{ resource: "tool:e2e.echo", decision: "ASK" }],
        scopes: [],
        approvalPolicy: { mode: "required" },
      },
    );
    const knowledgeBase = await json<{ id: string }>(
      await request.post(`/api/v1/projects/${auth.projectId}/knowledge-bases`, {
        headers: headers(auth),
        data: { key: `knowledge-${suffix}`, name: "E2E Knowledge", description: "真实元数据" },
      }),
      201,
    );
    const dataSource = await json<{ id: string }>(
      await request.post(
        `/api/v1/projects/${auth.projectId}/knowledge-bases/${knowledgeBase.id}/data-sources`,
        {
          headers: headers(auth),
          data: { type: "UPLOAD", name: "E2E Upload", descriptor: { source: "playwright" } },
        },
      ),
      201,
    );
    const documentRevision = await json<{ id: string }>(
      await request.post(
        `/api/v1/projects/${auth.projectId}/knowledge-bases/${knowledgeBase.id}/documents`,
        {
          headers: headers(auth),
          multipart: {
            dataSourceId: dataSource.id,
            title: "AgentArk Release Readiness",
            metadataJson: JSON.stringify({ source: "e2e" }),
            file: {
              name: "release-readiness.md",
              mimeType: "text/markdown",
              buffer: Buffer.from("# AgentArk\n\nRelease readiness knowledge."),
            },
          },
        },
      ),
      201,
    );
    const parserProfile = await createKnowledgeProfile(
      request,
      auth,
      "parser",
      `parser-${suffix}`,
      { format: "text" },
    );
    const chunkProfile = await createKnowledgeProfile(request, auth, "chunk", `chunk-${suffix}`, {
      maxCharacters: 512,
      overlapCharacters: 32,
    });
    const embeddingProfile = await createKnowledgeProfile(
      request,
      auth,
      "embedding",
      `embedding-${suffix}`,
      { provider: "deterministic-e2e", dimensions: 3 },
      secretRef,
    );
    const retrievalProfile = await createKnowledgeProfile(
      request,
      auth,
      "retrieval",
      `retrieval-${suffix}`,
      { topK: 4, scoreThreshold: 0.1, reranker: "none" },
    );
    const knowledgeRevision = await json<{ id: string; status: string }>(
      await request.post(
        `/api/v1/projects/${auth.projectId}/knowledge-bases/${knowledgeBase.id}/revisions`,
        {
          headers: headers(auth),
          data: {
            documentRevisionIds: [documentRevision.id],
            parserProfileId: parserProfile.id,
            chunkProfileId: chunkProfile.id,
            embeddingProfileId: embeddingProfile.id,
            retrievalProfileId: retrievalProfile.id,
          },
        },
      ),
      201,
    );
    expect(knowledgeRevision.status).toBe("CREATED");
    const ingestionRequest = await json<{ id: string }>(
      await request.post(
        `/api/v1/projects/${auth.projectId}/knowledge-revisions/${knowledgeRevision.id}/ingestion-requests`,
        {
          headers: headers(auth),
          data: { idempotencyKey: `ingestion-${suffix}` },
        },
      ),
      202,
    );
    const ingestionPayload = JSON.stringify({
      requestId: ingestionRequest.id,
      knowledgeRevisionId: knowledgeRevision.id,
    });
    const ingestionJob = await json<{ jobId: string; status: string }>(
      await request.post("http://127.0.0.1:8083/internal/v1/scheduler/jobs", {
        headers: { Authorization: `Bearer ${auth.serviceToken}` },
        data: {
          organizationId: auth.organizationId,
          projectId: auth.projectId,
          type: "KNOWLEDGE_INGESTION",
          businessKey: `knowledge-ingestion-${ingestionRequest.id}`,
          payload: ingestionPayload,
          payloadHash: `sha256:${createHash("sha256").update(ingestionPayload).digest("hex")}`,
          priority: 0,
          availableAt: new Date().toISOString(),
          retryPolicy: {
            maxAttempts: 2,
            initialBackoffMillis: 100,
            maxBackoffMillis: 500,
            multiplier: 2,
            jitterRatio: 0,
            timeoutMillis: 30_000,
          },
          idempotencyCapability: "PROVIDER_KEY",
        },
      }),
      202,
    );
    const completedIngestionJob = await eventually(
      async () =>
        json<{ id: string; status: string; currentAttempt: number }>(
          await request.get(
            `/api/v1/scheduler/jobs/${ingestionJob.jobId}?organizationId=${auth.organizationId}&projectId=${auth.projectId}`,
            { headers: headers(auth) },
          ),
          200,
        ),
      (job) => ["SUCCEEDED", "DEAD_LETTERED", "CANCELLED"].includes(job.status),
    );
    expect(completedIngestionJob.status).toBe("SUCCEEDED");
    const readyKnowledge = await eventually(
      async () =>
        json<{ items: Array<{ id: string; status: string }> }>(
          await request.get(
            `/api/v1/projects/${auth.projectId}/knowledge-bases/${knowledgeBase.id}/revisions?limit=100`,
            { headers: headers(auth) },
          ),
          200,
        ),
      (pageResult) =>
        pageResult.items.some(
          (item) => item.id === knowledgeRevision.id && item.status === "READY",
        ),
    );
    expect(readyKnowledge.items.find((item) => item.id === knowledgeRevision.id)?.status).toBe(
      "READY",
    );

    const validDraft = {
      runtimeProvider: "agentscope-java-2",
      requiredCapabilities: ["tool-calling", "streaming"],
      model: { providerId: model.ownerId, profileId: model.versionId },
      prompts: [{ promptId: prompt.ownerId, versionId: prompt.versionId, role: "SYSTEM" }],
      mcpServers: [{ serverId: mcp.ownerId, versionId: mcp.versionId, allowedTools: ["e2e.echo"] }],
      skills: [{ skillId: skill.ownerId, versionId: skill.versionId }],
      knowledge: [{ knowledgeBaseId: knowledgeBase.id, revisionId: knowledgeRevision.id }],
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
    const agent = await json<{ id: string }>(
      await request.post(`/api/v1/projects/${auth.projectId}/agents`, {
        headers: headers(auth),
        data: {
          key: `agent-${suffix}`,
          name: `E2E Agent ${suffix}`,
          description: "Phase 18 真实主流程",
          draft: { ...validDraft, requiredCapabilities: ["vision"] },
        },
      }),
      201,
    );
    const apiValidation = await json<{ valid: boolean }>(
      await request.post(`/api/v1/projects/${auth.projectId}/agents/${agent.id}/draft/validate`, {
        headers: headers(auth),
      }),
      200,
    );
    expect(apiValidation.valid).toBe(false);

    await signIn(page, auth);
    await page.getByRole("link", { name: "构建" }).click();
    const agentRow = page.getByRole("row", { name: new RegExp(`E2E Agent ${suffix}`) });
    await agentRow.getByRole("button", { name: "编辑 Draft" }).click();
    const invalidValidationResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/agents/${agent.id}/draft/validate`) &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "验证" }).click();
    const invalidValidation = await invalidValidationResponse;
    if (invalidValidation.status() !== 200) {
      throw new Error(
        `Browser validation failed with ${invalidValidation.status()}: ${await invalidValidation.text()}`,
      );
    }
    await expect(page.getByLabel("Validation Report")).toContainText('"valid": false');
    await page.locator(".json-editor textarea").fill(JSON.stringify(validDraft, null, 2));
    const draftSaveResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/agents/${agent.id}/draft`) &&
        response.request().method() === "PUT",
    );
    await page.getByRole("button", { name: "保存 Draft" }).click();
    expect((await draftSaveResponse).status()).toBe(200);
    const validValidationResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/agents/${agent.id}/draft/validate`) &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "验证" }).click();
    expect((await validValidationResponse).status()).toBe(200);
    await expect(page.getByLabel("Validation Report")).toContainText('"valid": true');

    await page.getByRole("link", { name: "发布" }).click();
    await page
      .getByRole("row", { name: new RegExp(`E2E Agent ${suffix}`) })
      .getByRole("button", { name: "选择" })
      .click();
    await page.getByRole("button", { name: "运行 Validation" }).click();
    await expect(page.getByText("VALID", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "发布 Revision" }).click();
    const publishResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/agents/${agent.id}/publish`) &&
        response.request().method() === "POST",
    );
    await page.getByRole("dialog").getByRole("button", { name: "确认发布" }).click();
    expect((await publishResponse).status()).toBe(201);
    await page.keyboard.press("Escape");
    await expect(page.getByRole("cell", { name: "#1" })).toBeVisible({ timeout: 15_000 });
    await page.getByRole("row", { name: /#1/ }).getByRole("button", { name: "Inspect" }).click();
    await expect(page.getByLabel("Agent Revision Snapshot")).toContainText("agentscope-java-2");
    await page.getByRole("button", { name: "创建 Deployment" }).click();
    const deploymentResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith(`/environments/${auth.environmentId}/deployments`) &&
        response.request().method() === "POST",
    );
    await page.getByRole("dialog").getByRole("button", { name: "确认创建" }).click();
    expect((await deploymentResponse).status()).toBe(201);
    await page.keyboard.press("Escape");
    await expect(page.getByRole("table", { name: "Environment Deployment" })).toContainText(
      agent.id,
    );

    const deploymentPage = await json<{ items: Array<{ id: string; desiredRevisionId: string }> }>(
      await request.get(
        `/api/v1/projects/${auth.projectId}/environments/${auth.environmentId}/deployments?limit=100`,
        { headers: headers(auth) },
      ),
      200,
    );
    const deployment = deploymentPage.items.at(-1)!;
    const firstRevisionId = deployment.desiredRevisionId;

    await page.getByRole("link", { name: "运行工作区" }).click();
    await page.getByLabel("Deployment UUIDv7").fill(deployment.id);
    await page.getByRole("button", { name: "创建 Session" }).click();
    const sessionId = await page.locator(".session-pin code").first().textContent();
    expect(sessionId).toBeTruthy();
    await page.getByLabel("Turn 输入").fill("执行需要审批的 E2E 工具");
    await page.getByRole("button", { name: "运行 Turn" }).click();
    await expect(page.getByText("PAUSED", { exact: true })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("准备执行受控工具。")).toBeVisible();

    await page.getByRole("link", { name: "审批中心" }).click();
    const approvalRow = page.getByRole("row", { name: /e2e.verify/ });
    await approvalRow.getByRole("button", { name: "Inspect" }).click();
    await expect(page.locator(".approval-risk code")).toContainText("sha256:");
    await expect(page.getByText("verify", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Approve", exact: true }).click();
    await page.getByRole("link", { name: /返回 Run/ }).click();
    await expect(page.getByText("SUCCEEDED", { exact: true })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("审批通过，运行完成。")).toBeVisible();

    await json(
      await request.post("/api/v1/scheduler/triggers", {
        headers: headers(auth),
        data: {
          organizationId: auth.organizationId,
          projectId: auth.projectId,
          key: `scheduled-run-${suffix}`,
          type: "CRON",
          cronExpression: "*/1 * * * * *",
          zoneId: "UTC",
          config: {
            sessionId: sessionId!,
            input: JSON.stringify({ message: "scheduled release-readiness turn" }),
            priority: "0",
          },
          secretRef: null,
          targetContract: "runtime-turn-v1",
          targetJobType: "RUNTIME_TURN",
        },
      }),
      201,
    );
    const scheduledJobs = await eventually(
      async () =>
        json<{
          items: Array<{ type: string; businessKey: string; status: string }>;
        }>(
          await request.get(
            `/api/v1/scheduler/jobs?organizationId=${auth.organizationId}&projectId=${auth.projectId}&limit=100`,
            { headers: headers(auth) },
          ),
          200,
        ),
      (pageResult) =>
        pageResult.items.some(
          (item) =>
            item.type === "RUNTIME_TURN" &&
            item.businessKey.startsWith("cron:") &&
            item.status === "SUCCEEDED",
        ),
    );
    expect(
      scheduledJobs.items.some(
        (item) => item.type === "RUNTIME_TURN" && item.status === "SUCCEEDED",
      ),
    ).toBe(true);
    await page.addScriptTag({ path: axePath });
    const seriousViolations = await page.evaluate(async () => {
      const axe = (
        window as unknown as Window & {
          axe: {
            run(): Promise<{ violations: Array<{ id: string; impact: string | null }> }>;
          };
        }
      ).axe;
      const result = await axe.run();
      return result.violations.filter(
        (violation) => violation.impact === "serious" || violation.impact === "critical",
      );
    });
    expect(seriousViolations).toEqual([]);
    mkdirSync(resolve("output/playwright"), { recursive: true });
    const topbar = page.locator(".topbar");
    await topbar.evaluate((element) => {
      (element as HTMLElement).style.visibility = "hidden";
    });
    try {
      await page
        .locator(".main-content")
        .screenshot({ path: resolve("output/playwright/phase-18-run.png") });
    } finally {
      await topbar.evaluate((element) => {
        (element as HTMLElement).style.visibility = "";
      });
    }

    const currentDraft = await json<{ version: number; spec: typeof validDraft }>(
      await request.get(`/api/v1/projects/${auth.projectId}/agents/${agent.id}/draft`, {
        headers: headers(auth),
      }),
      200,
    );
    await json(
      await request.put(`/api/v1/projects/${auth.projectId}/agents/${agent.id}/draft`, {
        headers: headers(auth),
        data: {
          expectedVersion: currentDraft.version,
          draft: { ...validDraft, limits: { ...validDraft.limits, maxToolCalls: 9 } },
        },
      }),
      200,
    );
    const secondRevision = await json<{ id: string }>(
      await request.post(`/api/v1/projects/${auth.projectId}/agents/${agent.id}/publish`, {
        headers: headers(auth),
        data: {
          idempotencyKey: `e2e-publish-${suffix}`,
          expectedDraftVersion: currentDraft.version + 1,
        },
      }),
      201,
    );
    const beforePromote = await json<{ items: Array<{ id: string; version: number }> }>(
      await request.get(
        `/api/v1/projects/${auth.projectId}/environments/${auth.environmentId}/deployments?limit=100`,
        { headers: headers(auth) },
      ),
      200,
    );
    const currentDeployment = beforePromote.items.find((item) => item.id === deployment.id)!;
    await json(
      await request.post(
        `/api/v1/projects/${auth.projectId}/environments/${auth.environmentId}/deployments/${deployment.id}/promote`,
        {
          headers: headers(auth),
          data: { revisionId: secondRevision.id, expectedVersion: currentDeployment.version },
        },
      ),
      200,
    );
    const secondSession = await json<{ revisionId: string; snapshotId: string }>(
      await request.post("/api/v1/runtime/sessions", {
        headers: { ...headers(auth), "Idempotency-Key": `e2e-session-v2-${suffix}` },
        data: {
          organizationId: auth.organizationId,
          projectId: auth.projectId,
          deploymentId: deployment.id,
          participantMetadata: { source: "phase-23" },
          channelMetadata: {},
        },
      }),
      201,
    );
    expect(secondSession.revisionId).toBe(secondRevision.id);
    expect(secondSession.snapshotId).toBeTruthy();
    const oldSession = await json<{ revisionId: string }>(
      await request.get(`/api/v1/runtime/sessions/${sessionId}`, { headers: headers(auth) }),
      200,
    );
    expect(oldSession.revisionId).toBe(firstRevisionId);
    await json(
      await request.post(
        `/api/v1/projects/${auth.projectId}/environments/${auth.environmentId}/deployments/${deployment.id}/rollback`,
        {
          headers: headers(auth),
          data: { revisionId: firstRevisionId, expectedVersion: currentDeployment.version + 1 },
        },
      ),
      200,
    );

    const serviceAccount = await json<{ id: string }>(
      await request.post(`/api/v1/projects/${auth.projectId}/service-accounts`, {
        headers: headers(auth),
        data: { name: `e2e-service-${suffix}` },
      }),
      201,
    );
    const roles = await json<Array<{ id: string; key: string }>>(
      await request.get(`/api/v1/projects/${auth.projectId}/roles`, {
        headers: headers(auth),
      }),
      200,
    );
    const viewerRole = roles.find((role) => role.key === "project-viewer");
    if (!viewerRole) throw new Error("project-viewer role is missing");
    await json(
      await request.post(`/api/v1/projects/${auth.projectId}/role-bindings`, {
        headers: headers(auth),
        data: {
          roleId: viewerRole.id,
          principalKind: "SERVICE_ACCOUNT",
          principalId: serviceAccount.id,
          scopeType: "PROJECT",
          scopeId: auth.projectId,
        },
      }),
      201,
    );
    const apiKey = await json<{ apiKey: { id: string; version: number }; plaintext: string }>(
      await request.post(`http://127.0.0.1:8081/api/v1/projects/${auth.projectId}/api-keys`, {
        headers: headers(auth),
        data: { serviceAccountId: serviceAccount.id, name: "E2E Key", scopes: ["agent:read"] },
      }),
      201,
    );
    if (!/^ark_[A-Za-z0-9_-]{12}_[A-Za-z0-9_-]{43}$/.test(apiKey.plaintext)) {
      throw new Error("created API key format is invalid");
    }
    const directApiKeyRead = await request.get(
      `http://127.0.0.1:8081/api/v1/projects/${auth.projectId}/agents?limit=1`,
      {
        headers: {
          ...headers(auth),
          Authorization: `ApiKey ${apiKey.plaintext}`,
        },
      },
    );
    expect(directApiKeyRead.status(), await directApiKeyRead.text()).toBe(200);
    const apiKeyRead = await request.get(`/api/v1/projects/${auth.projectId}/agents?limit=1`, {
      headers: {
        ...headers(auth),
        Authorization: `ApiKey ${apiKey.plaintext}`,
      },
    });
    expect(apiKeyRead.status(), await apiKeyRead.text()).toBe(200);
    await json(
      await request.post(`/api/v1/projects/${auth.projectId}/api-keys/${apiKey.apiKey.id}/revoke`, {
        headers: headers(auth),
        data: { expectedVersion: apiKey.apiKey.version },
      }),
      204,
    );
    const apiKeys = await json<Array<{ id: string; revokedAt: string | null }>>(
      await request.get(`/api/v1/projects/${auth.projectId}/api-keys`, {
        headers: headers(auth),
      }),
      200,
    );
    expect(apiKeys.find((item) => item.id === apiKey.apiKey.id)?.revokedAt).not.toBeNull();
    const revokedApiKeyRead = await request.get(
      `/api/v1/projects/${auth.projectId}/agents?limit=1`,
      {
        headers: {
          ...headers(auth),
          Authorization: `ApiKey ${apiKey.plaintext}`,
        },
      },
    );
    expect(revokedApiKeyRead.status()).toBe(401);

    const revokedSecret = await json<{ status: string; version: number }>(
      await request.post(`/api/v1/projects/${auth.projectId}/secrets/${secretMetadata.id}:revoke`, {
        headers: headers(auth),
        data: { expectedVersion: secretMetadata.version },
      }),
      200,
    );
    expect(revokedSecret.status).toBe("REVOKED");
    const validationAfterSecretRevoke = await json<{ valid: boolean }>(
      await request.post(`/api/v1/projects/${auth.projectId}/agents/${agent.id}/draft/validate`, {
        headers: headers(auth),
      }),
      200,
    );
    expect(validationAfterSecretRevoke.valid).toBe(false);
    const blockedPublish = await request.post(
      `/api/v1/projects/${auth.projectId}/agents/${agent.id}/publish`,
      {
        headers: headers(auth),
        data: {
          idempotencyKey: `e2e-publish-after-secret-revoke-${suffix}`,
          expectedDraftVersion: currentDraft.version + 1,
        },
      },
    );
    expect([404, 409]).toContain(blockedPublish.status());

    const crossTenantJob = await request.get(
      `/api/v1/scheduler/jobs/${auth.seededJobId}?organizationId=${auth.organizationId}&projectId=${childProject.id}`,
      { headers: headers(auth) },
    );
    expect([403, 404]).toContain(crossTenantJob.status());
    await page.getByRole("link", { name: "运行治理" }).click();
    await page.getByRole("tab", { name: "Dead Letter" }).click();
    await page.getByRole("button", { name: /Redrive/ }).click();
    await expect(page.getByText("暂无 OPEN Dead Letter")).toBeVisible();
    await page.screenshot({
      path: resolve("output/playwright/phase-18-operate.png"),
      fullPage: true,
    });
    await page.getByRole("link", { name: "治理与观测" }).click();
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: "Trace、Audit、Usage、Quota 与 Evaluation",
      }),
    ).toBeVisible();
    await expect(page.getByRole("table", { name: "安全审计事件" })).toContainText("agent.publish");
    await page.getByRole("tab", { name: "Usage / Cost", exact: true }).click();
    await expect(page.getByRole("table", { name: "Usage Cost 聚合" })).toBeVisible();
    await page.getByRole("tab", { name: "Quota", exact: true }).click();
    await page.getByLabel("Scope Ref").fill(auth.projectId);
    await page.getByLabel("Limit").fill("2");
    const quotaResponse = page.waitForResponse(
      (response) =>
        response.url().endsWith("/governance/quota-policies") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "创建 Policy" }).click();
    expect((await quotaResponse).status()).toBe(201);
    await expect(page.getByRole("table", { name: "Quota Policy" })).toContainText("CONCURRENT_RUN");
    await page.screenshot({
      path: resolve("output/playwright/phase-19-governance.png"),
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole("navigation", { name: "主导航" })).toBeVisible();
    const overflow = await page.evaluate(() => {
      const viewportWidth = document.documentElement.clientWidth;
      const elements = Array.from(document.querySelectorAll("body *"))
        .filter((element) => !element.closest(".sidebar__nav, .tabs-list, .table-scroll"))
        .map((element) => {
          const rectangle = element.getBoundingClientRect();
          return {
            element: element.tagName.toLowerCase(),
            className: element.className.toString(),
            left: Math.round(rectangle.left),
            right: Math.round(rectangle.right),
            width: Math.round(rectangle.width),
          };
        })
        .filter((item) => item.width > 0 && (item.left < -1 || item.right > viewportWidth + 1))
        .slice(0, 20);
      return {
        viewportWidth,
        documentWidth: document.documentElement.scrollWidth,
        elements,
      };
    });
    expect(overflow.elements, JSON.stringify(overflow, null, 2)).toEqual([]);
    expect(overflow.documentWidth).toBe(overflow.viewportWidth);
  });
});
