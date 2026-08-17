import { ApiProblemError, parseProblemDetail, problemFromResponse } from "./problem-detail";

/** 浏览器内存中的认证凭据提供器；不得把凭据写入持久存储。 */
export interface CredentialProvider {
  /** 返回当前请求所需认证头；无凭据时返回空 Headers。 */
  getHeaders(): Headers;
}

/** 表示前端选择意图的租户上下文，不替代后端授权。 */
export interface TenantSelection {
  /** 当前组织 UUIDv7。 */
  organizationId?: string;
  /** 当前项目 UUIDv7。 */
  projectId?: string;
  /** 当前环境 UUIDv7。 */
  environmentId?: string;
}

/** 需要注入到写请求的并发与幂等元数据。 */
export interface RequestMetadata {
  /** 乐观并发控制使用的实体 ETag。 */
  ifMatch?: string;
  /** 创建/命令请求使用的幂等键。 */
  idempotencyKey?: string;
}

/**
 * 创建只驻留内存的 Bearer/API Key 凭据容器。
 *
 * API Key 与 Token 不写入 localStorage、sessionStorage、日志或 URL。
 */
export function createMemoryCredentialProvider(): CredentialProvider & {
  /** 设置内存 Bearer Token，空值表示清除。 */
  setBearer(token?: string): void;
  /** 设置内存 API Key，空值表示清除。 */
  setApiKey(apiKey?: string): void;
  /** 清除所有内存凭据。 */
  clear(): void;
} {
  let bearer: string | undefined;
  let apiKey: string | undefined;
  return {
    getHeaders() {
      const headers = new Headers();
      if (bearer) {
        headers.set("Authorization", `Bearer ${bearer}`);
      } else if (apiKey) {
        headers.set("X-AgentArk-Api-Key", apiKey);
      }
      return headers;
    },
    setBearer(token) {
      bearer = token;
      apiKey = undefined;
    },
    setApiKey(value) {
      apiKey = value;
      bearer = undefined;
    },
    clear() {
      bearer = undefined;
      apiKey = undefined;
    },
  };
}

/**
 * 组合认证、租户选择、ETag 和幂等请求头。
 *
 * @param credentialProvider 只驻留内存的凭据来源。
 * @param tenant 当前租户选择意图。
 * @param metadata 可选并发和幂等元数据。
 */
export function createRequestInit(
  credentialProvider: CredentialProvider,
  tenant: TenantSelection = {},
  metadata: RequestMetadata = {},
): RequestInit {
  const headers = credentialProvider.getHeaders();
  if (tenant.organizationId) {
    headers.set("X-AgentArk-Organization-Id", tenant.organizationId);
  }
  if (tenant.projectId) {
    headers.set("X-AgentArk-Project-Id", tenant.projectId);
  }
  if (tenant.environmentId) {
    headers.set("X-AgentArk-Environment-Id", tenant.environmentId);
  }
  if (metadata.ifMatch) {
    headers.set("If-Match", metadata.ifMatch);
  }
  if (metadata.idempotencyKey) {
    headers.set("Idempotency-Key", metadata.idempotencyKey);
  }
  return { headers: Object.fromEntries(headers.entries()), credentials: "same-origin" };
}

/**
 * 执行需要前端自行控制响应体的公共 API 请求。
 *
 * @param input 同源 API URL 或 Request。
 * @param init Fetch 请求选项。
 */
export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, { ...init, credentials: init?.credentials ?? "same-origin" });
  if (!response.ok) {
    throw await problemFromResponse(response);
  }
  return response;
}

/**
 * 将生成客户端已解析的失败响应转为 UI 异常。
 *
 * @param status HTTP 状态码。
 * @param data 生成客户端返回的未知错误模型。
 */
export function generatedResponseError(status: number, data: unknown): ApiProblemError {
  return new ApiProblemError(parseProblemDetail(data, status));
}
