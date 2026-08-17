import { Pause, PlugZap, RotateCcw } from "lucide-react";

import { PageHeader } from "@/widgets/app-shell/app-shell";
import { Button, Inspector, JsonViewer, SplitPane, StatusBadge, Timeline } from "@/shared/ui";

const sampleEvents = [
  {
    id: "accepted",
    title: "run.accepted",
    detail: "Turn、Run、Work Item 与初始 Event 已在同一事务提交。",
    status: "success" as const,
    time: "seq 1",
  },
  {
    id: "started",
    title: "run.started",
    detail: "Runtime Worker 已取得有效 Lease 与 Fencing Token。",
    status: "success" as const,
    time: "seq 2",
  },
  {
    id: "approval",
    title: "approval.requested",
    detail: "等待有权限的主体作出幂等审批决定。",
    status: "running" as const,
    time: "seq 3",
  },
];

/** Phase 17 Runtime 事件体验与 Inspector 基础页。 */
export default function RuntimeWorkspacePage() {
  return (
    <div className="page-stack page-stack--flush">
      <PageHeader
        eyebrow="RUNTIME WORKSPACE"
        title="事件流与运行检查器"
        description="本页只验证工作台布局和 Runtime Event 交互基础，不会伪造或启动真实 Agent Run。"
        actions={
          <div className="inline-actions">
            <StatusBadge tone="warning">未连接</StatusBadge>
            <Button variant="secondary" disabled>
              <PlugZap aria-hidden="true" size={16} />
              连接 Run
            </Button>
          </div>
        }
      />

      <SplitPane
        secondaryLabel="Runtime Event 检查器"
        primary={
          <article className="panel runtime-canvas">
            <header className="panel__header">
              <div>
                <p className="eyebrow">EVENT TIMELINE</p>
                <h2>持久事件预览</h2>
              </div>
              <div className="inline-actions">
                <Button variant="ghost" size="sm" disabled>
                  <Pause aria-hidden="true" size={15} />
                  暂停跟随
                </Button>
                <Button variant="ghost" size="sm" disabled>
                  <RotateCcw aria-hidden="true" size={15} />
                  从断点恢复
                </Button>
              </div>
            </header>
            <Timeline items={sampleEvents} ariaLabel="Runtime Event 示例时间线" />
          </article>
        }
        secondary={
          <Inspector
            title="approval.requested"
            description="Inspector 只展示稳定 Event Envelope，不展示隐藏推理链。"
          >
            <dl className="property-list">
              <div>
                <dt>Schema</dt>
                <dd>runtime-event/v1</dd>
              </div>
              <div>
                <dt>Sequence</dt>
                <dd>3</dd>
              </div>
              <div>
                <dt>Connection</dt>
                <dd>Last-Event-ID ready</dd>
              </div>
            </dl>
            <JsonViewer
              ariaLabel="Runtime Event 示例负载"
              value={{
                eventType: "approval.requested",
                payload: { approvalId: "redacted-preview", argumentHash: "sha256:…" },
              }}
            />
          </Inspector>
        }
      />
    </div>
  );
}
