import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";

import { ProtectedRoute } from "./protected-route";
import { AppShell } from "@/widgets/app-shell/app-shell";
import { LoadingState } from "@/shared/ui";

const DashboardPage = lazy(() => import("@/app/views/dashboard-page"));
const DesignSystemPage = lazy(() => import("@/app/views/design-system-page"));
const RuntimeWorkspacePage = lazy(() => import("@/app/views/runtime-workspace-page"));
const SignInPage = lazy(() => import("@/app/views/sign-in-page"));
const NotFoundPage = lazy(() => import("@/app/views/not-found-page"));

/** 为 Lazy 页面提供一致加载反馈。 */
function LazyBoundary({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<LoadingState label="正在加载页面" />}>{children}</Suspense>;
}

/** AgentArk Web 路由定义；业务页面在 Feature Phase 中按域继续拆分。 */
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      {
        path: "/design-system",
        element: (
          <LazyBoundary>
            <DesignSystemPage />
          </LazyBoundary>
        ),
      },
      {
        path: "/sign-in",
        element: (
          <LazyBoundary>
            <SignInPage />
          </LazyBoundary>
        ),
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            index: true,
            element: (
              <LazyBoundary>
                <DashboardPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/runtime",
            element: (
              <LazyBoundary>
                <RuntimeWorkspacePage />
              </LazyBoundary>
            ),
          },
        ],
      },
      {
        path: "*",
        element: (
          <LazyBoundary>
            <NotFoundPage />
          </LazyBoundary>
        ),
      },
    ],
  },
]);
