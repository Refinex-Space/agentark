import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";

import { ProtectedRoute } from "./protected-route";
import { AppShell } from "@/widgets/app-shell/app-shell";
import { LoadingState } from "@/shared/ui";

const DashboardPage = lazy(() => import("@/app/views/dashboard-page"));
const DesignSystemPage = lazy(() => import("@/app/views/design-system-page"));
const RuntimeWorkspacePage = lazy(() => import("@/app/views/runtime-workspace-page"));
const GovernPage = lazy(() => import("@/features/govern/govern-page"));
const BuildPage = lazy(() => import("@/features/build/build-page"));
const ReleasePage = lazy(() => import("@/features/release/release-page"));
const ApprovalPage = lazy(() => import("@/features/approval/approval-page"));
const OperatePage = lazy(() => import("@/features/operate/operate-page"));
const ObservePage = lazy(() => import("@/features/observe/observe-page"));
const SignInPage = lazy(() => import("@/app/views/sign-in-page"));
const IdentityUsersPage = lazy(() => import("@/app/views/identity-users-page"));
const AccountSecurityPage = lazy(() => import("@/app/views/account-security-page"));
const NotFoundPage = lazy(() => import("@/app/views/not-found-page"));

/** 为 Lazy 页面提供一致加载反馈。 */
function LazyBoundary({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<LoadingState label="正在加载页面" />}>{children}</Suspense>;
}

/** AgentArk Web 路由定义；业务页面在 Feature Phase 中按域继续拆分。 */
export const router = createBrowserRouter([
  {
    path: "/sign-in",
    element: (
      <LazyBoundary>
        <SignInPage />
      </LazyBoundary>
    ),
  },
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
            path: "/build",
            element: (
              <LazyBoundary>
                <BuildPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/release",
            element: (
              <LazyBoundary>
                <ReleasePage />
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
          {
            path: "/approvals",
            element: (
              <LazyBoundary>
                <ApprovalPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/operate",
            element: (
              <LazyBoundary>
                <OperatePage />
              </LazyBoundary>
            ),
          },
          {
            path: "/govern",
            element: (
              <LazyBoundary>
                <GovernPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/govern/users",
            element: (
              <LazyBoundary>
                <IdentityUsersPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/account/security",
            element: (
              <LazyBoundary>
                <AccountSecurityPage />
              </LazyBoundary>
            ),
          },
          {
            path: "/observe",
            element: (
              <LazyBoundary>
                <ObservePage />
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
