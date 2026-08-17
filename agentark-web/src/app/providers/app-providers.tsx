import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

import { FeatureFlagProvider } from "@/app/config/feature-flags";
import { ThemeProvider } from "@/app/theme/theme-provider";
import { AuthSessionProvider } from "@/entities/auth/model/auth-session";
import { TenantProvider } from "@/entities/tenant/model/tenant-context";
import { ApiProblemError } from "@/shared/api/problem-detail";
import { ToastProvider, useToast } from "@/shared/ui";

/**
 * 创建并持有 Query Client，把未局部处理的请求错误路由到通知中心。
 *
 * @param children 应用内容。
 */
function QueryProvider({ children }: { children: ReactNode }) {
  const { notify } = useToast();
  const [client] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({
          onError: (error) => {
            notify({
              tone: "danger",
              title: "数据加载失败",
              description: error instanceof ApiProblemError ? error.message : "请稍后重试。",
            });
          },
        }),
        mutationCache: new MutationCache({
          onError: (error) => {
            notify({
              tone: "danger",
              title: "操作未完成",
              description: error instanceof ApiProblemError ? error.message : "请求未成功提交。",
            });
          },
        }),
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: (failureCount, error) =>
              !(error instanceof ApiProblemError && (error.problem.status ?? 500) < 500) &&
              failureCount < 2,
            refetchOnWindowFocus: false,
          },
          mutations: { retry: false },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

/**
 * 按稳定顺序装配主题、认证、租户、Feature Flag、通知和 Query Provider。
 *
 * @param children 应用内容。
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <FeatureFlagProvider>
        <AuthSessionProvider>
          <TenantProvider>
            <ToastProvider>
              <QueryProvider>{children}</QueryProvider>
            </ToastProvider>
          </TenantProvider>
        </AuthSessionProvider>
      </FeatureFlagProvider>
    </ThemeProvider>
  );
}
