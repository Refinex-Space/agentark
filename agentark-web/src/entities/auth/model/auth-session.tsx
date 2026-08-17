import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

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

/** 认证会话状态。 */
export type AuthSession =
  { status: "anonymous" } | { status: "authenticated"; principal: AuthPrincipal };

/** 认证外壳公开能力。 */
interface AuthSessionContextValue {
  /** 当前认证会话。 */
  session: AuthSession;
  /** 只驻留内存的凭据提供器。 */
  credentialProvider: ReturnType<typeof createMemoryCredentialProvider>;
  /** 完成 OIDC/Bearer 认证，不持久化 Token。 */
  authenticate: (principal: AuthPrincipal, bearerToken?: string) => void;
  /** 仅开发模式进入无凭据只读预览。 */
  enterDevelopmentPreview: () => void;
  /** 清除凭据并退出当前会话。 */
  signOut: () => void;
}

const credentialProvider = createMemoryCredentialProvider();
const AuthSessionContext = createContext<AuthSessionContextValue | undefined>(undefined);

/**
 * 提供认证外壳和只驻留内存的凭据生命周期。
 *
 * @param children 应用内容。
 */
export function AuthSessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession>({ status: "anonymous" });

  const authenticate = useCallback((principal: AuthPrincipal, bearerToken?: string) => {
    if (bearerToken) {
      credentialProvider.setBearer(bearerToken);
    } else {
      credentialProvider.clear();
    }
    setSession({ status: "authenticated", principal });
  }, []);

  const enterDevelopmentPreview = useCallback(() => {
    if (!import.meta.env.DEV) {
      throw new Error("开发预览身份只能在 Vite Development 模式启用");
    }
    credentialProvider.clear();
    setSession({
      status: "authenticated",
      principal: { subject: "development-preview", displayName: "本地只读预览", kind: "preview" },
    });
  }, []);

  const signOut = useCallback(() => {
    credentialProvider.clear();
    setSession({ status: "anonymous" });
  }, []);

  const value = useMemo(
    () => ({ session, credentialProvider, authenticate, enterDevelopmentPreview, signOut }),
    [authenticate, enterDevelopmentPreview, session, signOut],
  );

  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>;
}

/** 获取认证会话；必须在 AuthSessionProvider 内调用。 */
export function useAuthSession(): AuthSessionContextValue {
  const context = useContext(AuthSessionContext);
  if (!context) {
    throw new Error("useAuthSession 必须在 AuthSessionProvider 内使用");
  }
  return context;
}
