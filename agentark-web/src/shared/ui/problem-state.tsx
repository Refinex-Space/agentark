import { ShieldAlert } from "lucide-react";

import { ApiProblemError } from "@/shared/api/problem-detail";
import { Button } from "./button";

/** ProblemDetail 错误状态属性。 */
export interface ProblemStateProps {
  /** Query 或 Mutation 返回的未知错误。 */
  error: unknown;
  /** 可选重试动作。 */
  onRetry?: () => void;
}

/**
 * 展示稳定错误 Code、Trace ID 和安全 Detail，不输出响应原文或凭据。
 */
export function ProblemState({ error, onRetry }: ProblemStateProps) {
  const problem = error instanceof ApiProblemError ? error.problem : undefined;
  const denied = problem?.status === 401 || problem?.status === 403;
  return (
    <div className="problem-state" role="alert">
      <ShieldAlert aria-hidden="true" size={20} />
      <div>
        <strong>{denied ? "当前主体无权执行此操作" : (problem?.title ?? "请求未完成")}</strong>
        <p>{problem?.detail ?? "请检查服务状态后重试。"}</p>
        <dl>
          <div>
            <dt>Code</dt>
            <dd>{problem?.code ?? "CLIENT-UNCLASSIFIED"}</dd>
          </div>
          <div>
            <dt>Trace ID</dt>
            <dd>{problem?.traceId ?? "未提供"}</dd>
          </div>
        </dl>
      </div>
      {onRetry ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          重试
        </Button>
      ) : null}
    </div>
  );
}
