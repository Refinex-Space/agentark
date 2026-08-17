import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

import type { TenantSelection } from "@/shared/api/http";

/** 租户选择上下文能力。 */
interface TenantContextValue {
  /** 当前 Organization/Project/Environment 选择意图。 */
  selection: TenantSelection;
  /** 原子替换租户选择；服务端仍需独立授权。 */
  select: (next: TenantSelection) => void;
  /** 清除租户选择。 */
  clear: () => void;
}

const TenantContext = createContext<TenantContextValue | undefined>(undefined);

/**
 * 在浏览器内存中保存当前租户选择，不将 Header 视为授权事实。
 *
 * @param children 应用内容。
 */
export function TenantProvider({ children }: { children: ReactNode }) {
  const [selection, setSelection] = useState<TenantSelection>({});
  const value = useMemo(
    () => ({ selection, select: setSelection, clear: () => setSelection({}) }),
    [selection],
  );
  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

/** 获取租户选择上下文；必须在 TenantProvider 内调用。 */
export function useTenant(): TenantContextValue {
  const context = useContext(TenantContext);
  if (!context) {
    throw new Error("useTenant 必须在 TenantProvider 内使用");
  }
  return context;
}
