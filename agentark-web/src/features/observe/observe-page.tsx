import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, BarChart3, FlaskConical, Gauge, ScrollText } from "lucide-react";
import { useMemo, type FormEvent } from "react";
import { Link } from "react-router-dom";

import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  createGovernanceEvaluationDataset,
  createGovernanceEvaluator,
  createGovernancePriceTable,
  createGovernanceQuotaPolicy,
  getGovernanceOverview,
  listGovernanceAuditEvents,
  listGovernanceEvaluationDatasets,
  listGovernanceEvaluationRuns,
  listGovernanceEvaluators,
  listGovernancePriceTables,
  listGovernanceQuotaPolicies,
  listGovernanceReleaseGates,
  listGovernanceUsage,
  listGovernanceUsageAggregates,
  runGovernanceEvaluation,
  saveGovernanceReleaseGate,
} from "@/shared/api/generated/control/client";
import type {
  AuditEvent,
  EvaluationDataset,
  EvaluationRun,
  Evaluator,
  GovernanceOverview,
  PriceTable,
  QuotaPolicy,
  ReleaseGate,
  UsageAggregate,
  UsageLedgerEntry,
} from "@/shared/api/generated/control/models";
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

const zeroHash = `sha256:${"0".repeat(64)}`;

/** 从 HTML 表单读取并裁剪字符串。 */
function field(form: HTMLFormElement, name: string): string {
  const value = new FormData(form).get(name);
  return typeof value === "string" ? value.trim() : "";
}

/** 解析必需 JSON 对象并拒绝数组。 */
function jsonObject(value: string): Record<string, unknown> {
  const parsed = JSON.parse(value) as unknown;
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("JSON 必须是对象");
  }
  return parsed as Record<string, unknown>;
}

