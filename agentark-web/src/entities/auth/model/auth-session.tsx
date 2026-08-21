import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { createMemoryCredentialProvider } from "@/shared/api/http";
import type { TenantSelection } from "@/shared/api/http";

/** 已认证主体的最小前端投影。 */
export interface AuthPrincipal {
  /** 外部身份或 Service Account 的稳定主体标识。 */
  subject: string;
  /** 用户可见名称。 */
  displayName: string;
  /** 当前身份来源，仅用于 UI 提示。 */
  kind: "user" | "service" | "preview";
  /** 已验证 Token 中的非敏感租户选择提示，不替代服务端授权。 */
  tenantSelection?: TenantSelection;
}

/** Gateway 返回且不包含任何 Token 的 BFF 会话契约。 */
interface GatewaySessionResponse {
  /** 是否已经建立 OIDC 会话。 */
  authenticated: boolean;
  /** 当前部署是否允许发起登录。 */
  loginEnabled: boolean;
  /** 登录按钮用户文案。 */
  loginLabel: string;
  /** 同源 OIDC 登录入口。 */
  loginUri: string;
  /** 同源 OIDC 退出入口。 */
  logoutUri: string;
  /** 浏览器登录模式：PASSWORD 或 OIDC。 */
  loginMode: "PASSWORD" | "OIDC";
  /** PASSWORD 模式的强制改密入口。 */
  passwordChangeUri: string | null;
  /** CSRF Header 名称。 */
  csrfHeaderName: string;
  /** CSRF 表单字段名称。 */
  csrfParameterName: string;
  /** 当前服务端会话 CSRF Token。 */
  csrfToken: string;
  /** 已认证时存在的非敏感主体。 */
  principal: { subject: string; displayName: string; issuer: string } | null;
}

/** 浏览器认证会话状态。 */
export type AuthSession =
  | { status: "loading" }
  | {
      status: "anonymous";
      loginEnabled: boolean;
      loginLabel: string;
      loginUri?: string;
      loginMode: "PASSWORD" | "OIDC";
      passwordChangeUri?: string;
    }
  | {
      status: "password-change-required";
      passwordChangeUri: string;
    }
  | {
      status: "authenticated";
      principal: AuthPrincipal;
      source: "bff" | "bearer" | "preview";
      identityMode?: "PASSWORD" | "OIDC";
      logout?: {
        uri: string;
        csrfParameterName: string;
        csrfToken: string;
      };
    };

/** 认证会话公开能力。 */
interface AuthSessionContextValue {
  /** 当前认证会话。 */
  session: AuthSession;
  /** 内存 Bearer/API Key 或 BFF CSRF Header 提供器。 */
  credentialProvider: ReturnType<typeof createMemoryCredentialProvider>;
  /** 完成 E2E Bearer 认证，不持久化 Token。 */
  authenticate: (principal: AuthPrincipal, bearerToken?: string) => void;
  /** 发起由 Gateway 管理的 OIDC Authorization Code 登录。 */
  beginLogin: () => void;
  /** 使用内置用户名/邮箱和密码建立服务端 Session。 */
  signInWithPassword: (
    usernameOrEmail: string,
    password: string,
  ) => Promise<"AUTHENTICATED" | "PASSWORD_CHANGE_REQUIRED">;
  /** 完成首次强制改密并建立完整 Session。 */
  completeRequiredPasswordChange: (newPassword: string) => Promise<void>;
  /** 验证当前密码后修改本人密码，并使全部旧会话失效。 */
  changeOwnPassword: (currentPassword: string, newPassword: string) => Promise<void>;
  /** 仅开发模式进入无凭据只读预览。 */
  enterDevelopmentPreview: () => void;
  /** 退出当前会话；返回 true 表示调用方应执行本地路由跳转。 */
  signOut: () => boolean;
  /** 从 Gateway 重新加载不含 Token 的服务端会话。 */
  refreshSession: () => Promise<void>;
}

