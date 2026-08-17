import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Clock3, ExternalLink, ShieldAlert, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { useTenant } from "@/entities/tenant/model/tenant-context";
import { decideRuntimeApproval, listRuntimeApprovals } from "@/shared/api/generated/runtime/client";
import type { Approval, ApprovalPage } from "@/shared/api/generated/runtime/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";
import { createIdempotencyKey } from "@/shared/lib/idempotency";
import {
  Button,
  DataTable,
  EmptyState,
  Inspector,
  JsonViewer,
  ProblemState,
  SplitPane,
  StatusBadge,
} from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** Approval Center：参数只展示 Hash，决策使用服务端乐观锁和幂等键。 */
export default function ApprovalPageView() {
  const { selection } = useTenant();
  const request = useApiRequest();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<
    "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED" | "CANCELLED" | undefined
  >("PENDING");
  const [selected, setSelected] = useState<Approval>();
  const enabled = Boolean(selection.organizationId && selection.projectId);

  const approvals = useQuery<ApprovalPage>({
    queryKey: ["approvals", selection.projectId, status],
    enabled,
    queryFn: async () =>
      unwrapGenerated(
        await listRuntimeApprovals({ ...(status ? { status } : {}), limit: 100 }, request),
        [200],
      ),
  });
  const decision = useMutation({
    mutationFn: async ({
      approval,
      target,
    }: {
      approval: Approval;
      target: "APPROVED" | "REJECTED";
    }) =>
      unwrapGenerated<Approval>(
        await decideRuntimeApproval(
          approval.approvalId,
          { expectedVersion: approval.version, decision: target },
          { "Idempotency-Key": createIdempotencyKey() },
          request,
        ),
        [200],
      ),
    onSuccess: async (value) => {
      setSelected(value);
      await queryClient.invalidateQueries({ queryKey: ["approvals"] });
    },
  });

  if (!enabled) {
    return (
      <EmptyState
        title="先选择 Organization 与 Project"
        description="Approval 查询要求 Token 中的精确项目租户。"
      />
    );
  }

  return (
    <div className="page-stack page-stack--flush">
      <PageHeader
        eyebrow="APPROVAL CENTER"
        title="HITL 风险决策"
        description="Tool 参数原文不会返回浏览器；决策绑定参数 Hash、策略版本和乐观锁。"
        actions={<StatusBadge tone="warning">HUMAN IN THE LOOP</StatusBadge>}
      />
      <div className="filter-bar" role="group" aria-label="Approval 状态筛选">
        {["PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED"].map((value) => (
          <Button
            key={value}
            size="sm"
            variant={status === value ? "primary" : "ghost"}
            onClick={() => setStatus(value as typeof status)}
          >
            {value}
          </Button>
        ))}
        <Button
          size="sm"
          variant={!status ? "primary" : "ghost"}
          onClick={() => setStatus(undefined)}
        >
          ALL
        </Button>
      </div>
      <SplitPane
        secondaryLabel="Approval Inspector"
        primary={
          <section className="panel">
            {approvals.error ? (
              <ProblemState error={approvals.error} onRetry={() => void approvals.refetch()} />
            ) : (
              <DataTable
                caption="Runtime Approval"
                rows={approvals.data?.items ?? []}
                getRowKey={(row) => row.approvalId}
                emptyText="当前筛选条件没有 Approval"
                columns={[
                  { key: "tool", header: "Tool", render: (row) => row.toolName },
                  {
                    key: "risk",
                    header: "Risk / Policy",
                    render: (row) => (
                      <div>
                        <StatusBadge tone={row.status === "PENDING" ? "warning" : "info"}>
                          {row.status}
                        </StatusBadge>
                        <small>{row.policyVersion}</small>
                      </div>
                    ),
                  },
                  {
                    key: "expires",
                    header: "到期",
                    render: (row) => (
                      <time dateTime={row.expiresAt}>
                        {new Date(row.expiresAt).toLocaleString()}
                      </time>
                    ),
                  },
                  {
                    key: "inspect",
                    header: "操作",
                    render: (row) => (
                      <Button size="sm" variant="secondary" onClick={() => setSelected(row)}>
                        Inspect
                      </Button>
                    ),
                  },
                ]}
              />
            )}
          </section>
        }
        secondary={
          <Inspector
            title={selected?.toolName ?? "选择 Approval"}
            description="Argument Hash 是审批时固定的参数身份，不是参数原文。"
          >
            {selected ? (
              <>
                <div className="approval-risk">
                  <ShieldAlert size={20} />
                  <div>
                    <strong>{selected.action}</strong>
                    <code>{selected.argumentHash}</code>
                    <p>Policy：{selected.policyVersion}</p>
                  </div>
                </div>
                <dl className="property-list">
                  <div>
                    <dt>Status</dt>
                    <dd>{selected.status}</dd>
                  </div>
                  <div>
                    <dt>Expires</dt>
                    <dd>
                      <Clock3 size={14} /> {new Date(selected.expiresAt).toLocaleString()}
                    </dd>
                  </div>
                  <div>
                    <dt>Audit</dt>
                    <dd>
                      <Link to={`/runtime?run=${selected.runId}`}>
                        返回 Run <ExternalLink size={13} />
                      </Link>
                    </dd>
                  </div>
                </dl>
                {selected.status === "PENDING" ? (
                  <div className="approval-actions">
                    <Button
                      onClick={() => decision.mutate({ approval: selected, target: "APPROVED" })}
                    >
                      <Check size={15} />
                      Approve
                    </Button>
                    <Button
                      variant="danger"
                      onClick={() => decision.mutate({ approval: selected, target: "REJECTED" })}
                    >
                      <X size={15} />
                      Reject
                    </Button>
                  </div>
                ) : (
                  <StatusBadge tone={selected.status === "EXPIRED" ? "danger" : "info"}>
                    {selected.status}
                  </StatusBadge>
                )}
                {decision.error ? <ProblemState error={decision.error} /> : null}
                <JsonViewer ariaLabel="Approval 安全投影" value={selected} />
                <p className="muted-copy">
                  真实 Audit 事实与检索页面归 Phase 19；当前链接只保留 Run/Approval
                  关联，不伪造审计记录。
                </p>
              </>
            ) : (
              <EmptyState
                title="尚未选择 Approval"
                description="从左侧列表检查风险、策略和到期状态。"
              />
            )}
          </Inspector>
        }
      />
    </div>
  );
}
