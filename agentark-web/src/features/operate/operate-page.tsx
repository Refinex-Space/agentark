import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, CalendarClock, RotateCcw, Webhook } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  listDeployments,
  listKnowledgeBases,
  listKnowledgeRevisions,
} from "@/shared/api/generated/control/client";
import type {
  CursorPageKnowledgeBase,
  CursorPageKnowledgeRevision,
  DeploymentPageResponseResponse,
} from "@/shared/api/generated/control/models";
import { getRuntimeStatus } from "@/shared/api/generated/runtime/client";
import type { RuntimeStatus } from "@/shared/api/generated/runtime/models";
import {
  cancelSchedulerJob,
  createSchedulerTrigger,
  listSchedulerDeadLetters,
  listSchedulerJobs,
  listSchedulerTriggers,
  redriveSchedulerJob,
} from "@/shared/api/generated/scheduler/client";
import type {
  JobPage,
  ListSchedulerDeadLetters200,
  TriggerPage,
} from "@/shared/api/generated/scheduler/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";
import {
  Button,
  DataTable,
  EmptyState,
  JsonViewer,
  ProblemState,
  StatusBadge,
  Tabs,
} from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** 从表单读取字符串字段。 */
function field(form: HTMLFormElement, name: string): string {
  const value = new FormData(form).get(name);
  return typeof value === "string" ? value.trim() : "";
}