const credentialProvider = createMemoryCredentialProvider();
const AuthSessionContext = createContext<AuthSessionContextValue | undefined>(undefined);

/** 从 RFC 9457 响应提取安全错误说明，不回显请求字段。 */
async function problemDetail(response: Response, fallback: string): Promise<string> {
  try {
    const payload = (await response.json()) as { detail?: unknown };
    return typeof payload.detail === "string" && payload.detail.length > 0
      ? payload.detail
      : fallback;
  } catch {
    return fallback;
  }
}

/** 返回未配置 BFF 时的安全匿名状态。 */
function anonymousSession(): AuthSession {
  return {
    status: "anonymous",
    loginEnabled: false,
    loginLabel: "使用组织身份登录",
    loginMode: "OIDC",
  };
}

/** 判断 Gateway 会话响应是否包含前端所需的最小安全字段。 */
function isGatewaySession(value: unknown): value is GatewaySessionResponse {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return (
    typeof item.authenticated === "boolean" &&
    typeof item.loginEnabled === "boolean" &&
    typeof item.loginLabel === "string" &&
    typeof item.loginUri === "string" &&
    typeof item.logoutUri === "string" &&
    (item.loginMode === "PASSWORD" || item.loginMode === "OIDC") &&
    (item.passwordChangeUri === null || typeof item.passwordChangeUri === "string") &&
    typeof item.csrfHeaderName === "string" &&
    typeof item.csrfParameterName === "string" &&
    typeof item.csrfToken === "string"
  );
}

/**
 * 提供 BFF 会话恢复、E2E Bearer 和只读预览三种互斥认证状态。
 *
 * @param children 应用内容。
 */
