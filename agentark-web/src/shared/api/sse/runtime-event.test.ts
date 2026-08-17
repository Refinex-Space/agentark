import { describe, expect, it } from "vitest";

import { isTerminalRuntimeEvent, runtimeEventSchema } from "./runtime-event";

/** 创建满足 Runtime Event v1 的测试事实。 */
function event(eventType = "run.started") {
  return {
    schemaVersion: 1,
    eventId: "01890f47-0000-7000-8000-000000000001",
    sessionSequence: 1,
    sequence: 1,
    eventType,
    occurredAt: "2026-08-17T02:00:00.000Z",
    organizationId: "01890f47-0000-7000-8000-000000000002",
    projectId: "01890f47-0000-7000-8000-000000000003",
    sessionId: "01890f47-0000-7000-8000-000000000004",
    turnId: "01890f47-0000-7000-8000-000000000005",
    runId: "01890f47-0000-7000-8000-000000000006",
    traceId: "0123456789abcdef0123456789abcdef",
    fencingToken: 2,
    payload: {},
  };
}

describe("Runtime Event v1", () => {
  it("校验稳定信封并识别明确终态", () => {
    const running = runtimeEventSchema.parse(event());
    const terminal = runtimeEventSchema.parse(event("run.succeeded"));
    expect(isTerminalRuntimeEvent(running)).toBe(false);
    expect(isTerminalRuntimeEvent(terminal)).toBe(true);
  });

  it("拒绝未知 Schema 版本以及同时存在的内联和对象负载", () => {
    expect(runtimeEventSchema.safeParse({ ...event(), schemaVersion: 2 }).success).toBe(false);
    expect(
      runtimeEventSchema.safeParse({
        ...event(),
        payloadRef: {
          uri: "https://objects.example.test/payload",
          checksum: `sha256:${"a".repeat(64)}`,
          size: 10,
          mediaType: "application/json",
        },
      }).success,
    ).toBe(false);
  });
});
