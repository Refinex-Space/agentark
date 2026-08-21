import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAuthSession } from "@/entities/auth/model/auth-session";
import { LoadingState } from "@/shared/ui";

/**
 * 未认证时跳转登录页并保留原目标；前端 Guard 不替代服务端授权。
 */
export function ProtectedRoute() {
  const { session } = useAuthSession();
  const location = useLocation();
  if (session.status === "loading") {
    return <LoadingState label="正在恢复安全会话" />;
  }
  if (session.status !== "authenticated") {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