/** Operate 页面：Scheduler、Knowledge Ingestion、Runtime 与 Deployment 状态摘要。 */
export default function OperatePage() {
  const { selection } = useTenant();
  const request = useApiRequest();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [knowledgeBaseId, setKnowledgeBaseId] = useState<string>();
  const organizationId = selection.organizationId;
  const projectId = selection.projectId;
  const environmentId = selection.environmentId;
  const enabled = Boolean(organizationId && projectId);

  const jobs = useQuery<JobPage>({
    queryKey: ["operate", "jobs", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listSchedulerJobs(
          { organizationId: organizationId!, projectId: projectId!, limit: 100 },
          request,
        ),
        [200],
      ),
  });
  const triggers = useQuery<TriggerPage>({
    queryKey: ["operate", "triggers", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listSchedulerTriggers(
          { organizationId: organizationId!, projectId: projectId!, limit: 100 },
          request,
        ),
        [200],
      ),
  });
  const deadLetters = useQuery<ListSchedulerDeadLetters200>({
    queryKey: ["operate", "dead-letters", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listSchedulerDeadLetters(
          { organizationId: organizationId!, projectId: projectId!, limit: 100 },
          request,
        ),
        [200],
      ),
  });
  const runtimeStatus = useQuery<RuntimeStatus>({
    queryKey: ["operate", "runtime-status", projectId],
    enabled,
    refetchInterval: 10_000,
    queryFn: async () =>
      unwrapGenerated(
        await getRuntimeStatus({ organizationId: organizationId!, projectId: projectId! }, request),
        [200],
      ),
  });
  const deployments = useQuery<DeploymentPageResponseResponse>({
    queryKey: ["operate", "deployments", projectId, environmentId],
    enabled: Boolean(projectId && environmentId),
    queryFn: async () =>
      unwrapGenerated(
        await listDeployments(projectId!, environmentId!, { limit: 100 }, request),
        [200],
      ),
  });
  const knowledgeBases = useQuery<CursorPageKnowledgeBase>({
    queryKey: ["operate", "knowledge-bases", projectId],
    enabled: Boolean(projectId),
    queryFn: async () =>
      unwrapGenerated(await listKnowledgeBases(projectId!, { limit: 100 }, request), [200]),
  });
  const knowledgeRevisions = useQuery<CursorPageKnowledgeRevision>({
    queryKey: ["operate", "knowledge-revisions", projectId, knowledgeBaseId],
    enabled: Boolean(projectId && knowledgeBaseId),
    queryFn: async () =>
      unwrapGenerated(
        await listKnowledgeRevisions(projectId!, knowledgeBaseId!, { limit: 100 }, request),
        [200],
      ),
  });
  const triggerCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) => {
      const type = field(form, "type") as "CRON" | "WEBHOOK";
      return unwrapGenerated(
        await createSchedulerTrigger(
          {
            organizationId: organizationId!,
            projectId: projectId!,
            key: field(form, "key"),
            type,
            cronExpression: type === "CRON" ? field(form, "cronExpression") : null,
            zoneId: type === "CRON" ? field(form, "zoneId") : null,
            config: JSON.parse(field(form, "config")) as Record<string, string>,
            secretRef: type === "WEBHOOK" ? field(form, "secretRef") : null,
            targetContract: field(form, "targetContract"),
            targetJobType: field(form, "targetJobType") as
              "KNOWLEDGE_INGESTION" | "RUNTIME_TURN" | "OUTBOUND_WEBHOOK" | "CHANNEL_MESSAGE",
          },
          request,
        ),
        [201],
      );
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["operate", "triggers"] }),
  });

  if (!enabled) {
    return (
      <EmptyState
        title="先选择 Organization 与 Project"
        description="Scheduler 和 Runtime 管理 API 要求精确租户选择。"
      />
    );
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="OPERATE"
        title="Jobs、Triggers、Ingestion 与 Runtime"
        description="Scheduler 只管理持久 Job；Agent Turn 仍通过 Runtime 执行。"
        actions={
          <StatusBadge tone={runtimeStatus.data?.healthy ? "success" : "warning"}>
            {runtimeStatus.data?.healthy ?? 0} HEALTHY RUNTIME
          </StatusBadge>
        }
      />
      {searchParams.get("trace") || searchParams.get("run") ? (
        <section className="correlation-banner">
          <Activity size={18} />
          <div>
            <strong>关联上下文</strong>
            <code>{searchParams.get("trace") ?? searchParams.get("run")}</code>
            <p>Trace、Usage 与 Audit 的完整查询面板由 Phase 19 接入；当前保留稳定关联。</p>
          </div>
        </section>
      ) : null}
      <section className="metric-grid">
        <article className="metric-card">
          <p>Runtime</p>
          <strong>{runtimeStatus.data?.registered ?? 0}</strong>
          <span>{runtimeStatus.data?.runtimeProvider ?? "unavailable"}</span>
        </article>
        <article className="metric-card">
          <p>Jobs</p>
          <strong>{jobs.data?.items.length ?? 0}</strong>
          <span>当前游标页</span>
        </article>
        <article className="metric-card">
          <p>Dead Letter</p>
          <strong>{deadLetters.data?.items.length ?? 0}</strong>
          <span>等待 Redrive</span>
        </article>
        <article className="metric-card">
          <p>Deployments</p>
          <strong>{deployments.data?.items.length ?? 0}</strong>
          <span>当前 Environment</span>
        </article>
      </section>
      <Tabs
        defaultValue="scheduler"
        ariaLabel="Operate 工作区"
        items={[
          {
            value: "scheduler",
            label: "Trigger / Job",
            content: (
              <div className="page-stack">
                <form
                  className="resource-form"
                  onSubmit={(event: FormEvent<HTMLFormElement>) => {
                    event.preventDefault();
                    void triggerCreate
                      .mutateAsync(event.currentTarget)
                      .then(() => event.currentTarget.reset());
                  }}
                >
                  <h3>创建 Cron / Webhook Trigger</h3>
                  <div className="resource-form__fields">
                    <label>
                      <span>Key</span>
                      <input name="key" required />
                    </label>
                    <label>
                      <span>类型</span>
                      <select name="type">
                        <option>CRON</option>
                        <option>WEBHOOK</option>
                      </select>
                    </label>
                    <label>
                      <span>Cron</span>
                      <input name="cronExpression" defaultValue="0 */5 * * * *" />
                    </label>
                    <label>
                      <span>Zone</span>
                      <input name="zoneId" defaultValue="Asia/Shanghai" />
                    </label>
                    <label>
                      <span>SecretRef（Webhook）</span>
                      <input name="secretRef" />
                    </label>
                    <label>
                      <span>Target Job</span>
                      <select name="targetJobType">
                        <option>RUNTIME_TURN</option>
                        <option>KNOWLEDGE_INGESTION</option>
                        <option>OUTBOUND_WEBHOOK</option>
                        <option>CHANNEL_MESSAGE</option>
                      </select>
                    </label>
                    <label>
                      <span>Contract</span>
                      <input
                        name="targetContract"
                        defaultValue="agentark.scheduler.trigger/v1"
                        required
                      />
                    </label>
                    <label className="field-span">
                      <span>Config JSON（禁止敏感键）</span>
                      <textarea name="config" defaultValue="{}" />
                    </label>
                  </div>
                  <Button type="submit" size="sm">
                    <CalendarClock size={15} />
                    创建 Trigger
                  </Button>
                  {triggerCreate.error ? <ProblemState error={triggerCreate.error} /> : null}
                </form>
                <DataTable
                  caption="Scheduler Trigger"
                  rows={triggers.data?.items ?? []}
                  getRowKey={(row) => row.id}
                  columns={[
                    { key: "key", header: "Trigger", render: (row) => row.key },
                    { key: "type", header: "类型", render: (row) => row.type },
                    { key: "target", header: "目标", render: (row) => row.targetJobType },
                    { key: "status", header: "状态", render: (row) => row.status },
                  ]}
                />
                <DataTable
                  caption="Scheduler Job"
                  rows={jobs.data?.items ?? []}
                  getRowKey={(row) => row.id}
                  columns={[
                    { key: "type", header: "Job", render: (row) => row.type },
                    { key: "business", header: "Business Key", render: (row) => row.businessKey },
                    { key: "status", header: "状态", render: (row) => row.status },
                    { key: "attempt", header: "Attempt", render: (row) => row.currentAttempt },
                    {
                      key: "cancel",
                      header: "操作",
                      render: (row) => (
                        <Button
                          size="sm"
                          variant="danger"
                          disabled={[
                            "SUCCEEDED",
                            "DEAD_LETTERED",
                            "CANCELLED",
                            "TIMED_OUT",
                          ].includes(row.status)}
                          onClick={() =>
                            void cancelSchedulerJob(
                              row.id,
                              { reason: "AgentArk Web 用户取消" },
                              { organizationId: organizationId!, projectId: projectId! },
                              request,
                            ).then((response) => {
                              unwrapGenerated<void>(response, [202]);
                              return jobs.refetch();
                            })
                          }
                        >
                          取消
                        </Button>
                      ),
                    },
                  ]}
                />
              </div>
            ),
          },
          {
            value: "dead-letter",
            label: "Dead Letter",
            content: (
              <section className="panel">
                <DataTable
                  caption="Scheduler Dead Letter"
                  rows={deadLetters.data?.items ?? []}
                  getRowKey={(row) => row.id}
                  emptyText="暂无 OPEN Dead Letter"
                  columns={[
                    { key: "job", header: "Job", render: (row) => row.jobId },
                    { key: "reason", header: "失败原因", render: (row) => row.reason },
                    { key: "count", header: "Redrive", render: (row) => row.redriveCount },
                    {
                      key: "action",
                      header: "操作",
                      render: (row) => (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() =>
                            void redriveSchedulerJob(
                              row.jobId,
                              { reason: "AgentArk Web 人工复核后 Redrive" },
                              { organizationId: organizationId!, projectId: projectId! },
                              request,
                            ).then((response) => {
                              unwrapGenerated<void>(response, [202]);
                              return Promise.all([deadLetters.refetch(), jobs.refetch()]);
                            })
                          }
                        >
                          <RotateCcw size={14} />
                          Redrive
                        </Button>
                      ),
                    },
                  ]}
                />
              </section>
            ),
          },
          {
            value: "integrations",
            label: "Webhook / Channel",
            content: (
              <section className="panel">
                <header className="panel__header">
                  <div>
                    <p className="eyebrow">DELIVERY</p>
                    <h2>Webhook 与 Channel 投递</h2>
                  </div>
                  <Webhook size={18} />
                </header>
                <p className="muted-copy">
                  Webhook 入口由签名 Trigger 管理；Channel 以 `CHANNEL_MESSAGE` Job 投影，不把 Agent
                  推理循环放进 Scheduler。
                </p>
                <DataTable
                  caption="Integration Trigger"
                  rows={(triggers.data?.items ?? []).filter(
                    (row) =>
                      row.type === "WEBHOOK" ||
                      row.targetJobType === "CHANNEL_MESSAGE" ||
                      row.targetJobType === "OUTBOUND_WEBHOOK",
                  )}
                  getRowKey={(row) => row.id}
                  columns={[
                    { key: "key", header: "Integration", render: (row) => row.key },
                    { key: "type", header: "入口", render: (row) => row.type },
                    { key: "job", header: "投递类型", render: (row) => row.targetJobType },
                    { key: "contract", header: "Contract", render: (row) => row.targetContract },
                  ]}
                />
              </section>
            ),
          },
          {
            value: "knowledge",
            label: "Knowledge Ingestion",
            content: (
              <div className="dashboard-grid">
                <section className="panel">
                  <DataTable
                    caption="Knowledge Base"
                    rows={knowledgeBases.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "Knowledge Base", render: (row) => row.name },
                      { key: "status", header: "状态", render: (row) => row.status },
                      {
                        key: "open",
                        header: "操作",
                        render: (row) => (
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => setKnowledgeBaseId(row.id)}
                          >
                            查看摄取
                          </Button>
                        ),
                      },
                    ]}
                  />
                </section>
                <section className="panel">
                  <DataTable
                    caption="Knowledge Revision Ingestion"
                    rows={knowledgeRevisions.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "revision", header: "Revision", render: (row) => row.id },
                      {
                        key: "status",
                        header: "状态",
                        render: (row) => (
                          <StatusBadge
                            tone={
                              row.status === "READY"
                                ? "success"
                                : row.status === "FAILED"
                                  ? "danger"
                                  : "warning"
                            }
                          >
                            {row.status}
                          </StatusBadge>
                        ),
                      },
                      {
                        key: "failure",
                        header: "Failure Code",
                        render: (row) => row.failureCode || "—",
                      },
                    ]}
                  />
                </section>
              </div>
            ),
          },
          {
            value: "status",
            label: "Runtime / Deployment",
            content: (
              <div className="dashboard-grid">
                <section className="panel">
                  <h2>Runtime Status</h2>
                  {runtimeStatus.error ? (
                    <ProblemState error={runtimeStatus.error} />
                  ) : (
                    <JsonViewer ariaLabel="Runtime 状态摘要" value={runtimeStatus.data ?? {}} />
                  )}
                </section>
                <section className="panel">
                  <h2>Deployment Status</h2>
                  <DataTable
                    caption="Deployment 状态摘要"
                    rows={deployments.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "agent", header: "Agent", render: (row) => row.agentId },
                      {
                        key: "revision",
                        header: "Revision",
                        render: (row) => row.desiredRevisionId,
                      },
                      { key: "status", header: "状态", render: (row) => row.status },
                    ]}
                  />
                </section>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
