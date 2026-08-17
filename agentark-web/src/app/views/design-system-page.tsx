import { Ellipsis, Info, Plus, Rocket, Settings2, Sparkles, Trash2 } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/widgets/app-shell/app-shell";
import {
  ActionMenu,
  Button,
  CodeViewer,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  Input,
  Inspector,
  JsonViewer,
  LoadingState,
  Popover,
  Skeleton,
  SplitPane,
  StatusBadge,
  Tabs,
  Timeline,
  useToast,
} from "@/shared/ui";

/** 组件展示表格使用的资产行。 */
interface AssetRow {
  /** 资产稳定键。 */
  id: string;
  /** 资产名。 */
  name: string;
  /** 资产类型。 */
  kind: string;
  /** 当前版本状态。 */
  status: "READY" | "DRAFT";
}

const assets: AssetRow[] = [
  { id: "model-primary", name: "Primary Reasoning", kind: "Model Profile", status: "READY" },
  { id: "prompt-system", name: "Operations System", kind: "Prompt", status: "DRAFT" },
];

const timelineItems = [
  { id: "a", title: "Revision published", status: "success" as const, time: "10:24" },
  { id: "b", title: "Deployment promoted", status: "success" as const, time: "10:26" },
  { id: "c", title: "Waiting approval", status: "running" as const, time: "10:28" },
];

