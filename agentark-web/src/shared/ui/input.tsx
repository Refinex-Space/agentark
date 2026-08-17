import type { InputHTMLAttributes } from "react";

import { cn } from "@/shared/lib/cn";

/** AgentArk 文本输入属性。 */
export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  /** 输入错误提示的元素 ID，用于建立 aria-describedby。 */
  errorId?: string;
}

/**
 * 渲染具有稳定 Focus、禁用和错误状态的文本输入。
 *
 * @param props 原生输入属性与可选错误关联。
 */
export function Input({ className, errorId, "aria-invalid": invalid, ...props }: InputProps) {
  return (
    <input
      className={cn("ui-input", className)}
      aria-invalid={invalid}
      aria-describedby={errorId}
      {...props}
    />
  );
}
