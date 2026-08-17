import { describe, expect, it, vi } from "vitest";

import { createMemoryCredentialProvider } from "../http";
import { RuntimeEventStreamClient } from "./runtime-event-stream";

/** 创建可按序号变化的 Runtime Event v1 测试事实。 */
function runtimeEvent(sequence: number, eventType: string) {
  return {
    schemaVersion: 1,
    eventId: `01890f47-0000-7000-8000-${String(sequence).padStart(12, "0")}`,
    sessionSequence: sequence,
    sequence,
    eventType,
    occurredAt: "2026-08-17T02:00:00.000Z",
    organizationId: "01890f47-0000-7000-8000-000000000101",
    projectId: "01890f47-0000-7000-8000-000000000102",
    sessionId: "01890f47-0000-7000-8000-000000000103",
    turnId: "01890f47-0000-7000-8000-000000000104",
    runId: "01890f47-0000-7000-8000-000000000105",
    traceId: "0123456789abcdef0123456789abcdef",
    fencingToken: 3,
    payload: { sequence },
  };
}

/** 将若干 SSE 消息编码为一次 Fetch Response。 */
function sseResponse(messages: Array<{ id?: string; data: unknown }>): Response {
  const encoder = new TextEncoder();
  const text = messages
    .map(
      (message) =>
        `${message.id ? `id: ${message.id}\n` : ""}data: ${JSON.stringify(message.data)}\n\n`,
    )
    .join("");
  return new Response(
    new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(text));
        controller.close();
      },
    }),
    { status: 200, headers: { "Content-Type": "text/event-stream" } },
  );
}

/** 等待 SSE Client 满足异步断言。 */
async function waitFor(assertion: () => void): Promise<void> {
  await vi.waitFor(assertion, { timeout: 2000, interval: 10 });
}

describe("Runtime SSE Client", () => {
  it("断线后携带 Last-Event-ID 重连并去重，收到终态后停止", async () => {
    const first = runtimeEvent(1, "run.started");
    const terminal = runtimeEvent(2, "run.succeeded");
    const requestHeaders: Headers[] = [];
    const fetcher = vi
      .fn<typeof fetch>()
      .mockImplementationOnce((_input, init) => {
        requestHeaders.push(new Headers(init?.headers));
        return Promise.resolve(sseResponse([{ id: first.eventId, data: first }]));
      })
      .mockImplementationOnce((_input, init) => {
        requestHeaders.push(new Headers(init?.headers));
        return Promise.resolve(
          sseResponse([
            { id: first.eventId, data: first },
            { id: terminal.eventId, data: terminal },
          ]),
        );
      });
    const client = new RuntimeEventStreamClient({
      runId: first.runId,
      credentialProvider: createMemoryCredentialProvider(),
      fetcher,
      retryBaseMs: 0,
      retryMaxMs: 0,
    });

    client.start();
    await waitFor(() => expect(client.status).toBe("closed"));
    expect(client.events.map((item) => item.sequence)).toEqual([1, 2]);
    expect(requestHeaders[1]?.get("Last-Event-ID")).toBe(first.eventId);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("忽略坏消息并将本地事件缓冲限制在配置容量", async () => {
    const second = runtimeEvent(2, "model.delta");
    const terminal = runtimeEvent(3, "run.failed");
    const invalid = vi.fn();
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        sseResponse([
          { data: "not-an-event" },
          { data: runtimeEvent(1, "run.started") },
          { data: second },
          { data: terminal },
        ]),
      );
    const client = new RuntimeEventStreamClient({
      runId: terminal.runId,
      credentialProvider: createMemoryCredentialProvider(),
      fetcher,
      capacity: 2,
      onInvalidEvent: invalid,
    });

    client.start();
    await waitFor(() => expect(client.status).toBe("closed"));
    expect(invalid).toHaveBeenCalledOnce();
    expect(client.events.map((item) => item.sequence)).toEqual([2, 3]);
  });
});