/** Phase 19 治理与观测页面：Trace/Audit、Usage/Cost、Quota 和 Evaluation。 */
export default function ObservePage() {
  const { selection } = useTenant();
  const request = useApiRequest();
  const queryClient = useQueryClient();
  const projectId = selection.projectId;
  const enabled = Boolean(projectId);
  const range = useMemo(() => {
    const to = new Date();
    const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
    return { from: from.toISOString(), to: to.toISOString() };
  }, []);

  const overview = useQuery<GovernanceOverview>({
    queryKey: ["governance", "overview", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await getGovernanceOverview(projectId!, request), [200]),
  });
  const audits = useQuery<AuditEvent[]>({
    queryKey: ["governance", "audit", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listGovernanceAuditEvents(projectId!, { limit: 100 }, request), [200]),
  });
  const usage = useQuery<UsageLedgerEntry[]>({
    queryKey: ["governance", "usage", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listGovernanceUsage(projectId!, { limit: 100 }, request), [200]),
  });
  const aggregates = useQuery<UsageAggregate[]>({
    queryKey: ["governance", "usage-aggregate", projectId, range],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listGovernanceUsageAggregates(
          projectId!,
          { from: range.from, to: range.to, limit: 100 },
          request,
        ),
        [200],
      ),
  });
  const priceTables = useQuery<PriceTable[]>({
    queryKey: ["governance", "price-tables", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listGovernancePriceTables(projectId!, { limit: 100 }, request), [200]),
  });
  const quotas = useQuery<QuotaPolicy[]>({
    queryKey: ["governance", "quotas", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listGovernanceQuotaPolicies(projectId!, { limit: 100 }, request),
        [200],
      ),
  });
  const datasets = useQuery<EvaluationDataset[]>({
    queryKey: ["governance", "datasets", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listGovernanceEvaluationDatasets(projectId!, { limit: 100 }, request),
        [200],
      ),
  });
  const evaluators = useQuery<Evaluator[]>({
    queryKey: ["governance", "evaluators", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listGovernanceEvaluators(projectId!, { limit: 100 }, request), [200]),
  });
  const evaluationRuns = useQuery<EvaluationRun[]>({
    queryKey: ["governance", "evaluation-runs", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listGovernanceEvaluationRuns(projectId!, { limit: 100 }, request),
        [200],
      ),
  });
  const gates = useQuery<ReleaseGate[]>({
    queryKey: ["governance", "release-gates", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listGovernanceReleaseGates(projectId!, { limit: 100 }, request), [200]),
  });

  const priceCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createGovernancePriceTable(
          projectId!,
          {
            key: field(form, "key"),
            name: field(form, "name"),
            currency: field(form, "currency"),
            effectiveFrom: new Date(field(form, "effectiveFrom")).toISOString(),
            entries: JSON.parse(field(form, "entries")) as Record<string, number>,
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });
  const quotaCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) => {
      const metric = field(form, "metric") as
        "REQUEST_RATE" | "INPUT_TOKEN" | "OUTPUT_TOKEN" | "COST" | "CONCURRENT_RUN";
      return unwrapGenerated(
        await createGovernanceQuotaPolicy(
          projectId!,
          {
            scopeType: field(form, "scopeType") as "PROJECT" | "DEPLOYMENT" | "MODEL",
            scopeRef: field(form, "scopeRef"),
            metric,
            enforcement: field(form, "enforcement") as "SOFT" | "HARD",
            limitValue: Number(field(form, "limitValue")),
            windowSeconds:
              metric === "CONCURRENT_RUN" ? null : Number(field(form, "windowSeconds")),
            budgetAction: field(form, "budgetAction") as "WARN" | "REQUIRE_APPROVAL" | "STOP",
            effectiveFrom: new Date().toISOString(),
            effectiveUntil: null,
          },
          request,
        ),
        [201],
      );
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });
  const datasetCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) => {
      const expected = jsonObject(field(form, "expected"));
      return unwrapGenerated(
        await createGovernanceEvaluationDataset(
          projectId!,
          {
            key: field(form, "key"),
            name: field(form, "name"),
            description: field(form, "description") || null,
            schema: jsonObject(field(form, "schema")),
            contentHash: field(form, "contentHash"),
            cases: [
              {
                key: field(form, "caseKey"),
                inputObjectUri: field(form, "inputObjectUri"),
                inputContentHash: field(form, "inputContentHash"),
                expected,
                expectedContentHash: field(form, "expectedContentHash"),
                weight: 1,
              },
            ],
          },
          request,
        ),
        [201],
      );
    },
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });
  const evaluatorCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createGovernanceEvaluator(
          projectId!,
          {
            key: field(form, "key"),
            name: field(form, "name"),
            type: "DETERMINISTIC",
            config: jsonObject(field(form, "config")),
            contentHash: field(form, "contentHash"),
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });
  const evaluationRun = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await runGovernanceEvaluation(
          projectId!,
          {
            candidateRevisionId: field(form, "revisionId"),
            datasetVersionId: field(form, "datasetVersionId"),
            evaluatorVersionId: field(form, "evaluatorVersionId"),
            threshold: Number(field(form, "threshold")),
            baselineRunId: field(form, "baselineRunId") || null,
            observedHashes: JSON.parse(field(form, "observedHashes")) as Record<string, string>,
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });
  const gateSave = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await saveGovernanceReleaseGate(
          projectId!,
          {
            agentId: field(form, "agentId"),
            environmentId: field(form, "environmentId") || null,
            datasetVersionId: field(form, "datasetVersionId"),
            evaluatorVersionId: field(form, "evaluatorVersionId"),
            threshold: Number(field(form, "threshold")),
            enforcement: field(form, "enforcement") as "SOFT" | "HARD",
            status: "ACTIVE",
          },
          request,
        ),
        [200],
      ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["governance"] }),
  });

  if (!projectId) {
    return <EmptyState title="先选择 Project" description="治理事实必须在严格项目权限下查询。" />;
  }

  const queryError =
    overview.error ?? audits.error ?? usage.error ?? aggregates.error ?? quotas.error;

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="OBSERVE / GOVERN"
        title="Trace、Audit、Usage、Quota 与 Evaluation"
        description="Telemetry、Runtime Event 与安全 Audit 分离；成本固定价格版本，发布门禁固定全部评估版本。"
        actions={
          <StatusBadge tone={queryError ? "danger" : "success"}>GOVERNANCE ONLINE</StatusBadge>
        }
      />
      {queryError ? <ProblemState error={queryError} /> : null}
      <section className="metric-grid" aria-label="治理概览">
        <article className="metric-card">
          <ScrollText size={18} />
          <p>Audit / 24h</p>
          <strong>{overview.data?.auditCount ?? 0}</strong>
          <span>append-only</span>
        </article>
        <article className="metric-card">
          <BarChart3 size={18} />
          <p>Tokens / 24h</p>
          <strong>{overview.data?.tokenCount ?? 0}</strong>
          <span>{String(overview.data?.costAmount ?? 0)} cost</span>
        </article>
        <article className="metric-card">
          <Gauge size={18} />
          <p>Active Quota</p>
          <strong>{overview.data?.activeQuotaCount ?? 0}</strong>
          <span>soft / hard</span>
        </article>
        <article className="metric-card">
          <FlaskConical size={18} />
          <p>Evaluation / 24h</p>
          <strong>{overview.data?.evaluationRunCount ?? 0}</strong>
          <span>fixed versions</span>
        </article>
      </section>
      <Tabs
        defaultValue="audit"
        ariaLabel="治理与观测工作区"
        items={[
          {
            value: "audit",
            label: "Trace / Audit",
            content: (
              <section className="panel">
                <DataTable
                  caption="安全审计事件"
                  rows={audits.data ?? []}
                  getRowKey={(row) => row.id}
                  columns={[
                    {
                      key: "time",
                      header: "时间",
                      render: (row) => new Date(row.occurredAt).toLocaleString(),
                    },
                    { key: "action", header: "Action", render: (row) => row.action },
                    { key: "result", header: "结果", render: (row) => row.result },
                    { key: "principal", header: "Principal", render: (row) => row.principalRef },
                    {
                      key: "trace",
                      header: "Trace",
                      render: (row) =>
                        row.traceId ? (
                          <Link to={`/observe?trace=${row.traceId}`} className="text-link">
                            {row.traceId.slice(0, 12)}…
                          </Link>
                        ) : (
                          "—"
                        ),
                    },
                  ]}
                />
              </section>
            ),
          },
          {
            value: "usage",
            label: "Usage / Cost",
            content: (
              <div className="page-stack">
                <DataTable
                  caption="Usage Cost 聚合"
                  rows={aggregates.data ?? []}
                  getRowKey={(row) => `${row.periodStart}-${row.provider}-${row.model}`}
                  columns={[
                    {
                      key: "period",
                      header: "窗口",
                      render: (row) => new Date(row.periodStart).toLocaleDateString(),
                    },
                    {
                      key: "provider",
                      header: "Provider / Model",
                      render: (row) => `${row.provider} / ${row.model}`,
                    },
                    {
                      key: "tokens",
                      header: "Tokens",
                      render: (row) => row.inputTokens + row.outputTokens + row.embeddingTokens,
                    },
                    {
                      key: "cost",
                      header: "Cost",
                      render: (row) => `${row.currency} ${row.costAmount}`,
                    },
                    {
                      key: "estimate",
                      header: "Estimate",
                      render: (row) => `${row.estimatedRecords}/${row.sourceRecords}`,
                    },
                  ]}
                />
                <DataTable
                  caption="Usage 明细"
                  rows={usage.data ?? []}
                  getRowKey={(row) => row.id}
                  columns={[
                    { key: "type", header: "Type", render: (row) => row.usageType },
                    { key: "provider", header: "Provider", render: (row) => row.provider },
                    {
                      key: "tokens",
                      header: "Input / Output",
                      render: (row) => `${row.inputTokens} / ${row.outputTokens}`,
                    },
                    {
                      key: "estimate",
                      header: "精度",
                      render: (row) => (row.estimated ? "ESTIMATE" : "PROVIDER"),
                    },
                    {
                      key: "time",
                      header: "时间",
                      render: (row) => new Date(row.occurredAt).toLocaleString(),
                    },
                  ]}
                />
                <div className="resource-grid">
                  <form
                    className="resource-form"
                    onSubmit={(event: FormEvent<HTMLFormElement>) => {
                      event.preventDefault();
                      const form = event.currentTarget;
                      priceCreate.mutate(form, { onSuccess: () => form.reset() });
                    }}
                  >
                    <h3>创建 Price Table V1</h3>
                    <label>
                      <span>Key</span>
                      <input name="key" required />
                    </label>
                    <label>
                      <span>Name</span>
                      <input name="name" required />
                    </label>
                    <label>
                      <span>Currency</span>
                      <input name="currency" defaultValue="USD" required />
                    </label>
                    <label>
                      <span>Effective From</span>
                      <input name="effectiveFrom" type="datetime-local" required />
                    </label>
                    <label>
                      <span>Entries JSON</span>
                      <textarea
                        name="entries"
                        defaultValue={
                          '{"MODEL:default:input":0.000001,"MODEL:default:output":0.000002}'
                        }
                        required
                      />
                    </label>
                    <Button type="submit">创建不可变版本</Button>
                    {priceCreate.error ? <ProblemState error={priceCreate.error} /> : null}
                  </form>
                  <section className="resource-panel panel">
                    <h3>Price Tables</h3>
                    <JsonViewer ariaLabel="Price Table 列表" value={priceTables.data ?? []} />
                  </section>
                </div>
              </div>
            ),
          },
          {
            value: "quota",
            label: "Quota",
            content: (
              <div className="resource-grid">
                <form
                  className="resource-form"
                  onSubmit={(event: FormEvent<HTMLFormElement>) => {
                    event.preventDefault();
                    const form = event.currentTarget;
                    quotaCreate.mutate(form, { onSuccess: () => form.reset() });
                  }}
                >
                  <h3>创建软硬 Quota Policy</h3>
                  <label>
                    <span>Scope</span>
                    <select name="scopeType">
                      <option>PROJECT</option>
                      <option>DEPLOYMENT</option>
                      <option>MODEL</option>
                    </select>
                  </label>
                  <label>
                    <span>Scope Ref</span>
                    <input name="scopeRef" defaultValue={projectId} required />
                  </label>
                  <label>
                    <span>Metric</span>
                    <select name="metric">
                      <option>CONCURRENT_RUN</option>
                      <option>INPUT_TOKEN</option>
                      <option>OUTPUT_TOKEN</option>
                      <option>COST</option>
                      <option>REQUEST_RATE</option>
                    </select>
                  </label>
                  <label>
                    <span>Enforcement</span>
                    <select name="enforcement">
                      <option>HARD</option>
                      <option>SOFT</option>
                    </select>
                  </label>
                  <label>
                    <span>Limit</span>
                    <input
                      name="limitValue"
                      type="number"
                      min="0"
                      step="0.000001"
                      defaultValue="1"
                      required
                    />
                  </label>
                  <label>
                    <span>Window Seconds</span>
                    <input name="windowSeconds" type="number" min="1" defaultValue="3600" />
                  </label>
                  <label>
                    <span>Budget Action</span>
                    <select name="budgetAction">
                      <option>STOP</option>
                      <option>REQUIRE_APPROVAL</option>
                      <option>WARN</option>
                    </select>
                  </label>
                  <Button type="submit">创建 Policy</Button>
                  {quotaCreate.error ? <ProblemState error={quotaCreate.error} /> : null}
                </form>
                <section className="panel">
                  <DataTable
                    caption="Quota Policy"
                    rows={quotas.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      {
                        key: "scope",
                        header: "Scope",
                        render: (row) => `${row.scopeType} / ${row.scopeRef}`,
                      },
                      { key: "metric", header: "Metric", render: (row) => row.metric },
                      { key: "limit", header: "Limit", render: (row) => row.limitValue },
                      {
                        key: "mode",
                        header: "Mode",
                        render: (row) => `${row.enforcement} · ${row.budgetAction}`,
                      },
                      { key: "status", header: "状态", render: (row) => row.status },
                    ]}
                  />
                </section>
              </div>
            ),
          },
          {
            value: "evaluation",
            label: "Evaluation",
            content: (
              <div className="page-stack">
                <div className="resource-grid">
                  <form
                    className="resource-form"
                    onSubmit={(event: FormEvent<HTMLFormElement>) => {
                      event.preventDefault();
                      const form = event.currentTarget;
                      datasetCreate.mutate(form, { onSuccess: () => form.reset() });
                    }}
                  >
                    <h3>Dataset + V1 + Case</h3>
                    <label>
                      <span>Key</span>
                      <input name="key" required />
                    </label>
                    <label>
                      <span>Name</span>
                      <input name="name" required />
                    </label>
                    <label>
                      <span>Description</span>
                      <input name="description" />
                    </label>
                    <label>
                      <span>Schema JSON</span>
                      <textarea name="schema" defaultValue="{}" required />
                    </label>
                    <label>
                      <span>Version Hash</span>
                      <input name="contentHash" defaultValue={zeroHash} required />
                    </label>
                    <label>
                      <span>Case Key</span>
                      <input name="caseKey" defaultValue="case-1" required />
                    </label>
                    <label>
                      <span>Input ObjectRef</span>
                      <input
                        name="inputObjectUri"
                        defaultValue="object://evaluation/input-1"
                        required
                      />
                    </label>
                    <label>
                      <span>Input Hash</span>
                      <input name="inputContentHash" defaultValue={zeroHash} required />
                    </label>
                    <label>
                      <span>Expected JSON</span>
                      <textarea name="expected" defaultValue={'{"result":"ok"}'} required />
                    </label>
                    <label>
                      <span>Expected Hash</span>
                      <input name="expectedContentHash" defaultValue={zeroHash} required />
                    </label>
                    <Button type="submit">创建 Dataset V1</Button>
                    {datasetCreate.error ? <ProblemState error={datasetCreate.error} /> : null}
                  </form>
                  <form
                    className="resource-form"
                    onSubmit={(event: FormEvent<HTMLFormElement>) => {
                      event.preventDefault();
                      const form = event.currentTarget;
                      evaluatorCreate.mutate(form, { onSuccess: () => form.reset() });
                    }}
                  >
                    <h3>Deterministic Evaluator V1</h3>
                    <label>
                      <span>Key</span>
                      <input name="key" required />
                    </label>
                    <label>
                      <span>Name</span>
                      <input name="name" required />
                    </label>
                    <label>
                      <span>Config JSON</span>
                      <textarea name="config" defaultValue={'{"metric":"exact_match"}'} required />
                    </label>
                    <label>
                      <span>Version Hash</span>
                      <input name="contentHash" defaultValue={zeroHash} required />
                    </label>
                    <Button type="submit">创建 Evaluator V1</Button>
                    {evaluatorCreate.error ? <ProblemState error={evaluatorCreate.error} /> : null}
                  </form>
                </div>
                <div className="resource-grid">
                  <form
                    className="resource-form"
                    onSubmit={(event: FormEvent<HTMLFormElement>) => {
                      event.preventDefault();
                      evaluationRun.mutate(event.currentTarget);
                    }}
                  >
                    <h3>运行固定版本 Evaluation</h3>
                    <label>
                      <span>Candidate Revision ID</span>
                      <input name="revisionId" required />
                    </label>
                    <label>
                      <span>Dataset Version ID</span>
                      <input name="datasetVersionId" required />
                    </label>
                    <label>
                      <span>Evaluator Version ID</span>
                      <input name="evaluatorVersionId" required />
                    </label>
                    <label>
                      <span>Threshold</span>
                      <input
                        name="threshold"
                        type="number"
                        min="0"
                        max="1"
                        step="0.01"
                        defaultValue="0.8"
                        required
                      />
                    </label>
                    <label>
                      <span>Baseline Run ID</span>
                      <input name="baselineRunId" />
                    </label>
                    <label>
                      <span>Observed Hashes JSON</span>
                      <textarea
                        name="observedHashes"
                        defaultValue={`{"case-1":"${zeroHash}"}`}
                        required
                      />
                    </label>
                    <Button type="submit">运行 Deterministic Evaluation</Button>
                    {evaluationRun.error ? <ProblemState error={evaluationRun.error} /> : null}
                  </form>
                  <form
                    className="resource-form"
                    onSubmit={(event: FormEvent<HTMLFormElement>) => {
                      event.preventDefault();
                      gateSave.mutate(event.currentTarget);
                    }}
                  >
                    <h3>保存 Release Gate</h3>
                    <label>
                      <span>Agent ID</span>
                      <input name="agentId" required />
                    </label>
                    <label>
                      <span>Environment ID</span>
                      <input name="environmentId" />
                    </label>
                    <label>
                      <span>Dataset Version ID</span>
                      <input name="datasetVersionId" required />
                    </label>
                    <label>
                      <span>Evaluator Version ID</span>
                      <input name="evaluatorVersionId" required />
                    </label>
                    <label>
                      <span>Threshold</span>
                      <input
                        name="threshold"
                        type="number"
                        min="0"
                        max="1"
                        step="0.01"
                        defaultValue="0.8"
                        required
                      />
                    </label>
                    <label>
                      <span>Enforcement</span>
                      <select name="enforcement">
                        <option>HARD</option>
                        <option>SOFT</option>
                      </select>
                    </label>
                    <Button type="submit">保存 Gate</Button>
                    {gateSave.error ? <ProblemState error={gateSave.error} /> : null}
                  </form>
                </div>
                <DataTable
                  caption="Evaluation Run"
                  rows={evaluationRuns.data ?? []}
                  getRowKey={(row) => row.id}
                  columns={[
                    {
                      key: "revision",
                      header: "Revision",
                      render: (row) => row.candidateRevisionId,
                    },
                    {
                      key: "versions",
                      header: "Dataset / Evaluator",
                      render: (row) => `${row.datasetVersionId} / ${row.evaluatorVersionId}`,
                    },
                    {
                      key: "score",
                      header: "Score / Threshold",
                      render: (row) => `${String(row.totalScore ?? "—")} / ${row.threshold}`,
                    },
                    { key: "status", header: "状态", render: (row) => row.status },
                  ]}
                />
                <JsonViewer
                  ariaLabel="Evaluation 资源"
                  value={{
                    datasets: datasets.data ?? [],
                    evaluators: evaluators.data ?? [],
                    releaseGates: gates.data ?? [],
                  }}
                />
              </div>
            ),
          },
        ]}
      />
      <section className="correlation-banner">
        <Activity size={18} />
        <div>
          <strong>Telemetry Backend 不属于业务事实源</strong>
          <p>
            Collector 不可用时 Trace 可被丢弃；Audit、Usage、Quota Reservation 与 Evaluation 仍由
            MySQL 保存。
          </p>
        </div>
      </section>
    </div>
  );
}
