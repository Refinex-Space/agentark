import { useQuery } from "@tanstack/react-query";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  listEnvironments,
  listOrganizations,
  listProjects,
} from "@/shared/api/generated/control/client";
import type { Environment, Organization, Project } from "@/shared/api/generated/control/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";

/** 查询当前主体可见 Organization。 */
export function useOrganizationsQuery() {
  const { session } = useAuthSession();
  const request = useApiRequest();
  return useQuery<Organization[]>({
    queryKey: ["iam", "organizations", session.status],
    enabled: session.status === "authenticated" && session.principal.kind !== "preview",
    queryFn: async () => unwrapGenerated<Organization[]>(await listOrganizations(request), [200]),
  });
}

/** 查询当前选择 Organization 下的 Project。 */
export function useProjectsQuery() {
  const { selection } = useTenant();
  const request = useApiRequest();
  return useQuery<Project[]>({
    queryKey: ["iam", "projects", selection.organizationId],
    enabled: Boolean(selection.organizationId && !selection.projectId),
    queryFn: async () =>
      unwrapGenerated<Project[]>(await listProjects(selection.organizationId!, request), [200]),
  });
}

/** 查询当前选择 Project 下的 Environment。 */
export function useEnvironmentsQuery() {
  const { selection } = useTenant();
  const request = useApiRequest();
  return useQuery<Environment[]>({
    queryKey: ["iam", "environments", selection.projectId],
    enabled: Boolean(selection.projectId),
    queryFn: async () =>
      unwrapGenerated<Environment[]>(await listEnvironments(selection.projectId!, request), [200]),
  });
}