export function AuthSessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession>({ status: "loading" });

  const refreshSession = useCallback(async (): Promise<void> => {
    credentialProvider.clear();
    try {
      const response = await fetch("/api/v1/auth/session", {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      });
      if (!response.ok) {
        setSession(anonymousSession());
        return;
      }
      const payload: unknown = await response.json();
      if (!isGatewaySession(payload)) {
        setSession(anonymousSession());
        return;
      }
      credentialProvider.setCsrf(payload.csrfHeaderName, payload.csrfToken);
      if (payload.authenticated && payload.principal) {
        setSession({
          status: "authenticated",
          source: "bff",
          identityMode: payload.loginMode,
          principal: {
            subject: payload.principal.subject,
            displayName: payload.principal.displayName,
            kind: "user",
          },
          logout: {
            uri: payload.logoutUri,
            csrfParameterName: payload.csrfParameterName,
            csrfToken: payload.csrfToken,
          },
        });
        return;
      }
      setSession({
        status: "anonymous",
        loginEnabled: payload.loginEnabled,
        loginLabel: payload.loginLabel,
        loginUri: payload.loginUri,
        loginMode: payload.loginMode,
        ...(payload.passwordChangeUri ? { passwordChangeUri: payload.passwordChangeUri } : {}),
      });
    } catch {
      setSession(anonymousSession());
    }
  }, []);

  useEffect(() => {
    const refreshTask = window.setTimeout(() => {
      void refreshSession();
    }, 0);
    return () => window.clearTimeout(refreshTask);
  }, [refreshSession]);

  const authenticate = useCallback((principal: AuthPrincipal, bearerToken?: string) => {
    credentialProvider.clear();
    if (bearerToken) {
      credentialProvider.setBearer(bearerToken);
    }
    setSession({ status: "authenticated", principal, source: "bearer" });
  }, []);

  const beginLogin = useCallback(() => {
    if (
      session.status !== "anonymous" ||
      session.loginMode !== "OIDC" ||
      !session.loginEnabled ||
      !session.loginUri
    ) {
      return;
    }
    window.location.assign(session.loginUri);
  }, [session]);

  const signInWithPassword = useCallback(
    async (
      usernameOrEmail: string,
      password: string,
    ): Promise<"AUTHENTICATED" | "PASSWORD_CHANGE_REQUIRED"> => {
      if (session.status !== "anonymous" || session.loginMode !== "PASSWORD" || !session.loginUri) {
        throw new Error("当前会话不接受账号密码登录");
      }
      const headers = credentialProvider.getHeaders();
      headers.set("Content-Type", "application/json");
      headers.set("Accept", "application/json");
      const response = await fetch(session.loginUri, {
        method: "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({ usernameOrEmail, password }),
      });
      if (response.status === 428) {
        if (!session.passwordChangeUri) {
          throw new Error("Gateway 未返回强制改密入口");
        }
        setSession({
          status: "password-change-required",
          passwordChangeUri: session.passwordChangeUri,
        });
        return "PASSWORD_CHANGE_REQUIRED";
      }
      if (!response.ok) {
        throw new Error("用户名、电子邮箱或密码错误");
      }
      await refreshSession();
      return "AUTHENTICATED";
    },
    [refreshSession, session],
  );

  const completeRequiredPasswordChange = useCallback(
    async (newPassword: string): Promise<void> => {
      if (session.status !== "password-change-required") {
        throw new Error("当前会话不需要修改密码");
      }
      const headers = credentialProvider.getHeaders();
      headers.set("Content-Type", "application/json");
      headers.set("Accept", "application/json");
      const response = await fetch(session.passwordChangeUri, {
        method: "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({ newPassword }),
      });
      if (!response.ok) {
        throw new Error("新密码不符合安全策略或改密会话已失效");
      }
      await refreshSession();
    },
    [refreshSession, session],
  );

  const changeOwnPassword = useCallback(
    async (currentPassword: string, newPassword: string): Promise<void> => {
      if (
        session.status !== "authenticated" ||
        session.source !== "bff" ||
        session.identityMode !== "PASSWORD"
      ) {
        throw new Error("当前身份不支持修改本地密码");
      }
      const headers = credentialProvider.getHeaders();
      headers.set("Content-Type", "application/json");
      headers.set("Accept", "application/json");
      const response = await fetch("/api/v1/identity/me/password-changes", {
        method: "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      if (!response.ok) {
        throw new Error(await problemDetail(response, "密码修改失败"));
      }
      credentialProvider.clear();
      await refreshSession();
    },
    [refreshSession, session],
  );

  const enterDevelopmentPreview = useCallback(() => {
    if (!import.meta.env.DEV) {
      throw new Error("开发预览身份只能在 Vite Development 模式启用");
    }
    credentialProvider.clear();
    setSession({
      status: "authenticated",
      source: "preview",
      principal: { subject: "development-preview", displayName: "本地只读预览", kind: "preview" },
    });
  }, []);

  const signOut = useCallback((): boolean => {
    credentialProvider.clear();
    if (session.status === "authenticated" && session.source === "bff" && session.logout) {
      const form = document.createElement("form");
      form.method = "post";
      form.action = session.logout.uri;
      const csrf = document.createElement("input");
      csrf.type = "hidden";
      csrf.name = session.logout.csrfParameterName;
      csrf.value = session.logout.csrfToken;
      form.append(csrf);
      document.body.append(form);
      form.submit();
      return false;
    }
    setSession(anonymousSession());
    return true;
  }, [session]);

  const value = useMemo(
    () => ({
      session,
      credentialProvider,
      authenticate,
      beginLogin,
      signInWithPassword,
      completeRequiredPasswordChange,
      changeOwnPassword,
      enterDevelopmentPreview,
      signOut,
      refreshSession,
    }),
    [
      authenticate,
      beginLogin,
      changeOwnPassword,
      completeRequiredPasswordChange,
      enterDevelopmentPreview,
      refreshSession,
      session,
      signInWithPassword,
      signOut,
    ],
  );

  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>;
}

/** 获取认证会话；必须在 AuthSessionProvider 内使用。 */
export function useAuthSession(): AuthSessionContextValue {
  const context = useContext(AuthSessionContext);
  if (!context) {
    throw new Error("useAuthSession 必须在 AuthSessionProvider 内使用");
  }
  return context;
}
