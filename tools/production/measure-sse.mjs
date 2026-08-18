import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const sessionPath = resolve(process.argv[2]);
const seedPath = resolve(process.argv[3]);
const outputPath = resolve(process.argv[4]);
const session = JSON.parse(readFileSync(sessionPath, "utf8"));
const seed = JSON.parse(readFileSync(seedPath, "utf8"));
const baseUrl = "http://127.0.0.1:8080";

/** 构造真实 JWT 与租户选择请求头。 */
function headers(extra = {}) {
  return {
    Authorization: `Bearer ${session.userToken}`,
    "X-AgentArk-Organization-Id": seed.organizationId,
    "X-AgentArk-Project-Id": seed.projectId,
    "X-AgentArk-Environment-Id": seed.environmentId,
    ...extra,
  };
}

/** 调用 JSON API 并要求精确成功状态。 */
async function json(path, options, status) {
  const response = await fetch(`${baseUrl}${path}`, options);
  if (response.status !== status) {
    throw new Error(`${options.method} ${path} returned ${response.status}`);
  }
  return response.json();
}

const suffix = `${Date.now()}-${process.pid}`;
const runtimeSession = await json(
  "/api/v1/runtime/sessions",
  {
    method: "POST",
    headers: headers({
      "Content-Type": "application/json",
      "Idempotency-Key": `phase22-sse-session-${suffix}`,
    }),
    body: JSON.stringify({
      organizationId: seed.organizationId,
      projectId: seed.projectId,
      deploymentId: seed.deploymentId,
    }),
  },
  201,
);
const started = performance.now();
const turn = await json(
  `/api/v1/runtime/sessions/${runtimeSession.sessionId}/turns`,
  {
    method: "POST",
    headers: headers({
      "Content-Type": "application/json",
      "Idempotency-Key": `phase22-sse-turn-${suffix}`,
    }),
    body: JSON.stringify({
      organizationId: seed.organizationId,
      projectId: seed.projectId,
      input: { text: "Phase 22 SSE first-event probe" },
      priority: 0,
    }),
  },
  202,
);

const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 5000);
const response = await fetch(`${baseUrl}/api/v1/runtime/runs/${turn.runId}/events:stream`, {
  headers: headers({ Accept: "text/event-stream", "Last-Event-ID": "0" }),
  signal: controller.signal,
});
if (response.status !== 200 || !response.body) {
  throw new Error(`SSE stream returned ${response.status}`);
}
const reader = response.body.getReader();
let text = "";
while (!text.includes("\n\n")) {
  const chunk = await reader.read();
  if (chunk.done) break;
  text += new TextDecoder().decode(chunk.value, { stream: true });
}
const firstEventMillis = performance.now() - started;
if (!text.includes("id:") || !text.includes("data:")) {
  throw new Error("SSE first event is missing id or data");
}
await reader.cancel();
clearTimeout(timeout);

/** 打开一个可回放的真实 SSE 连接并读取首个完整事件。 */
async function openReplayStream() {
  const streamResponse = await fetch(
    `${baseUrl}/api/v1/runtime/runs/${turn.runId}/events:stream`,
    { headers: headers({ Accept: "text/event-stream", "Last-Event-ID": "0" }) },
  );
  if (streamResponse.status !== 200 || !streamResponse.body) {
    throw new Error(`concurrent SSE stream returned ${streamResponse.status}`);
  }
  const streamReader = streamResponse.body.getReader();
  let event = "";
  while (!event.includes("\n\n")) {
    const chunk = await streamReader.read();
    if (chunk.done) break;
    event += new TextDecoder().decode(chunk.value, { stream: true });
  }
  if (!event.includes("id:") || !event.includes("data:")) {
    throw new Error("concurrent SSE replay is missing id or data");
  }
  return streamReader;
}

const concurrentConnections = 20;
const holdMillis = 3000;
const replayReaders = await Promise.all(
  Array.from({ length: concurrentConnections }, () => openReplayStream()),
);
await new Promise((resolvePromise) => setTimeout(resolvePromise, holdMillis));
await Promise.all(replayReaders.map((streamReader) => streamReader.cancel()));
writeFileSync(
  outputPath,
  JSON.stringify({
    firstEventMillis: Number(firstEventMillis.toFixed(2)),
    lastEventId: "0",
    concurrentConnections,
    holdMillis,
  }),
  { mode: 0o600 },
);
console.info(
  `Phase 22 SSE first event: ${firstEventMillis.toFixed(2)} ms; ` +
    `${concurrentConnections} replay streams held for ${holdMillis} ms`,
);
