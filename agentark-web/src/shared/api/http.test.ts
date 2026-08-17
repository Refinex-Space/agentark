import { describe, expect, it } from "vitest";

import { createMemoryCredentialProvider, createRequestInit } from "./http";

describe("HTTP 请求安全基线", () => {
  it("只在内存中切换 Bearer 与 API Key，且两者不会同时发送", () => {
    const provider = createMemoryCredentialProvider();
    provider.setBearer("bearer-value");
    expect(provider.getHeaders().get("Authorization")).toBe("Bearer bearer-value");
    expect(provider.getHeaders().has("X-AgentArk-Api-Key")).toBe(false);

    provider.setApiKey("api-key-value");
    expect(provider.getHeaders().has("Authorization")).toBe(false);
    expect(provider.getHeaders().get("X-AgentArk-Api-Key")).toBe("api-key-value");

    provider.clear();
    expect([...provider.getHeaders()]).toHaveLength(0);
  });

  it("组合租户意图、If-Match 和幂等键，不把选择当作认证头", () => {
    const provider = createMemoryCredentialProvider();
    const request = createRequestInit(
      provider,
      {
        organizationId: "org-id",
        projectId: "project-id",
        environmentId: "environment-id",
      },
      { ifMatch: '"etag-1"', idempotencyKey: "request-1" },
    );
    const headers = new Headers(request.headers);
    expect(headers.get("X-AgentArk-Organization-Id")).toBe("org-id");
    expect(headers.get("X-AgentArk-Project-Id")).toBe("project-id");
    expect(headers.get("X-AgentArk-Environment-Id")).toBe("environment-id");
    expect(headers.get("If-Match")).toBe('"etag-1"');
    expect(headers.get("Idempotency-Key")).toBe("request-1");
    expect(headers.has("X-AgentArk-Authenticated-Project-Id")).toBe(false);
    expect(request.headers).not.toBeInstanceOf(Headers);
  });
});
