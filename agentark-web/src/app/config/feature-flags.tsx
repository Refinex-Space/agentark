import { createContext, useContext, type ReactNode } from "react";

/** Phase 17 可用的前端能力开关。 */
export interface FeatureFlags {
  /** 是否展示 Runtime 基础工作区入口。 */
  runtimeWorkspace: boolean;
  /** 是否展示实验性命令面板入口。 */
  commandPalette: boolean;
}

const flags: FeatureFlags = {
  runtimeWorkspace: import.meta.env.VITE_FEATURE_RUNTIME_WORKSPACE !== "false",
  commandPalette: import.meta.env.VITE_FEATURE_COMMAND_PALETTE !== "false",
};

const FeatureFlagContext = createContext<FeatureFlags>(flags);

/**
 * 提供构建时 Feature Flag；安全和授权逻辑不得依赖这些前端开关。
 *
 * @param children 应用内容。
 */
export function FeatureFlagProvider({ children }: { children: ReactNode }) {
  return <FeatureFlagContext.Provider value={flags}>{children}</FeatureFlagContext.Provider>;
}

/** 获取当前前端能力开关。 */
export function useFeatureFlags(): FeatureFlags {
  return useContext(FeatureFlagContext);
}
