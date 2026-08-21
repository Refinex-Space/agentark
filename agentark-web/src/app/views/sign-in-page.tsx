import { KeyRound, LoaderCircle } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { IdentityButton } from "@/features/authentication/ui/identity-button";
import {
  LoginField,
  LoginFieldDescription,
  LoginFieldGroup,
  LoginFieldSeparator,
  LoginForm,
} from "@/features/authentication/ui/login-form";

/** Route Guard 保存的登录前目标。 */
interface SignInLocationState {
  /** 登录完成后返回的仓库内路由。 */
  from?: string;
}

/** 从 JWT Payload 读取非敏感租户选择提示；签名与授权仍完全由服务端验证。 */
function tenantHint(token: string) {
  try {
    const payload = token.split(".")[1];
    if (!payload) return undefined;
    const claims = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))) as Record<
      string,
      unknown
    >;
    if (typeof claims.org_id !== "string") return undefined;
    return {
      organizationId: claims.org_id,
      ...(typeof claims.project_id === "string" ? { projectId: claims.project_id } : {}),
      ...(typeof claims.environment_id === "string"
        ? { environmentId: claims.environment_id }
        : {}),
    };
  } catch {
    return undefined;
  }
}

/** shadcn login-05 的居中单列表面。 */
function SignInShell({ children }: { children: ReactNode }) {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-6 bg-background p-6 text-foreground md:p-10">
      <div className="w-full max-w-sm">{children}</div>
    </main>
  );
}

