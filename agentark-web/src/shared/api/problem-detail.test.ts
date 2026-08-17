import { describe, expect, it } from "vitest";

import { ApiProblemError, parseProblemDetail } from "./problem-detail";

describe("Problem Detail", () => {
  it("保留稳定字段并允许服务端扩展字段", () => {
    const problem = parseProblemDetail({
      title: "禁止访问",
      status: 403,
      code: "IAM_FORBIDDEN",
      traceId: "abc",
      extension: "kept",
    });
    expect(problem).toMatchObject({ title: "禁止访问", status: 403, code: "IAM_FORBIDDEN" });
    expect(new ApiProblemError(problem).message).toBe("禁止访问");
  });

  it("未知响应不会把原始值直接展示给用户", () => {
    const problem = parseProblemDetail("upstream raw body", 502);
    expect(problem.status).toBe(502);
    expect(problem.detail).not.toContain("upstream raw body");
  });
});
