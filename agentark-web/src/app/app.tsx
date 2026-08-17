import { RouterProvider } from "react-router-dom";

import { AppErrorBoundary } from "@/app/error/app-error-boundary";
import { AppProviders } from "@/app/providers/app-providers";
import { router } from "@/app/router/router";

/**
 * 装配全局 Provider、错误边界和浏览器路由。
 */
export function App() {
  return (
    <AppErrorBoundary>
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>
    </AppErrorBoundary>
  );
}
