import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AuthSessionProvider, useAuthSession } from "./auth-session";

/** 展示认证状态与请求 Header，验证 BFF 不向前端注入 Bearer。 */
function SessionProbe() {
  const { session, credentialProvider } = useAuthSession();
  const headers = credentialProvider.getHeaders();
  return (
    <div>
      <span>{session.status}</span>
      <span>{session.status === "authenticated" ? session.principal.displayName : ""}</span>
      <span>{headers.get("Authorization") ?? ""}</span>
      <span>{headers.get("X-CSRF-TOKEN") ?? ""}</span>
    </div>
  );
}

/** 创建脱敏 Gateway BFF 会话响应。 */
function gatewaySession(authenticated: boolean, loginMode: "PASSWORD" | "OIDC" = "OIDC") {
  return {
    authenticated,
    loginEnabled: true,
    loginLabel: "使用本地账号登录",
    loginUri: loginMode === "PASSWORD" ? "/api/v1/auth/login" : "/oauth2/authorization/agentark",
    logoutUri: "/api/v1/auth/logout",
    loginMode,
    passwordChangeUri: loginMode === "PASSWORD" ? "/api/v1/auth/required-password-change" : null,
    csrfHeaderName: "X-CSRF-TOKEN",
    csrfParameterName: "_csrf",
    csrfToken: "csrf-token-value-123456",
    principal: authenticated
      ? {
          subject: "user-1",
          displayName: "refinex",
          issuer: "https://identity.example.test",
        }
      : null,
  };
}

/** 触发本人修改密码并展示刷新后的会话状态。 */
function PasswordChangeProbe() {
  const { session, changeOwnPassword } = useAuthSession();
  return (
    <div>
      <span>{session.status}</span>
      <button
        type="button"
        onClick={() => void changeOwnPassword("current password", "new password phrase")}
      >
        修改密码
      </button>
    </div>
  );
}

describe("AuthSessionProvider", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("从 Gateway 恢复 BFF 会话且只注入 CSRF Header", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(gatewaySession(true)), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthSessionProvider>
        <SessionProbe />
      </AuthSessionProvider>,
    );

    await waitFor(() => expect(screen.getByText("refinex")).toBeInTheDocument());
    expect(screen.getByText("authenticated")).toBeInTheDocument();
    expect(screen.queryByText(/^Bearer /)).not.toBeInTheDocument();
    expect(screen.getByText("csrf-token-value-123456")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/auth/session",
      expect.objectContaining({ credentials: "same-origin" }),
    );
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });

  it("Gateway 会话不可用时安全回到匿名状态", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));

    render(
      <AuthSessionProvider>
        <SessionProbe />
      </AuthSessionProvider>,
    );

    await waitFor(() => expect(screen.getByText("anonymous")).toBeInTheDocument());
  });

  it("本人修改密码后清除旧会话并刷新为匿名登录态", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify(gatewaySession(true, "PASSWORD")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify(gatewaySession(false, "PASSWORD")), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthSessionProvider>
        <PasswordChangeProbe />
      </AuthSessionProvider>,
    );

    await waitFor(() => expect(screen.getByText("authenticated")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    await waitFor(() => expect(screen.getByText("anonymous")).toBeInTheDocument());

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/identity/me/password-changes",
      expect.objectContaining({
        method: "POST",
        credentials: "same-origin",
        body: JSON.stringify({
          currentPassword: "current password",
          newPassword: "new password phrase",
        }),
      }),
    );
  });
});
