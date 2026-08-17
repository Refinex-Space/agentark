import { useQuery } from "@tanstack/react-query";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import { listOrganizations } from "@/shared/api/generated/control/client";
import type { Organization } from "@/shared/api/generated/control/models";
import { createRequestInit, generatedResponseError } from "@/shared/api/http";

/**
 * 通过生成客户端读取当前主体可见组织，并保持 Generated Model 在 Feature 边界内。
 */
export function useOrganizations() {
  const { credentialProvider, session } = useAuthSession();
  const { selection } = useTenant();
  return useQuery<Organization[]>({
    queryKey: ["organizations", session.status, selection.organizationId],
    enabled: session.status === "authenticated" && session.principal.kind !== "preview",
    queryFn: async () => {
      const response = await listOrganizations(createRequestInit(credentialProvider, selection));
      if (response.status !== 200) {
        throw generatedResponseError(response.status, response.data);
      }
      return response.data;
    },
  });
}
