import { KeyRound, LockKeyhole, ShieldCheck } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import type { FormEvent } from "react";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { Button, StatusBadge } from "@/shared/ui";

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

/** 认证外壳页面；真实 OIDC Redirect 配置归部署环境。 */
export default function SignInPage() {
  const { session, authenticate, enterDevelopmentPreview } = useAuthSession();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as SignInLocationState | null;

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

  if (session.status === "authenticated") {
    return (
      <section className="auth-page">
        <div className="auth-card">
          <StatusBadge tone="success">已认证</StatusBadge>
          <h1>当前会话已建立</h1>
          <p>凭据只保留在当前页面内存中。</p>
          <Button onClick={() => void navigate(state?.from ?? "/", { replace: true })}>
            返回控制台
          </Button>
        </div>
      </section>
    );
  }

  return (
    <section className="auth-page">
      <div className="auth-card">
        <span className="auth-card__icon" aria-hidden="true">
          <LockKeyhole size={24} />
        </span>
        <p className="eyebrow">SECURE ENTRY</p>
        <h1>登录 AgentArk</h1>
        <p>生产环境必须配置 OIDC/JWT。控制台不会将 Token 或 API Key 写入浏览器持久存储。</p>
        <div className="auth-assurances">
          <span>
            <ShieldCheck aria-hidden="true" size={16} />
            下游独立验证
          </span>
          <span>
            <KeyRound aria-hidden="true" size={16} />
            凭据仅驻内存
          </span>
        </div>
        <Button disabled title="尚未配置生产 OIDC Provider">
          使用组织身份登录
        </Button>
        {import.meta.env.MODE === "e2e" ? (
          <form className="e2e-auth-form" onSubmit={e2eSignIn}>
            <label>
              <span>临时 E2E Bearer</span>
              <input name="token" type="password" autoComplete="off" required />
            </label>
            <Button type="submit" variant="secondary">
              进入真实 API E2E
            </Button>
          </form>
        ) : null}
        {import.meta.env.DEV ? (
          <Button variant="secondary" onClick={preview}>
            进入本地只读预览
          </Button>
        ) : null}
        <small>Phase 17 只建立认证外壳，不提供默认生产用户或共享 Secret。</small>
      </div>
    </section>
  );
}
