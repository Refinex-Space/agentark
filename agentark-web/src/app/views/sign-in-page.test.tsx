import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AuthSessionProvider } from "@/entities/auth/model/auth-session";

import SignInPage from "./sign-in-page";

describe("SignInPage", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            authenticated: false,
            loginEnabled: true,
            loginLabel: "使用本地账号登录",
            loginUri: "/oauth2/authorization/agentark",
            logoutUri: "/api/v1/auth/logout",
            loginMode: "OIDC",
            passwordChangeUri: null,
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfParameterName: "_csrf",
            csrfToken: "csrf-token-value-123456",
            principal: null,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
  });

  it("以 login-05 单列表面展示 BFF 入口且不在 React 收集账号密码", async () => {
    render(
      <MemoryRouter initialEntries={["/sign-in?preview=1"]}>
        <AuthSessionProvider>
          <SignInPage />
        </AuthSessionProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole("button", { name: "继续登录" })).toBeEnabled();
    expect(screen.getByRole("heading", { name: "登录 AgentArk" })).toBeInTheDocument();
    expect(screen.getByText(/Argon2id 摘要/)).toBeInTheDocument();
    expect(screen.getByText(/Token 不进入前端/)).toBeInTheDocument();
    expect(screen.getByRole("main").firstElementChild).toHaveClass("max-w-sm");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Apple|Google/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/生产环境必须配置 OIDC\/JWT/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Phase 17/)).not.toBeInTheDocument();
  });

  it("PASSWORD 模式直接展示用户名或邮箱和密码字段", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            authenticated: false,
            loginEnabled: true,
            loginLabel: "使用用户名或电子邮箱登录",
            loginUri: "/api/v1/auth/login",
            logoutUri: "/api/v1/auth/logout",
            loginMode: "PASSWORD",
            passwordChangeUri: "/api/v1/auth/required-password-change",
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfParameterName: "_csrf",
            csrfToken: "csrf-token-value-123456",
            principal: null,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={["/sign-in?preview=1"]}>
        <AuthSessionProvider>
          <SignInPage />
        </AuthSessionProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText("用户名或电子邮箱")).toBeVisible();
    expect(screen.getByLabelText("密码")).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "登录" })).toBeEnabled();
  });
});
