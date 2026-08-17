import { useMutation, useQuery } from "@tanstack/react-query";
import { Download, Pause, PlugZap, RotateCcw, Square } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  cancelRuntimeRun,
  createRuntimeSession,
  createRuntimeTurn,
  downloadRuntimeEventPayload,
  getRuntimeRun,
  getRuntimeSession,
  listRuntimeRunEvents,
} from "@/shared/api/generated/runtime/client";
import type { Run, Session, Turn } from "@/shared/api/generated/runtime/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";
import {
  RuntimeEventStreamClient,
  type RuntimeEventConnectionStatus,
} from "@/shared/api/sse/runtime-event-stream";
import type { RuntimeEvent } from "@/shared/api/sse/runtime-event";
import { createIdempotencyKey } from "@/shared/lib/idempotency";
import {
  Button,
  EmptyState,
  Inspector,
  JsonViewer,
  ProblemState,
  SplitPane,
  StatusBadge,
  Timeline,
} from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** 未加载持久 Event 时复用稳定空数组，避免 Hook 依赖漂移。 */
const EMPTY_EVENTS: RuntimeEvent[] = [];

/** 从 Runtime Event Payload 安全读取字符串字段。 */
function payloadText(event: RuntimeEvent, key: string): string | undefined {
  const value = event.payload?.[key];
  return typeof value === "string" ? value : undefined;
}

/** 将连接状态映射为用户可见状态。 */
function connectionTone(status: RuntimeEventConnectionStatus) {
  if (status === "open") return "success" as const;
  if (status === "error" || status === "closed") return "danger" as const;
  return "warning" as const;
}

