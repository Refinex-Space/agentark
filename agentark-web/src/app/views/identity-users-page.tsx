import { KeyRound, LockOpen, ShieldCheck, UserPlus } from "lucide-react";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { Button, EmptyState, ProblemState, StatusBadge } from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** Gateway Identity 返回的账号安全视图。 */
interface IdentityAccount {
  /** 账号 UUIDv7。 */
  id: string;
  /** 用户名。 */
  username: string;
  /** 可空邮箱。 */
  email?: string;
  /** 展示名称。 */
  displayName: string;
  /** 生命周期状态。 */
  status: "ACTIVE" | "SUSPENDED" | "DISABLED";
  /** 是否必须首次改密。 */
  passwordChangeRequired: boolean;
  /** 可空锁定截止。 */
  lockedUntil?: string;
  /** 可空最近登录。 */
  lastLoginAt?: string;
  /** 平台权限。 */
  authorities: string[];
  /** 乐观锁版本。 */
  version: number;
}

/** 一次性临时密码响应。 */
interface CreatedIdentityAccount {
  /** 账号安全视图。 */
  account: IdentityAccount;
  /** 只在当前响应交付的临时密码。 */
  temporaryPassword: string | null;
}

/** 内置账号治理页面，密码摘要和 Token 永不进入响应。 */
export default function IdentityUsersPage() {
  const { credentialProvider, session } = useAuthSession();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<IdentityAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const [delivered, setDelivered] = useState<CreatedIdentityAccount>();

  /** 创建带当前 CSRF Header 的同源请求。 */
  const request = useCallback(
    async <T,>(path: string, init?: RequestInit): Promise<T> => {
      const headers = credentialProvider.getHeaders();
      new Headers(init?.headers).forEach((value, key) => headers.set(key, value));
      headers.set("Accept", "application/json");
      if (init?.body) headers.set("Content-Type", "application/json");
      const response = await fetch(path, {
        ...init,
        credentials: "same-origin",
        headers,
      });
      if (!response.ok) {
        throw new Error(`身份管理请求失败（${response.status}）`);
      }
      if (response.status === 204) return undefined as T;
      return (await response.json()) as T;
    },
    [credentialProvider],
  );

  /** 刷新账号列表。 */
  const load = useCallback(async (): Promise<void> => {
    setLoading(true);
    setError(undefined);
    try {
      setAccounts(await request<IdentityAccount[]>("/api/v1/identity/accounts"));
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : "账号列表加载失败");
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    let active = true;
    void request<IdentityAccount[]>("/api/v1/identity/accounts")
      .then((nextAccounts) => {
        if (active) setAccounts(nextAccounts);
      })
      .catch((nextError: unknown) => {
        if (active) {
          setError(nextError instanceof Error ? nextError.message : "账号列表加载失败");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [request]);

  /** 创建首次改密账号。 */
  const create = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setPending(true);
    setError(undefined);
    void request<CreatedIdentityAccount>("/api/v1/identity/accounts", {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({
        username: data.get("username"),
        email: data.get("email") || null,
        displayName: data.get("displayName"),
      }),
    })
      .then((created) => {
        setDelivered(created);
        form.reset();
        return load();
      })
      .catch((nextError: unknown) => {
        setError(nextError instanceof Error ? nextError.message : "账号创建失败");
      })
      .finally(() => setPending(false));
  };

  /** 改变账号状态。 */
  const updateStatus = (account: IdentityAccount, status: IdentityAccount["status"]): void => {
    setPending(true);
    void request<IdentityAccount>(`/api/v1/identity/accounts/${account.id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, expectedVersion: account.version }),
    })
      .then(() => load())
      .catch((nextError: unknown) => {
        setError(nextError instanceof Error ? nextError.message : "账号状态更新失败");
      })
      .finally(() => setPending(false));
  };

  /** 重置随机临时密码。 */
  const resetPassword = (account: IdentityAccount): void => {
    setPending(true);
    void request<CreatedIdentityAccount>(
      `/api/v1/identity/accounts/${account.id}/password-resets`,
      { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() } },
    )
      .then((created) => {
        setDelivered(created);
        return load();
      })
      .catch((nextError: unknown) => {
        setError(nextError instanceof Error ? nextError.message : "密码重置失败");
      })
      .finally(() => setPending(false));
  };

  /** 解除登录失败锁定。 */
  const unlock = (account: IdentityAccount): void => {
    setPending(true);
    void request<IdentityAccount>(`/api/v1/identity/accounts/${account.id}/unlock`, {
      method: "POST",
    })
      .then(() => load())
      .catch((nextError: unknown) => {
        setError(nextError instanceof Error ? nextError.message : "账号解锁失败");
      })
      .finally(() => setPending(false));
  };

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="BUILT-IN IDENTITY"
        title="用户与登录安全"
        description="账号和 Argon2id 摘要由 Gateway 独占 MySQL Schema 管理；租户成员与业务角色仍归 Control。"
      />

      {error ? <ProblemState error={new Error(error)} onRetry={() => void load()} /> : null}

      {delivered ? (
        <article className="panel border-amber-400 bg-amber-50">
          <header className="panel__header">
            <div>
              <p className="eyebrow">ONE-TIME DELIVERY</p>
              <h2>请立即交付临时密码</h2>
            </div>
            <KeyRound aria-hidden size={20} />
          </header>
          <p>
            用户：<strong>{delivered.account.username}</strong>
          </p>
          {delivered.temporaryPassword ? (
            <code className="block break-all rounded-md bg-white p-3 text-sm">
              {delivered.temporaryPassword}
            </code>
          ) : (
            <p className="muted-copy">该幂等操作已成功执行，临时密码不会再次返回。</p>
          )}
          <p className="muted-copy">关闭后无法再次读取；丢失时只能使用新幂等键重新重置。</p>
          <Button variant="secondary" onClick={() => setDelivered(undefined)}>
            已安全保存
          </Button>
        </article>
      ) : null}

      <form className="panel resource-form" onSubmit={create}>
        <header className="panel__header">
          <div>
            <p className="eyebrow">CREATE ACCOUNT</p>
            <h2>创建本地用户</h2>
          </div>
          <UserPlus aria-hidden size={20} />
        </header>
        <div className="resource-form__fields">
          <label>
            <span>用户名</span>
            <input name="username" pattern={"[a-z][a-z0-9._\\-]{2,63}"} required />
          </label>
          <label>
            <span>电子邮箱</span>
            <input name="email" type="email" />
          </label>
          <label>
            <span>展示名称</span>
            <input name="displayName" maxLength={128} required />
          </label>
        </div>
        <Button disabled={pending} type="submit">
          {pending ? "正在创建" : "创建并生成临时密码"}
        </Button>
      </form>

      {loading ? (
        <p className="muted-copy">正在加载账号…</p>
      ) : accounts.length === 0 ? (
        <EmptyState title="暂无本地账号" description="创建首个用户后将在这里管理登录状态。" />
      ) : (
        <div className="resource-grid">
          {accounts.map((account) => (
            <article className="panel" key={account.id}>
              <header className="panel__header">
                <div>
                  <p className="eyebrow">{account.username}</p>
                  <h3>{account.displayName}</h3>
                </div>
                <StatusBadge tone={account.status === "ACTIVE" ? "success" : "warning"}>
                  {account.status}
                </StatusBadge>
              </header>
              <p>{account.email || "未设置邮箱"}</p>
              <p className="muted-copy">
                {account.passwordChangeRequired ? "等待首次改密" : "密码已激活"}
                {account.lockedUntil ? ` · 锁定至 ${account.lockedUntil}` : ""}
              </p>
              <div className="button-row">
                {session.status === "authenticated" &&
                session.identityMode === "PASSWORD" &&
                session.principal.subject === account.id ? (
                  <Button
                    disabled={pending}
                    size="sm"
                    variant="secondary"
                    onClick={() => void navigate("/account/security")}
                  >
                    <KeyRound aria-hidden size={14} /> 修改密码
                  </Button>
                ) : null}
                {account.status === "ACTIVE" ? (
                  <Button
                    disabled={pending}
                    size="sm"
                    variant="secondary"
                    onClick={() => updateStatus(account, "SUSPENDED")}
                  >
                    暂停
                  </Button>
                ) : (
                  <Button
                    disabled={pending}
                    size="sm"
                    variant="secondary"
                    onClick={() => updateStatus(account, "ACTIVE")}
                  >
                    启用
                  </Button>
                )}
                <Button
                  disabled={pending}
                  size="sm"
                  variant="secondary"
                  onClick={() => resetPassword(account)}
                >
                  <KeyRound aria-hidden size={14} /> 重置密码
                </Button>
                {account.lockedUntil ? (
                  <Button
                    disabled={pending}
                    size="sm"
                    variant="secondary"
                    onClick={() => unlock(account)}
                  >
                    <LockOpen aria-hidden size={14} /> 解锁
                  </Button>
                ) : null}
              </div>
              <p className="muted-copy">
                <ShieldCheck aria-hidden size={14} /> 权限：
                {account.authorities.join("、") || "无平台权限"}
              </p>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