/** 提供 Gateway BFF 登录入口、E2E 临时凭据和开发只读预览。 */
export default function SignInPage() {
  const {
    session,
    authenticate,
    beginLogin,
    signInWithPassword,
    completeRequiredPasswordChange,
    enterDevelopmentPreview,
  } = useAuthSession();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as SignInLocationState | null;
  const redirectStarted = useRef(false);
  const [formError, setFormError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const previewRequested =
    import.meta.env.DEV && new URLSearchParams(location.search).get("preview") === "1";
  const passwordChanged = new URLSearchParams(location.search).get("passwordChanged") === "1";

  useEffect(() => {
    if (
      session.status !== "anonymous" ||
      session.loginMode !== "OIDC" ||
      !session.loginEnabled ||
      previewRequested ||
      import.meta.env.MODE === "e2e" ||
      redirectStarted.current
    ) {
      return;
    }
    redirectStarted.current = true;
    beginLogin();
  }, [beginLogin, previewRequested, session]);

  /** 进入开发预览并返回原目标。 */
  const preview = (): void => {
    enterDevelopmentPreview();
    void navigate(state?.from ?? "/", { replace: true });
  };

  /** Test-only E2E 模式接收临时 Bearer；Production Build 会静态移除该分支。 */
  const e2eSignIn = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (import.meta.env.MODE !== "e2e") return;
    const value = new FormData(event.currentTarget).get("token");
    const token = typeof value === "string" ? value : "";
    const hint = tenantHint(token);
    authenticate(
      {
        subject: "e2e-operator",
        displayName: "E2E Operator",
        kind: "user",
        ...(hint ? { tenantSelection: hint } : {}),
      },
      token,
    );
    void navigate(state?.from ?? "/", { replace: true });
  };

  /** 由 login-05 主表单发起内置密码或外部 OIDC 登录。 */
  const signIn = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    setFormError(undefined);
    if (session.status !== "anonymous") return;
    if (session.loginMode === "OIDC") {
      beginLogin();
      return;
    }
    const data = new FormData(event.currentTarget);
    const rawUsernameOrEmail = data.get("usernameOrEmail");
    const rawPassword = data.get("password");
    const usernameOrEmail = typeof rawUsernameOrEmail === "string" ? rawUsernameOrEmail : "";
    const password = typeof rawPassword === "string" ? rawPassword : "";
    setSubmitting(true);
    void signInWithPassword(usernameOrEmail, password)
      .then((result) => {
        if (result === "AUTHENTICATED") {
          void navigate(state?.from ?? "/", { replace: true });
        }
      })
      .catch((error: unknown) => {
        setFormError(error instanceof Error ? error.message : "登录失败");
      })
      .finally(() => setSubmitting(false));
  };

  /** 完成首次强制改密；确认字段只在浏览器内比较。 */
  const changePassword = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    setFormError(undefined);
    const data = new FormData(event.currentTarget);
    const rawPassword = data.get("newPassword");
    const rawConfirmation = data.get("confirmPassword");
    const password = typeof rawPassword === "string" ? rawPassword : "";
    const confirmation = typeof rawConfirmation === "string" ? rawConfirmation : "";
    if (password !== confirmation) {
      setFormError("两次输入的新密码不一致");
      return;
    }
    setSubmitting(true);
    void completeRequiredPasswordChange(password)
      .then(() => void navigate(state?.from ?? "/", { replace: true }))
      .catch((error: unknown) => {
        setFormError(error instanceof Error ? error.message : "修改密码失败");
      })
      .finally(() => setSubmitting(false));
  };

  if (session.status === "authenticated") {
    return (
      <SignInShell>
        <LoginForm
          title="当前会话已建立"
          description={
            session.source === "bff"
              ? "登录凭据由 Gateway 服务端会话保管，不进入前端 JavaScript。"
              : "临时凭据只保留在当前页面内存中。"
          }
        >
          <LoginField>
            <IdentityButton
              className="w-full"
              onClick={() => void navigate(state?.from ?? "/", { replace: true })}
            >
              返回控制台
            </IdentityButton>
          </LoginField>
        </LoginForm>
      </SignInShell>
    );
  }

  if (session.status === "loading") {
    return (
      <SignInShell>
        <LoginForm
          aria-live="polite"
          title="正在恢复安全会话"
          description="正在向 Gateway 查询当前浏览器会话，不会读取浏览器持久凭据。"
        >
          <LoginField className="items-center">
            <LoaderCircle className="size-6 animate-spin text-foreground" aria-hidden="true" />
          </LoginField>
        </LoginForm>
      </SignInShell>
    );
  }

  if (session.status === "password-change-required") {
    return (
      <SignInShell>
        <LoginForm
          title="设置新的登录密码"
          description="首次登录必须替换随机临时密码，完成后才能进入后台。"
          footer="密码至少 15 个字符，支持空格和密码管理器，不要求固定字符组合。"
        >
          <form onSubmit={changePassword}>
            <LoginFieldGroup>
              <LoginField>
                <label className="font-medium text-sm" htmlFor="new-password">
                  新密码
                </label>
                <input
                  autoComplete="new-password"
                  className="h-10 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                  id="new-password"
                  minLength={15}
                  name="newPassword"
                  required
                  type="password"
                />
              </LoginField>
              <LoginField>
                <label className="font-medium text-sm" htmlFor="confirm-password">
                  确认新密码
                </label>
                <input
                  autoComplete="new-password"
                  className="h-10 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                  id="confirm-password"
                  minLength={15}
                  name="confirmPassword"
                  required
                  type="password"
                />
              </LoginField>
              {formError ? (
                <p className="text-center text-red-700 text-sm" role="alert">
                  {formError}
                </p>
              ) : null}
              <LoginField>
                <IdentityButton className="w-full" disabled={submitting} type="submit">
                  {submitting ? "正在保存" : "保存新密码并进入后台"}
                </IdentityButton>
              </LoginField>
            </LoginFieldGroup>
          </form>
        </LoginForm>
      </SignInShell>
    );
  }

  return (
    <SignInShell>
      <LoginForm
        title="登录 AgentArk"
        description={
          session.loginEnabled
            ? "使用你的 AgentArk 账号继续访问控制台。"
            : "当前环境尚未配置可用的身份登录入口。"
        }
        footer="密码只提交给 AgentArk Gateway，并以 Argon2id 摘要保存在独立 MySQL Schema；Token 不进入前端。"
      >
        {passwordChanged ? (
          <p
            className="mb-4 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-emerald-900 text-sm"
            role="status"
          >
            密码修改成功，全部旧会话已退出。请使用新密码重新登录。
          </p>
        ) : null}
        <form onSubmit={signIn}>
          <LoginFieldGroup>
            {session.loginMode === "PASSWORD" ? (
              <>
                <LoginField>
                  <label className="font-medium text-sm" htmlFor="username-or-email">
                    用户名或电子邮箱
                  </label>
                  <input
                    autoComplete="username"
                    autoFocus
                    className="h-10 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                    id="username-or-email"
                    maxLength={320}
                    name="usernameOrEmail"
                    required
                  />
                </LoginField>
                <LoginField>
                  <label className="font-medium text-sm" htmlFor="password">
                    密码
                  </label>
                  <input
                    autoComplete="current-password"
                    className="h-10 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                    id="password"
                    maxLength={128}
                    name="password"
                    required
                    type="password"
                  />
                </LoginField>
              </>
            ) : null}
            <LoginField>
              <IdentityButton
                className="w-full"
                disabled={!session.loginEnabled || submitting}
                title={session.loginEnabled ? undefined : "当前环境尚未启用身份登录"}
                type="submit"
              >
                <KeyRound data-icon="inline-start" />
                {submitting
                  ? "正在验证"
                  : session.loginMode === "PASSWORD"
                    ? "登录"
                    : session.loginEnabled
                      ? "继续登录"
                      : session.loginLabel}
              </IdentityButton>
            </LoginField>
            {formError ? (
              <p className="text-center text-red-700 text-sm" role="alert">
                {formError}
              </p>
            ) : null}
            <LoginFieldSeparator>安全登录</LoginFieldSeparator>
            <LoginFieldDescription className="text-center text-xs">
              登录后仅保存 HttpOnly 服务端会话，不向浏览器返回访问令牌。
            </LoginFieldDescription>
          </LoginFieldGroup>
        </form>
        {import.meta.env.MODE === "e2e" ? (
          <form onSubmit={e2eSignIn}>
            <LoginFieldGroup>
              <LoginField>
                <label className="grid gap-2 font-medium text-sm" htmlFor="e2e-token">
                  临时 E2E Bearer
                </label>
                <input
                  className="h-9 w-full rounded-md border border-input bg-background px-3 text-foreground outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                  id="e2e-token"
                  name="token"
                  type="password"
                  autoComplete="off"
                  required
                />
              </LoginField>
              <LoginField>
                <IdentityButton className="w-full" type="submit" variant="outline">
                  进入真实 API E2E
                </IdentityButton>
              </LoginField>
            </LoginFieldGroup>
          </form>
        ) : null}
        {previewRequested ? (
          <LoginField>
            <IdentityButton className="w-full" variant="outline" onClick={preview}>
              进入本地只读预览
            </IdentityButton>
          </LoginField>
        ) : null}
      </LoginForm>
    </SignInShell>
  );
}