/** Runtime 真实 Session、Turn、Run、SSE、Timeline 与 Artifact 工作区。 */
export default function RuntimeWorkspacePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { selection } = useTenant();
  const { credentialProvider } = useAuthSession();
  const request = useApiRequest();
  const [session, setSession] = useState<Session>();
  const [runId, setRunId] = useState<string | undefined>(searchParams.get("run") ?? undefined);
  const [events, setEvents] = useState<RuntimeEvent[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<string>();
  const [connection, setConnection] = useState<RuntimeEventConnectionStatus>("idle");
  const streamRef = useRef<RuntimeEventStreamClient | null>(null);
  const projectReady = Boolean(selection.organizationId && selection.projectId);
  const sessionId = searchParams.get("session") ?? undefined;

  const recoveredSession = useQuery<Session>({
    queryKey: ["runtime", "session", sessionId],
    enabled: Boolean(sessionId),
    queryFn: async () => unwrapGenerated(await getRuntimeSession(sessionId!, request), [200]),
  });
  const activeSession = session ?? recoveredSession.data;

  const run = useQuery<Run>({
    queryKey: ["runtime", "run", runId],
    enabled: Boolean(runId),
    refetchInterval: ({ state }) => {
      const status = state.data?.status;
      return status && ["SUCCEEDED", "FAILED", "CANCELLED", "ABANDONED"].includes(status)
        ? false
        : 1500;
    },
    queryFn: async () => unwrapGenerated(await getRuntimeRun(runId!, request), [200]),
  });

  useEffect(() => {
    if (run.data && !sessionId) {
      setSearchParams({ session: run.data.sessionId, run: run.data.runId }, { replace: true });
    }
  }, [run.data, sessionId, setSearchParams]);
  const persistedEvents = useQuery<RuntimeEvent[]>({
    queryKey: ["runtime", "events", runId],
    enabled: Boolean(runId),
    queryFn: async () =>
      unwrapGenerated(await listRuntimeRunEvents(runId!, { after: 0, limit: 500 }, request), [200]),
  });

  const displayedEvents = events.length > 0 ? events : (persistedEvents.data ?? EMPTY_EVENTS);

  useEffect(() => {
    if (!runId || !persistedEvents.isSuccess) return;
    const initial = persistedEvents.data ?? [];
    const lastSequence = initial.at(-1)?.sessionSequence;
    const client = new RuntimeEventStreamClient({
      runId,
      credentialProvider,
      ...(lastSequence ? { lastEventId: String(lastSequence) } : {}),
      capacity: 500,
      visibility: document,
      onStatus: setConnection,
      onEvent: () => {
        const merged = new Map(initial.map((event) => [event.eventId, event]));
        client.events.forEach((event) => merged.set(event.eventId, event));
        setEvents(
          [...merged.values()].sort((left, right) => left.sessionSequence - right.sessionSequence),
        );
      },
    });
    streamRef.current = client;
    client.start();
    return () => {
      client.stop();
      streamRef.current = null;
    };
  }, [credentialProvider, persistedEvents.data, persistedEvents.isSuccess, runId]);

  const createSessionMutation = useMutation({
    mutationFn: async (deploymentId: string) =>
      unwrapGenerated<Session>(
        await createRuntimeSession(
          {
            organizationId: selection.organizationId!,
            projectId: selection.projectId!,
            deploymentId,
            participantMetadata: { source: "agentark-web" },
          },
          { "Idempotency-Key": createIdempotencyKey() },
          request,
        ),
        [201],
      ),
    onSuccess: (created) => {
      setSession(created);
      setRunId(undefined);
      setEvents([]);
      setSearchParams({ session: created.sessionId });
    },
  });
  const createTurnMutation = useMutation({
    mutationFn: async (input: string) =>
      unwrapGenerated<Turn>(
        await createRuntimeTurn(
          activeSession!.sessionId,
          {
            organizationId: selection.organizationId!,
            projectId: selection.projectId!,
            input: { text: input },
            priority: 0,
          },
          { "Idempotency-Key": createIdempotencyKey() },
          request,
        ),
        [202],
      ),
    onSuccess: (created) => {
      setRunId(created.runId);
      setEvents([]);
      setSearchParams({ session: activeSession!.sessionId, run: created.runId });
    },
  });

  const selectedEvent =
    displayedEvents.find((event) => event.eventId === selectedEventId) ?? displayedEvents.at(-1);
  const textOutput = useMemo(
    () =>
      displayedEvents
        .filter((event) => event.eventType.includes("text") || event.eventType.includes("message"))
        .map((event) => payloadText(event, "text") ?? payloadText(event, "delta") ?? "")
        .join(""),
    [displayedEvents],
  );
  const callEvents = useMemo(
    () =>
      displayedEvents.filter((event) =>
        ["model", "tool", "mcp", "rag", "subagent", "approval"].some((kind) =>
          event.eventType.includes(kind),
        ),
      ),
    [displayedEvents],
  );
  const timelineItems = displayedEvents.map((event) => ({
    id: event.eventId,
    title: event.eventType,
    detail: payloadText(event, "summary") ?? `Trace ${event.traceId}`,
    status: event.eventType.includes("failed")
      ? ("failure" as const)
      : event.eventType.includes("requested")
        ? ("running" as const)
        : ("success" as const),
    time: `seq ${event.sequence}`,
  }));

  /** 下载经后端重新授权的 Event Artifact。 */
  const download = async (event: RuntimeEvent): Promise<void> => {
    const response = await downloadRuntimeEventPayload(runId!, event.eventId, request);
    const blob = unwrapGenerated<Blob>(response, [200]);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = event.eventId;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="page-stack page-stack--flush">
      <PageHeader
        eyebrow="RUN"
        title="Session、Turn 与可恢复事件流"
        description="Session 创建时固定 Revision/Snapshot；SSE 断开只影响消费，不取消 Run。"
        actions={
          <div className="inline-actions">
            <StatusBadge tone={connectionTone(connection)}>{connection.toUpperCase()}</StatusBadge>
            {run.data ? (
              <StatusBadge tone={run.data.status === "SUCCEEDED" ? "success" : "info"}>
                {run.data.status}
              </StatusBadge>
            ) : null}
          </div>
        }
      />
      {!projectReady ? (
        <EmptyState
          title="先选择 Organization 与 Project"
          description="Runtime 会验证 Token 中的精确租户选择。"
        />
      ) : (
        <>
          <section className="runtime-command-bar">
            <form
              onSubmit={(event: FormEvent<HTMLFormElement>) => {
                event.preventDefault();
                const value = new FormData(event.currentTarget).get("deploymentId");
                const deploymentId = typeof value === "string" ? value : "";
                void createSessionMutation.mutateAsync(deploymentId);
              }}
            >
              <label>
                <span>Deployment UUIDv7</span>
                <input name="deploymentId" required placeholder="已启用 Deployment" />
              </label>
              <Button type="submit" disabled={createSessionMutation.isPending}>
                <PlugZap size={15} />
                创建 Session
              </Button>
            </form>
            <form
              onSubmit={(event: FormEvent<HTMLFormElement>) => {
                event.preventDefault();
                const value = new FormData(event.currentTarget).get("input");
                const input = typeof value === "string" ? value : "";
                void createTurnMutation.mutateAsync(input);
              }}
            >
              <label>
                <span>Turn 输入</span>
                <input
                  name="input"
                  required
                  disabled={!activeSession}
                  placeholder="发送到固定 Snapshot"
                />
              </label>
              <Button type="submit" disabled={!activeSession || createTurnMutation.isPending}>
                运行 Turn
              </Button>
            </form>
            <Button
              variant="danger"
              disabled={
                !runId ||
                ["SUCCEEDED", "FAILED", "CANCELLED", "ABANDONED"].includes(run.data?.status ?? "")
              }
              onClick={() =>
                void cancelRuntimeRun(runId!, request).then((response) =>
                  unwrapGenerated<void>(response, [202]),
                )
              }
            >
              <Square size={14} />
              Cancel
            </Button>
          </section>
          {createSessionMutation.error ? (
            <ProblemState error={createSessionMutation.error} />
          ) : null}
          {createTurnMutation.error ? <ProblemState error={createTurnMutation.error} /> : null}
          {activeSession ? (
            <section className="session-pin" aria-label="Session 固定发布信息">
              <div>
                <span>Session</span>
                <code>{activeSession.sessionId}</code>
              </div>
              <div>
                <span>Revision</span>
                <code>{activeSession.revisionId}</code>
              </div>
              <div>
                <span>Snapshot</span>
                <code>{activeSession.snapshotHash}</code>
              </div>
            </section>
          ) : null}
          <SplitPane
            secondaryLabel="Runtime Event Inspector"
            primary={
              <div className="runtime-canvas">
                <section className="panel message-stream">
                  <header className="panel__header">
                    <div>
                      <p className="eyebrow">MESSAGE STREAMING</p>
                      <h2>模型输出</h2>
                    </div>
                    <div className="inline-actions">
                      <Button variant="ghost" size="sm" onClick={() => streamRef.current?.stop()}>
                        <Pause size={15} />
                        暂停
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => streamRef.current?.start()}>
                        <RotateCcw size={15} />
                        恢复
                      </Button>
                    </div>
                  </header>
                  <pre aria-live="polite">{textOutput || "等待持久 Message/Text Event…"}</pre>
                </section>
                <section className="panel">
                  <header className="panel__header">
                    <div>
                      <p className="eyebrow">EVENT TIMELINE</p>
                      <h2>持久事件</h2>
                    </div>
                    <StatusBadge tone="info">{displayedEvents.length} EVENTS</StatusBadge>
                  </header>
                  {timelineItems.length ? (
                    <Timeline items={timelineItems} ariaLabel="Runtime Event 时间线" />
                  ) : (
                    <EmptyState
                      title="尚无 Event"
                      description="创建 Turn 后从持久 Event Log 回放并切换实时 SSE。"
                    />
                  )}
                </section>
                <section className="panel">
                  <header className="panel__header">
                    <h2>Model / Tool / MCP / RAG / Sub-Agent 调用树</h2>
                  </header>
                  <ul className="call-tree">
                    {callEvents.map((event) => (
                      <li key={event.eventId}>
                        <button type="button" onClick={() => setSelectedEventId(event.eventId)}>
                          <span>{event.eventType}</span>
                          <code>{event.traceId}</code>
                        </button>
                      </li>
                    ))}
                  </ul>
                </section>
              </div>
            }
            secondary={
              <Inspector
                title={selectedEvent?.eventType ?? "Event Inspector"}
                description="稳定 AgentArk Event Envelope；不展示隐藏推理链。"
              >
                {selectedEvent ? (
                  <>
                    <dl className="property-list">
                      <div>
                        <dt>Sequence</dt>
                        <dd>{selectedEvent.sequence}</dd>
                      </div>
                      <div>
                        <dt>Trace</dt>
                        <dd>
                          <Link to={`/operate?trace=${selectedEvent.traceId}`}>
                            {selectedEvent.traceId}
                          </Link>
                        </dd>
                      </div>
                      <div>
                        <dt>Usage</dt>
                        <dd>
                          <Link to={`/operate?run=${selectedEvent.runId}`}>按 Run 查看</Link>
                        </dd>
                      </div>
                    </dl>
                    <JsonViewer ariaLabel="Runtime Event Payload" value={selectedEvent} />
                    {selectedEvent.payloadRef ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => void download(selectedEvent)}
                      >
                        <Download size={15} />
                        下载 Artifact
                      </Button>
                    ) : null}
                  </>
                ) : (
                  <EmptyState
                    title="选择 Event"
                    description="Timeline 与调用树共享同一 Inspector。"
                  />
                )}
              </Inspector>
            }
          />
        </>
      )}
    </div>
  );
}
