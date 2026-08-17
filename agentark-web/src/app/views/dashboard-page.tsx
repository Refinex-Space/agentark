import { Activity, ArrowUpRight, Database, GitBranch, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/widgets/app-shell/app-shell";
import { Button, CodeViewer, StatusBadge, Timeline } from "@/shared/ui";

const foundationSteps = [
  {
    id: "contract",
    title: "公共契约已接入",
    detail: "Control、Runtime、Scheduler 生成客户端由 OpenAPI 可重复生成。",
    status: "success" as const,
    time: "v1",
  },
  {
    id: "sse",
    title: "Runtime Event Client",
    detail: "支持 Last-Event-ID、重连去重、Schema v1 和有界内存。",
    status: "success" as const,
    time: "SSE",
  },
  {
    id: "features",
    title: "核心产品流程",
    detail: "Govern、Build、Release、Run、Approval 与 Operate 已连接 Public API。",
    status: "success" as const,
    time: "P18",
  },
];

/** Phase 18 核心产品流程总览页。 */
export default function DashboardPage() {
  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="PRODUCT FLOW"
        title="Build → Publish → Deploy → Run"
        description="从租户治理、版本资产到不可变发布、运行审批和 Scheduler 运维，所有生产路径均通过真实 Public API。"
        actions={
          <Button asChild>
            <Link to="/build">
              开始构建 Agent
              <ArrowUpRight aria-hidden="true" size={16} />
            </Link>
          </Button>
        }
      />

      <section className="metric-grid" aria-label="平台基础状态">
        <article className="metric-card metric-card--accent">
          <div className="metric-card__icon">
            <Activity aria-hidden="true" size={20} />
          </div>
          <p>Runtime Event</p>
          <strong>Schema v1</strong>
          <span>持久事实优先，SSE 可恢复消费</span>
        </article>
        <article className="metric-card">
          <div className="metric-card__icon">
            <GitBranch aria-hidden="true" size={20} />
          </div>
          <p>Public API</p>
          <strong>3 Clients</strong>
          <span>Control / Runtime / Scheduler</span>
        </article>
        <article className="metric-card">
          <div className="metric-card__icon">
            <ShieldCheck aria-hidden="true" size={20} />
          </div>
          <p>Credential Policy</p>
          <strong>Memory only</strong>
          <span>Token 与 API Key 不进入持久存储</span>
        </article>
        <article className="metric-card">
          <div className="metric-card__icon">
            <Database aria-hidden="true" size={20} />
          </div>
          <p>Tenant Context</p>
          <strong>Intent only</strong>
          <span>下游服务独立认证和授权</span>
        </article>
      </section>

      <section className="dashboard-grid">
        <article className="panel">
          <header className="panel__header">
            <div>
              <p className="eyebrow">READINESS</p>
              <h2>Web Foundation 进度</h2>
            </div>
            <StatusBadge tone="info">PHASE 18</StatusBadge>
          </header>
          <Timeline items={foundationSteps} ariaLabel="核心产品流程进度" />
        </article>

        <article className="panel panel--terminal">
          <header className="panel__header">
            <div>
              <p className="eyebrow">CONTRACT FIRST</p>
              <h2>生成客户端边界</h2>
            </div>
            <span className="terminal-dots" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
          </header>
          <CodeViewer
            ariaLabel="OpenAPI 客户端生成命令"
            language="shell"
            value={`pnpm api:generate\npnpm api:check\n\n# generated is transport, not UI domain\nsrc/shared/api/generated/`}
          />
        </article>
      </section>
    </div>
  );
}
