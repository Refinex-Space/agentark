import type { HTMLAttributes } from "react";

import { cn } from "@/shared/lib/cn";

/** 状态徽标的可穷举视觉语义。 */
export type StatusTone = "neutral" | "info" | "success" | "warning" | "danger";

/** 状态徽标属性。 */
export interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  /** 状态颜色语义，不直接接受任意颜色值。 */
  tone?: StatusTone;
}

/**
 * 渲染同时使用文本与颜色表达状态的紧凑徽标。
 *
 * @param props 状态语义和原生 Span 属性。
 */
export function StatusBadge({ tone = "neutral", className, ...props }: StatusBadgeProps) {
  return <span className={cn("status-badge", `status-badge--${tone}`, className)} {...props} />;
}
