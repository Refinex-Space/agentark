import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GitCompare, Rocket, RotateCcw, ShieldAlert } from "lucide-react";
import { useMemo, useState } from "react";

import { useEnvironmentsQuery } from "@/entities/tenant/api/tenant-queries";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  compareAgentRevisions,
  createDeployment,
  getAgentDraft,
  getAgentRevisionSnapshot,
  listAgentRevisions,
  listAgents,
  listDeployments,
  promoteDeployment,
  publishAgentRevision,
  rollbackDeployment,
  validateAgentDraft,
} from "@/shared/api/generated/control/client";
import type {
  AgentDraftResponseResponse,
  AgentPageResponseResponse,
  AgentRevision,
  AgentRevisionComparison,
  AgentSnapshot,
  Deployment,
  DeploymentPageResponseResponse,
  ValidationReport,
} from "@/shared/api/generated/control/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";
import { createIdempotencyKey } from "@/shared/lib/idempotency";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  Inspector,
  JsonViewer,
  ProblemState,
  SplitPane,
  StatusBadge,
} from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** Release 页面：验证、发布、Snapshot、Diff、Deployment、Promote 与 Rollback。 */
export default function ReleasePage() {
  const queryClient = useQueryClient();
  const request = useApiRequest();
  const { selection } = useTenant();
  const environments = useEnvironmentsQuery();
  const projectId = selection.projectId;
  const environmentId = selection.environmentId;
  const [agentId, setAgentId] = useState<string>();
  const [revisionId, setRevisionId] = useState<string>();
  const [validation, setValidation] = useState<ValidationReport>();
  const [comparison, setComparison] = useState<AgentRevisionComparison>();
  const enabled = Boolean(projectId);

  const agents = useQuery<AgentPageResponseResponse>({
    queryKey: ["release", "agents", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listAgents(projectId!, { limit: 100 }, request), [200]),
  });
  const draft = useQuery<AgentDraftResponseResponse>({
    queryKey: ["release", "draft", projectId, agentId],
    enabled: Boolean(projectId && agentId),
    queryFn: async () => unwrapGenerated(await getAgentDraft(projectId!, agentId!, request), [200]),
  });
  const revisions = useQuery<AgentRevision[]>({
    queryKey: ["release", "revisions", projectId, agentId],
    enabled: Boolean(projectId && agentId),
    queryFn: async () =>
      unwrapGenerated(await listAgentRevisions(projectId!, agentId!, request), [200]),
  });
  const snapshot = useQuery<AgentSnapshot>({
    queryKey: ["release", "snapshot", projectId, agentId, revisionId],
    enabled: Boolean(projectId && agentId && revisionId),
    queryFn: async () =>
      unwrapGenerated(
        await getAgentRevisionSnapshot(projectId!, agentId!, revisionId!, request),
        [200],
      ),
  });
  const deployments = useQuery<DeploymentPageResponseResponse>({
    queryKey: ["release", "deployments", projectId, environmentId],
    enabled: Boolean(projectId && environmentId),
    queryFn: async () =>
      unwrapGenerated(
        await listDeployments(projectId!, environmentId!, { limit: 100 }, request),
        [200],
      ),
  });

  const currentEnvironment = environments.data?.find((item) => item.id === environmentId);
  const isProduction = Boolean(currentEnvironment?.key.toLowerCase().includes("prod"));

  const validate = useMutation({
    mutationFn: async () =>
      unwrapGenerated<ValidationReport>(
        await validateAgentDraft(projectId!, agentId!, request),
        [200],
      ),
    onSuccess: setValidation,
  });
  const publish = useMutation({
    mutationFn: async () =>
      unwrapGenerated<AgentRevision>(
        await publishAgentRevision(
          projectId!,
          agentId!,
          {
            idempotencyKey: createIdempotencyKey(),
            expectedDraftVersion: draft.data!.version,
          },
          request,
        ),
        [201],
      ),
    onSuccess: async (revision) => {
      setRevisionId(revision.id);
      await queryClient.invalidateQueries({ queryKey: ["release", "revisions"] });
    },
  });
  const deploy = useMutation({
    mutationFn: async () =>
      unwrapGenerated<Deployment>(
        await createDeployment(
          projectId!,
          environmentId!,
          { agentId: agentId!, revisionId: revisionId!, trafficPolicy: "FULL", canaryPercent: 0 },
          request,
        ),
        [201],
      ),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["release", "deployments"] }),
  });

  const revisionOptions = useMemo(() => revisions.data ?? [], [revisions.data]);
  const selectedRevision = revisionOptions.find((item) => item.id === revisionId);
  const previousRevision = useMemo(() => {
    const index = revisionOptions.findIndex((item) => item.id === revisionId);
    return index > 0 ? revisionOptions[index - 1] : undefined;
  }, [revisionId, revisionOptions]);

  /** 比较所选 Revision 与上一 Revision。 */
  const compare = async (): Promise<void> => {
    if (!previousRevision || !selectedRevision) return;
    const response = await compareAgentRevisions(
      projectId!,
      agentId!,
      { baseRevisionId: previousRevision.id, targetRevisionId: selectedRevision.id },
      request,
    );
    setComparison(unwrapGenerated(response, [200]));
  };

  /** 执行带乐观锁的 Deployment 指针变更。 */
  const move = async (deployment: Deployment, target: AgentRevision, rollback: boolean) => {
    const response = rollback
      ? await rollbackDeployment(
          projectId!,
          environmentId!,
          deployment.id,
          { revisionId: target.id, expectedVersion: deployment.version },
          request,
        )
      : await promoteDeployment(
          projectId!,
          environmentId!,
          deployment.id,
          { revisionId: target.id, expectedVersion: deployment.version },
          request,
        );
    unwrapGenerated(response, [200]);
    await queryClient.invalidateQueries({ queryKey: ["release", "deployments"] });
  };

  if (!projectId) {
    return <EmptyState title="先选择 Project" description="发布资源不能跨 Project 解析。" />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="RELEASE"
        title="Validate → Publish → Deploy"
        description="发布创建不可变 Snapshot；Promote/Rollback 只移动 Environment Deployment 指针。"
        actions={
          <StatusBadge tone={isProduction ? "danger" : "info"}>
            {currentEnvironment?.name ?? "NO ENVIRONMENT"}
          </StatusBadge>
        }
      />
      <SplitPane
        secondaryLabel="Release Inspector"
        primary={
          <div className="page-stack">
            <section className="panel">
              <header className="panel__header">
                <div>
                  <p className="eyebrow">AGENT</p>
                  <h2>选择发布对象</h2>
                </div>
              </header>
              <DataTable
                caption="Agent 发布列表"
                rows={agents.data?.items ?? []}
                getRowKey={(row) => row.id}
                columns={[
                  { key: "name", header: "Agent", render: (row) => row.name },
                  { key: "key", header: "Key", render: (row) => row.key },
                  {
                    key: "select",
                    header: "操作",
                    render: (row) => (
                      <Button
                        size="sm"
                        variant={row.id === agentId ? "primary" : "secondary"}
                        onClick={() => {
                          setAgentId(row.id);
                          setRevisionId(undefined);
                          setValidation(undefined);
                        }}
                      >
                        选择
                      </Button>
                    ),
                  },
                ]}
              />
            </section>
            {agentId && draft.data ? (
              <section className="panel release-actions">
                <header className="panel__header">
                  <div>
                    <p className="eyebrow">DRAFT V{draft.data.version}</p>
                    <h2>验证与发布</h2>
                  </div>
                  <StatusBadge tone={validation?.valid ? "success" : "warning"}>
                    {validation ? (validation.valid ? "VALID" : "INVALID") : "NOT VALIDATED"}
                  </StatusBadge>
                </header>
                <div className="inline-actions">
                  <Button
                    variant="secondary"
                    onClick={() => validate.mutate()}
                    disabled={validate.isPending}
                  >
                    运行 Validation
                  </Button>
                  <Dialog
                    trigger={
                      <Button disabled={!validation?.valid}>
                        <Rocket size={16} />
                        发布 Revision
                      </Button>
                    }
                    title="发布不可变 Revision"
                    description={`Agent ${agentId} · Draft v${draft.data.version}`}
                  >
                    <div className="risk-confirm">
                      <ShieldAlert size={20} />
                      <p>
                        发布会固定全部资产版本、策略和 SecretRef，不会复制 Secret
                        Value。该操作不做乐观更新。
                      </p>
                      <Button onClick={() => publish.mutate()} disabled={publish.isPending}>
                        确认发布
                      </Button>
                      {publish.error ? <ProblemState error={publish.error} /> : null}
                    </div>
                  </Dialog>
                </div>
                {validation ? (
                  <JsonViewer ariaLabel="Validation Report" value={validation} />
                ) : null}
              </section>
            ) : null}
            <section className="panel">
              <header className="panel__header">
                <div>
                  <p className="eyebrow">IMMUTABLE</p>
                  <h2>Revision 与 Snapshot</h2>
                </div>
                <GitCompare size={18} />
              </header>
              <DataTable
                caption="Agent Revision"
                rows={revisionOptions}
                getRowKey={(row) => row.id}
                columns={[
                  { key: "number", header: "Revision", render: (row) => `#${row.revisionNumber}` },
                  { key: "provider", header: "Provider", render: (row) => row.runtimeProvider },
                  {
                    key: "hash",
                    header: "Snapshot Hash",
                    render: (row) => <code>{row.contentHash.slice(0, 24)}…</code>,
                  },
                  {
                    key: "open",
                    header: "操作",
                    render: (row) => (
                      <Button size="sm" variant="secondary" onClick={() => setRevisionId(row.id)}>
                        Inspect
                      </Button>
                    ),
                  },
                ]}
              />
            </section>
            <section className="panel">
              <header className="panel__header">
                <div>
                  <p className="eyebrow">DEPLOYMENT</p>
                  <h2>Environment 指针</h2>
                </div>
              </header>
              {!environmentId ? (
                <EmptyState
                  title="选择 Environment"
                  description="Deployment 必须属于明确 Environment。"
                />
              ) : (
                <>
                  <Dialog
                    trigger={<Button disabled={!revisionId || !agentId}>创建 Deployment</Button>}
                    title="创建 Environment Deployment"
                    description={`${currentEnvironment?.name ?? environmentId} · ${isProduction ? "生产风险操作" : "非生产环境"}`}
                  >
                    <div className="risk-confirm">
                      <ShieldAlert size={20} />
                      <p>
                        目标 Revision：{revisionId ?? "未选择"}。新 Deployment 只执行 FULL
                        流量策略。
                      </p>
                      <Button
                        onClick={() => deploy.mutate()}
                        disabled={deploy.isPending || !revisionId}
                      >
                        确认创建
                      </Button>
                    </div>
                  </Dialog>
                  <DataTable
                    caption="Environment Deployment"
                    rows={deployments.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "agent", header: "Agent", render: (row) => row.agentId },
                      {
                        key: "revision",
                        header: "Desired Revision",
                        render: (row) => row.desiredRevisionId,
                      },
                      { key: "status", header: "状态", render: (row) => row.status },
                      { key: "version", header: "Version", render: (row) => row.version },
                      {
                        key: "actions",
                        header: "操作",
                        render: (row) => (
                          <div className="inline-actions">
                            <Dialog
                              trigger={
                                <Button
                                  size="sm"
                                  disabled={
                                    !selectedRevision ||
                                    row.desiredRevisionId === selectedRevision.id
                                  }
                                >
                                  Promote
                                </Button>
                              }
                              title={isProduction ? "确认生产 Promote" : "确认 Promote"}
                              description={`Environment: ${currentEnvironment?.name ?? environmentId}`}
                            >
                              <div className="risk-confirm">
                                <ShieldAlert size={20} />
                                <p>
                                  从 {row.desiredRevisionId} 切换到 {selectedRevision?.id}。已有
                                  Session 不会漂移。
                                </p>
                                <Button
                                  onClick={() =>
                                    selectedRevision && void move(row, selectedRevision, false)
                                  }
                                >
                                  确认 Promote
                                </Button>
                              </div>
                            </Dialog>
                            <Button
                              size="sm"
                              variant="secondary"
                              disabled={!previousRevision}
                              onClick={() =>
                                previousRevision && void move(row, previousRevision, true)
                              }
                            >
                              <RotateCcw size={14} />
                              Rollback
                            </Button>
                          </div>
                        ),
                      },
                    ]}
                  />
                </>
              )}
            </section>
          </div>
        }
        secondary={
          <Inspector
            title="Snapshot Inspector"
            description="只读 Canonical Snapshot；Secret 只保留 SecretRef。"
          >
            {snapshot.error ? (
              <ProblemState error={snapshot.error} />
            ) : snapshot.data ? (
              <JsonViewer ariaLabel="Agent Revision Snapshot" value={snapshot.data.snapshot} />
            ) : (
              <EmptyState
                title="选择 Revision"
                description="Snapshot Inspector 通过 Public API 和 Project 授权读取。"
              />
            )}
            <div className="inspector-actions">
              <Button
                size="sm"
                variant="secondary"
                disabled={!previousRevision}
                onClick={() => void compare()}
              >
                与上一 Revision 比较
              </Button>
            </div>
            {comparison ? (
              <JsonViewer ariaLabel="Revision Diff Summary" value={comparison} />
            ) : null}
          </Inspector>
        }
      />
    </div>
  );
}