/** AgentArk Design Token 与核心组件测试页。 */
export default function DesignSystemPage() {
  const { notify } = useToast();
  const [inputError, setInputError] = useState(false);

  return (
    <div className="page-stack design-page">
      <PageHeader
        eyebrow="DESIGN SYSTEM / FOUNDATION"
        title="AgentArk Interface Language"
        description="独立 Token、明暗主题、可访问交互与工作台组件；只借鉴终端工作台的层次，不复制任何上游品牌或资产。"
        actions={<StatusBadge tone="success">WCAG 2.2 AA BASELINE</StatusBadge>}
      />

      <section className="design-section" aria-labelledby="tokens-heading">
        <header>
          <p className="eyebrow">01 / TOKENS</p>
          <h2 id="tokens-heading">Surface · Border · Status</h2>
        </header>
        <div className="token-grid">
          {[
            ["Canvas", "--canvas"],
            ["Surface", "--surface"],
            ["Raised", "--surface-raised"],
            ["Border", "--border"],
            ["Accent", "--accent"],
            ["Success", "--success"],
            ["Warning", "--warning"],
            ["Danger", "--danger"],
          ].map(([label, token]) => (
            <div className="token-card" key={token}>
              <span style={{ background: `var(${token})` }} aria-hidden="true" />
              <strong>{label}</strong>
              <code>{token}</code>
            </div>
          ))}
        </div>
      </section>

      <section className="design-section" aria-labelledby="controls-heading">
        <header>
          <p className="eyebrow">02 / CONTROLS</p>
          <h2 id="controls-heading">Button · Input · Overlay · Menu</h2>
        </header>
        <div className="component-card component-card--grid">
          <div className="component-stack">
            <h3>Actions</h3>
            <div className="inline-actions inline-actions--wrap">
              <Button>
                <Rocket aria-hidden="true" size={16} />
                发布 Revision
              </Button>
              <Button variant="secondary">验证 Draft</Button>
              <Button variant="ghost">取消</Button>
              <Button variant="danger">
                <Trash2 aria-hidden="true" size={16} />
                归档
              </Button>
              <Button disabled>不可用</Button>
            </div>
          </div>

          <div className="component-stack">
            <h3>Field</h3>
            <label className="field-label" htmlFor="design-name">
              Agent 名称
            </label>
            <Input
              id="design-name"
              defaultValue="Operations Copilot"
              aria-invalid={inputError}
              {...(inputError ? { errorId: "design-name-error" } : {})}
              onBlur={(event) => setInputError(event.currentTarget.value.trim().length < 3)}
            />
            {inputError ? (
              <p id="design-name-error" className="field-error">
                名称至少需要 3 个字符。
              </p>
            ) : (
              <p className="field-help">失去焦点时验证最小长度。</p>
            )}
          </div>

          <div className="component-stack">
            <h3>Overlays</h3>
            <div className="inline-actions inline-actions--wrap">
              <Dialog
                trigger={<Button variant="secondary">打开 Dialog</Button>}
                title="发布不可变 Revision"
                description="发布后旧 Snapshot 不会随资产更新而改变。"
              >
                <div className="dialog-demo-content">
                  <StatusBadge tone="warning">需要验证</StatusBadge>
                  <p>
                    确认所有 Secret Binding、Model Capability 和 Knowledge Revision 已通过校验。
                  </p>
                  <Button>开始验证</Button>
                </div>
              </Dialog>
              <Popover
                trigger={
                  <Button variant="secondary">
                    <Info aria-hidden="true" size={16} />
                    查看说明
                  </Button>
                }
                ariaLabel="版本说明"
              >
                <div className="context-popover">
                  <strong>Immutable by default</strong>
                  <p>已发布版本只能被引用或归档，不能原地更新。</p>
                </div>
              </Popover>
              <ActionMenu
                ariaLabel="更多操作"
                trigger={
                  <Button variant="ghost" size="icon" aria-label="打开更多操作">
                    <Ellipsis aria-hidden="true" size={18} />
                  </Button>
                }
                items={[
                  { key: "inspect", label: "检查 JSON", onSelect: () => undefined },
                  { key: "archive", label: "归档", danger: true, onSelect: () => undefined },
                ]}
              />
            </div>
          </div>

          <div className="component-stack">
            <h3>Status</h3>
            <div className="inline-actions inline-actions--wrap">
              <StatusBadge>Draft</StatusBadge>
              <StatusBadge tone="info">Running</StatusBadge>
              <StatusBadge tone="success">Ready</StatusBadge>
              <StatusBadge tone="warning">Waiting</StatusBadge>
              <StatusBadge tone="danger">Failed</StatusBadge>
            </div>
          </div>
        </div>
      </section>

      <section className="design-section" aria-labelledby="data-heading">
        <header>
          <p className="eyebrow">03 / DATA</p>
          <h2 id="data-heading">Tabs · Table · Code · JSON</h2>
        </header>
        <div className="component-card">
          <Tabs
            defaultValue="assets"
            ariaLabel="数据组件示例"
            items={[
              {
                value: "assets",
                label: "资产",
                content: (
                  <DataTable
                    caption="AI 资产版本示例"
                    rows={assets}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "名称", render: (row) => <strong>{row.name}</strong> },
                      { key: "kind", header: "类型", render: (row) => row.kind },
                      {
                        key: "status",
                        header: "状态",
                        render: (row) => (
                          <StatusBadge tone={row.status === "READY" ? "success" : "neutral"}>
                            {row.status}
                          </StatusBadge>
                        ),
                      },
                    ]}
                  />
                ),
              },
              {
                value: "contract",
                label: "契约",
                content: (
                  <CodeViewer
                    ariaLabel="Snapshot 契约示例"
                    language="json"
                    value={`{\n  "schemaVersion": 1,\n  "runtimeProvider": "agentscope",\n  "contentHash": "sha256:…"\n}`}
                  />
                ),
              },
            ]}
          />
        </div>
      </section>

      <section className="design-section" aria-labelledby="workbench-heading">
        <header>
          <p className="eyebrow">04 / WORKBENCH</p>
          <h2 id="workbench-heading">Split Pane · Timeline · Inspector</h2>
        </header>
        <SplitPane
          secondaryLabel="事件详情示例"
          primary={
            <div className="component-card component-card--flush">
              <Timeline items={timelineItems} ariaLabel="发布运行时间线示例" />
            </div>
          }
          secondary={
            <Inspector title="Waiting approval" description="稳定 Event Envelope 投影">
              <JsonViewer
                ariaLabel="审批事件示例"
                value={{ schemaVersion: 1, eventType: "approval.requested", sequence: 3 }}
              />
            </Inspector>
          }
        />
      </section>

      <section className="design-section" aria-labelledby="states-heading">
        <header>
          <p className="eyebrow">05 / FEEDBACK</p>
          <h2 id="states-heading">Loading · Empty · Error · Toast</h2>
        </header>
        <div className="feedback-grid">
          <div className="component-card">
            <LoadingState label="正在读取 Deployment" />
            <Skeleton lines={3} />
          </div>
          <div className="component-card">
            <EmptyState
              title="尚无 Agent"
              description="创建第一个 Agent Draft 后会显示在这里。"
              action={
                <Button size="sm">
                  <Plus aria-hidden="true" size={15} />
                  创建 Agent
                </Button>
              }
            />
          </div>
          <div className="component-card">
            <ErrorState title="无法加载事件" description="连接已断开，可从最后事件 ID 恢复。" />
          </div>
          <div className="component-card notification-demo" id="commands">
            <Sparkles aria-hidden="true" />
            <h3>Notification</h3>
            <p>通知不夺取焦点，并使用文本说明结果。</p>
            <Button
              size="sm"
              variant="secondary"
              onClick={() =>
                notify({
                  tone: "success",
                  title: "验证完成",
                  description: "当前 Draft 可以发布为不可变 Revision。",
                })
              }
            >
              <Settings2 aria-hidden="true" size={15} />
              触发通知
            </Button>
          </div>
        </div>
      </section>
    </div>
  );
}
