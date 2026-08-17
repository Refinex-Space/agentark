import { useEffect } from "react";

import {
  useEnvironmentsQuery,
  useOrganizationsQuery,
  useProjectsQuery,
} from "@/entities/tenant/api/tenant-queries";
import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import { Button, LoadingState } from "@/shared/ui";

/** 常驻 App Shell 的租户初始化器，Popover 关闭时仍可读取并选择首个可见范围。 */
export function TenantBootstrap() {
  const { session } = useAuthSession();
  const { selection, select } = useTenant();
  const organizations = useOrganizationsQuery();
  const projects = useProjectsQuery();
  const environments = useEnvironmentsQuery();

  useEffect(() => {
    if (
      session.status === "authenticated" &&
      session.principal.tenantSelection &&
      !selection.organizationId
    ) {
      select(session.principal.tenantSelection);
    }
  }, [select, selection.organizationId, session]);

  useEffect(() => {
    if (!selection.organizationId && organizations.data?.[0]) {
      select({ organizationId: organizations.data[0].id });
    }
  }, [organizations.data, select, selection.organizationId]);

  useEffect(() => {
    if (selection.organizationId && !selection.projectId && projects.data?.[0]) {
      select({ organizationId: selection.organizationId, projectId: projects.data[0].id });
    }
  }, [projects.data, select, selection.organizationId, selection.projectId]);

  useEffect(() => {
    if (selection.projectId && !selection.environmentId && environments.data?.[0]) {
      select({ ...selection, environmentId: environments.data[0].id });
    }
  }, [environments.data, select, selection, selection.environmentId, selection.projectId]);

  return null;
}

/**
 * 使用真实 IAM 列表完成 Organization、Project 和 Environment 切换。
 */
export function TenantSwitcher() {
  const { selection, select, clear } = useTenant();
  const organizations = useOrganizationsQuery();
  const projects = useProjectsQuery();
  const environments = useEnvironmentsQuery();

  useEffect(() => {
    if (!selection.organizationId && organizations.data?.[0]) {
      select({ organizationId: organizations.data[0].id });
    }
  }, [organizations.data, select, selection.organizationId]);

  useEffect(() => {
    if (selection.organizationId && !selection.projectId && projects.data?.[0]) {
      select({ organizationId: selection.organizationId, projectId: projects.data[0].id });
    }
  }, [projects.data, select, selection.organizationId, selection.projectId]);

  useEffect(() => {
    if (selection.projectId && !selection.environmentId && environments.data?.[0]) {
      select({ ...selection, environmentId: environments.data[0].id });
    }
  }, [environments.data, select, selection, selection.environmentId, selection.projectId]);

  if (organizations.isLoading) {
    return <LoadingState label="正在读取租户范围" />;
  }

  return (
    <div className="tenant-switcher">
      <label>
        <span>Organization</span>
        <select
          value={selection.organizationId ?? ""}
          onChange={(event) =>
            select(event.target.value ? { organizationId: event.target.value } : {})
          }
        >
          <option value="">选择 Organization</option>
          {organizations.data?.map((organization) => (
            <option key={organization.id} value={organization.id}>
              {organization.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Project</span>
        <select
          value={selection.projectId ?? ""}
          disabled={!selection.organizationId}
          onChange={(event) => {
            const organizationId = selection.organizationId;
            if (!organizationId) return;
            select(
              event.target.value
                ? { organizationId, projectId: event.target.value }
                : { organizationId },
            );
          }}
        >
          <option value="">选择 Project</option>
          {selection.projectId &&
          !projects.data?.some((project) => project.id === selection.projectId) ? (
            <option value={selection.projectId}>当前 Token Project</option>
          ) : null}
          {projects.data?.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Environment</span>
        <select
          value={selection.environmentId ?? ""}
          disabled={!selection.projectId}
          onChange={(event) =>
            select(
              event.target.value
                ? { ...selection, environmentId: event.target.value }
                : {
                    ...(selection.organizationId
                      ? { organizationId: selection.organizationId }
                      : {}),
                    ...(selection.projectId ? { projectId: selection.projectId } : {}),
                  },
            )
          }
        >
          <option value="">选择 Environment</option>
          {environments.data?.map((environment) => (
            <option key={environment.id} value={environment.id}>
              {environment.name}
            </option>
          ))}
        </select>
      </label>
      <p>选择只表示请求意图，服务端仍按 Principal 与资源归属授权。</p>
      <Button size="sm" variant="secondary" onClick={clear}>
        清除选择
      </Button>
    </div>
  );
}
