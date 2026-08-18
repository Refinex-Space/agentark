import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const session = JSON.parse(open(__ENV.SESSION_FILE));
const seed = JSON.parse(open(__ENV.SEED_FILE));
const baseUrl = __ENV.AGENTARK_BASE_URL || "http://127.0.0.1:8080";

const controlReadDuration = new Trend("control_read_duration", true);
const controlWriteDuration = new Trend("control_write_duration", true);
const runtimeTurnAcceptDuration = new Trend("runtime_turn_accept_duration", true);
const eventVisibilityDuration = new Trend("event_visibility_duration", true);
const schedulerDueStartDuration = new Trend("scheduler_due_start_duration", true);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    control_read: {
      executor: "constant-vus",
      exec: "controlRead",
      vus: 5,
      duration: "20s",
    },
    control_write: {
      executor: "shared-iterations",
      exec: "controlWrite",
      vus: 1,
      iterations: 60,
      maxDuration: "60s",
    },
    runtime_accept: {
      executor: "shared-iterations",
      exec: "runtimeAccept",
      vus: 3,
      iterations: 30,
      maxDuration: "60s",
    },
    scheduler_due: {
      executor: "shared-iterations",
      exec: "schedulerDue",
      vus: 1,
      iterations: 10,
      maxDuration: "45s",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    control_read_duration: ["p(95)<300"],
    control_write_duration: ["p(95)<800"],
    runtime_turn_accept_duration: ["p(95)<500"],
    event_visibility_duration: ["p(95)<1000"],
    scheduler_due_start_duration: ["p(95)<5000"],
  },
};

/** 生成真实用户 JWT 和精确租户选择请求头。 */
function tenantHeaders(contentType = false) {
  const result = {
    Authorization: `Bearer ${session.userToken}`,
    "X-AgentArk-Organization-Id": seed.organizationId,
    "X-AgentArk-Project-Id": seed.projectId,
    "X-AgentArk-Environment-Id": seed.environmentId,
  };
  if (contentType) result["Content-Type"] = "application/json";
  return result;
}

/** 构造跨 VU、迭代和毫秒唯一的可审计 Key。 */
function unique(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}`;
}

/** 测量经 Gateway 的 Control 常规列表读取。 */
export function controlRead() {
  const response = http.get(`${baseUrl}/api/v1/organizations`, {
    headers: tenantHeaders(),
    tags: { operation: "control-read" },
  });
  controlReadDuration.add(response.timings.duration);
  check(response, { "Control read returns 200": (value) => value.status === 200 });
}

/** 测量真实事务和约束参与的 Control Project 创建。 */
export function controlWrite() {
  const key = unique("perf-project");
  const response = http.post(
    `${baseUrl}/api/v1/organizations/${seed.organizationId}/projects`,
    JSON.stringify({ slug: key, name: `Phase22 ${key}` }),
    {
      headers: {
        Authorization: `Bearer ${session.platformToken}`,
        "Content-Type": "application/json",
      },
      tags: { operation: "control-write" },
    },
  );
  controlWriteDuration.add(response.timings.duration);
  check(response, { "Control write returns 201": (value) => value.status === 201 });
}

/** 测量 Turn 接单事务及持久 Event 首次可见延迟。 */
export function runtimeAccept() {
  const sessionResponse = http.post(
    `${baseUrl}/api/v1/runtime/sessions`,
    JSON.stringify({
      organizationId: seed.organizationId,
      projectId: seed.projectId,
      deploymentId: seed.deploymentId,
      participantMetadata: { source: "phase22-k6" },
      channelMetadata: { channel: "performance" },
    }),
    {
      headers: {
        ...tenantHeaders(true),
        "Idempotency-Key": unique("perf-session"),
      },
      tags: { operation: "runtime-session" },
    },
  );
  if (!check(sessionResponse, { "Runtime session returns 201": (value) => value.status === 201 })) {
    return;
  }
  const runtimeSession = sessionResponse.json();
  const started = Date.now();
  const turnResponse = http.post(
    `${baseUrl}/api/v1/runtime/sessions/${runtimeSession.sessionId}/turns`,
    JSON.stringify({
      organizationId: seed.organizationId,
      projectId: seed.projectId,
      input: { text: "Phase 22 deterministic turn" },
      priority: 0,
    }),
    {
      headers: {
        ...tenantHeaders(true),
        "Idempotency-Key": unique("perf-turn"),
      },
      tags: { operation: "runtime-turn-accept" },
    },
  );
  runtimeTurnAcceptDuration.add(turnResponse.timings.duration);
  if (!check(turnResponse, { "Runtime turn returns 202": (value) => value.status === 202 })) {
    return;
  }
  const runId = turnResponse.json().runId;
  let visible = false;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const events = http.get(`${baseUrl}/api/v1/runtime/runs/${runId}/events?after=0&limit=10`, {
      headers: tenantHeaders(),
      tags: { operation: "runtime-event-visible" },
    });
    if (events.status === 200 && Array.isArray(events.json()) && events.json().length > 0) {
      visible = true;
      break;
    }
    sleep(0.05);
  }
  eventVisibilityDuration.add(Date.now() - started);
  check(visible, { "Persisted runtime event becomes visible": (value) => value === true });
}

/** 测量 Cron 到期后 Durable Job 对管理 API 可见的端到端延迟。 */
export function schedulerDue() {
  const key = unique("perf-trigger");
  const started = Date.now();
  const triggerResponse = http.post(
    `${baseUrl}/api/v1/scheduler/triggers`,
    JSON.stringify({
      organizationId: seed.organizationId,
      projectId: seed.projectId,
      key,
      type: "CRON",
      cronExpression: "* * * * * *",
      zoneId: "UTC",
      config: { channel: "performance" },
      secretRef: null,
      targetContract: "agentark.scheduler.channel-message.v1",
      targetJobType: "CHANNEL_MESSAGE",
    }),
    { headers: tenantHeaders(true), tags: { operation: "scheduler-trigger-create" } },
  );
  if (!check(triggerResponse, { "Scheduler trigger returns 201": (value) => value.status === 201 })) {
    console.error(
      `Scheduler trigger failed with ${triggerResponse.status}: ${triggerResponse.body.slice(0, 500)}`,
    );
    return;
  }
  const triggerId = triggerResponse.json().id;
  let visible = false;
  for (let attempt = 0; attempt < 70; attempt += 1) {
    const jobs = http.get(
      `${baseUrl}/api/v1/scheduler/jobs?organizationId=${seed.organizationId}&projectId=${seed.projectId}&limit=100`,
      { headers: tenantHeaders(), tags: { operation: "scheduler-job-visible" } },
    );
    if (
      jobs.status === 200 &&
      jobs.json().items.some((job) => job.businessKey.includes(`cron:${triggerId}:`))
    ) {
      visible = true;
      break;
    }
    sleep(0.1);
  }
  schedulerDueStartDuration.add(Date.now() - started);
  check(visible, { "Due scheduler job becomes visible": (value) => value === true });
}
