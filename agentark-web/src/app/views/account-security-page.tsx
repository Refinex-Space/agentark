import { KeyRound, LogOut } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { Button, EmptyState, ProblemState } from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** 已登录用户本人修改本地密码页面，密码不会进入 React State 或浏览器存储。 */
export default function AccountSecurityPage() {
  const { session, changeOwnPassword } = useAuthSession();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();

  /** 校验确认字段后提交当前密码和新密码；成功时全部会话失效。 */
  const submit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const rawCurrentPassword = data.get("currentPassword");
    const rawNewPassword = data.get("newPassword");
    const rawConfirmation = data.get("confirmPassword");
    const currentPassword = typeof rawCurrentPassword === "string" ? rawCurrentPassword : "";
    const newPassword = typeof rawNewPassword === "string" ? rawNewPassword : "";
    const confirmation = typeof rawConfirmation === "string" ? rawConfirmation : "";
    setError(undefined);
    if (newPassword !== confirmation) {
      setError("两次输入的新密码不一致");
      return;
    }
    setPending(true);
    void changeOwnPassword(currentPassword, newPassword)
      .then(() => {
        form.reset();
        void navigate("/sign-in?passwordChanged=1", { replace: true });
      })
      .catch((nextError: unknown) => {
        setError(nextError instanceof Error ? nextError.message : "密码修改失败");
      })
      .finally(() => setPending(false));
  };

  if (
    session.status !== "authenticated" ||
    session.source !== "bff" ||
    session.identityMode !== "PASSWORD"
  ) {
    return (
      <EmptyState
        title="当前身份不使用本地密码"
        description="组织身份、API 凭据和开发预览的密码由各自身份提供方管理。"
      />
    );
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="ACCOUNT SECURITY"
        title="修改我的密码"
        description="修改密码需要验证当前密码；管理员重置密码仍在“用户与登录”中独立执行。"
      />

      {error ? <ProblemState error={new Error(error)} /> : null}

      <form className="panel resource-form max-w-3xl" onSubmit={submit}>
        <header className="panel__header">
          <div>
            <p className="eyebrow">CHANGE PASSWORD</p>
            <h2>{session.principal.displayName}</h2>
          </div>
          <KeyRound aria-hidden size={20} />
        </header>

        <div className="resource-form__fields grid-cols-1">
          <label>
            <span>当前密码</span>
            <input
              autoComplete="current-password"
              name="currentPassword"
              type="password"
              maxLength={128}
              required
            />
          </label>
          <label>
            <span>新密码</span>
            <input
              autoComplete="new-password"
              name="newPassword"
              type="password"
              minLength={15}
              maxLength={128}
              required
            />
          </label>
          <label>
            <span>确认新密码</span>
            <input
              autoComplete="new-password"
              name="confirmPassword"
              type="password"
              minLength={15}
              maxLength={128}
              required
            />
          </label>
        </div>

        <p className="muted-copy">
          新密码长度为 15–128 个字符，不能与当前密码或最近使用过的密码相同。
        </p>
        <p className="muted-copy flex items-center gap-2">
          <LogOut aria-hidden size={16} /> 修改成功后将退出包括当前浏览器在内的全部登录会话。
        </p>

        <div className="button-row">
          <Button disabled={pending} type="submit">
            {pending ? "正在修改" : "确认修改密码"}
          </Button>
          <Button
            disabled={pending}
            type="button"
            variant="secondary"
            onClick={() => void navigate(-1)}
          >
            取消
          </Button>
        </div>
      </form>
    </div>
  );
}
