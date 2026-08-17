import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import { createRequestInit, generatedResponseError, type RequestMetadata } from "./http";

/** 生成客户端的最小公共响应形态。 */
export interface GeneratedResponse {
  /** HTTP 状态码。 */
  status: number;
  /** 成功模型或 ProblemDetail。 */
  data: unknown;
  /** 原始响应头。 */
  headers: Headers;
}

/**
 * 从生成客户端响应中提取成功模型，其他状态统一转为 ApiProblemError。
 *
 * @param response 生成客户端响应。
 * @param successStatuses 当前操作接受的成功状态集合。
 */
export function unwrapGenerated<T>(
  response: GeneratedResponse,
  successStatuses: readonly number[],
): T {
  if (!successStatuses.includes(response.status)) {
    throw generatedResponseError(response.status, response.data);
  }
  return response.data as T;
}

/**
 * 构造随当前内存凭据和租户选择变化的请求选项。
 *
 * @param metadata 可选乐观锁和幂等元数据。
 */
export function useApiRequest(metadata: RequestMetadata = {}): RequestInit {
  const { credentialProvider } = useAuthSession();
  const { selection } = useTenant();
  return createRequestInit(credentialProvider, selection, metadata);
}
