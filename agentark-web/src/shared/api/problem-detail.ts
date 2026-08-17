import { z } from "zod";

const problemDetailSchema = z
  .object({
    type: z.string().optional(),
    title: z.string().optional(),
    status: z.number().int().optional(),
    detail: z.string().optional(),
    instance: z.string().optional(),
    code: z.string().optional(),
    requestId: z.string().optional(),
    traceId: z.string().optional(),
    violations: z
      .array(
        z.object({
          field: z.string().optional(),
          code: z.string().optional(),
          message: z.string().optional(),
        }),
      )
      .optional(),
  })
  .passthrough();

/** UI 层稳定消费的 RFC 9457 Problem Detail 视图。 */
export type ProblemDetail = z.infer<typeof problemDetailSchema>;

/**
 * 将未知响应体收敛为可安全展示的 Problem Detail。
 *
 * @param value 服务端或网络层返回的未知值。
 * @param fallbackStatus 无有效状态码时使用的 HTTP 状态。
 */
export function parseProblemDetail(value: unknown, fallbackStatus = 500): ProblemDetail {
  const parsed = problemDetailSchema.safeParse(value);
  if (parsed.success) {
    return {
      title: "请求处理失败",
      status: fallbackStatus,
      ...parsed.data,
    };
  }
  return {
    title: "请求处理失败",
    status: fallbackStatus,
    detail: "服务返回了无法识别的错误结构。",
  };
}

/** 保留 Problem Detail 上下文的前端请求异常。 */
export class ApiProblemError extends Error {
  /** 服务端返回或前端收敛后的 Problem Detail。 */
  readonly problem: ProblemDetail;

  /**
   * 创建可由 Error Boundary 和通知中心统一处理的请求异常。
   *
   * @param problem 已解析的稳定错误视图。
   */
  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? "请求处理失败");
    this.name = "ApiProblemError";
    this.problem = problem;
  }
}

/**
 * 从 Fetch Response 解析 Problem Detail，非 JSON 响应不会泄露原始正文。
 *
 * @param response 失败的 Fetch 响应。
 */
export async function problemFromResponse(response: Response): Promise<ApiProblemError> {
  const contentType = response.headers.get("content-type") ?? "";
  let body: unknown;
  if (contentType.includes("json")) {
    try {
      body = await response.json();
    } catch {
      body = undefined;
    }
  }
  return new ApiProblemError(parseProblemDetail(body, response.status));
}
