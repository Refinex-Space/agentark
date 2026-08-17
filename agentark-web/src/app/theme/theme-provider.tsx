import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

/** 用户可选择的主题模式。 */
export type ThemeMode = "light" | "dark" | "system";

/** 主题上下文能力。 */
interface ThemeContextValue {
  /** 当前用户设置。 */
  mode: ThemeMode;
  /** 当前实际渲染主题。 */
  resolved: "light" | "dark";
  /** 更新主题设置。 */
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);
const storageKey = "agentark.theme";

/** 获取系统主题；服务端或测试环境默认使用浅色。 */
function systemTheme(): "light" | "dark" {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/**
 * 提供 Light/Dark/System 主题并只持久化非敏感外观偏好。
 *
 * @param children 应用内容。
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(() => {
    const stored = localStorage.getItem(storageKey);
    return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
  });
  const [system, setSystem] = useState(systemTheme);
  const resolved = mode === "system" ? system : mode;

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const listener = (): void => setSystem(media.matches ? "dark" : "light");
    media.addEventListener("change", listener);
    return () => media.removeEventListener("change", listener);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = resolved;
    document.documentElement.style.colorScheme = resolved;
    localStorage.setItem(storageKey, mode);
  }, [mode, resolved]);

  const value = useMemo(() => ({ mode, resolved, setMode }), [mode, resolved]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/** 获取主题上下文；必须在 ThemeProvider 内调用。 */
export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme 必须在 ThemeProvider 内使用");
  }
  return context;
}
