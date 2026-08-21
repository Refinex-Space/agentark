import { Boxes } from "lucide-react";

import { cn } from "@/shared/lib/cn";

/** AgentArk 身份入口标志属性。 */
export interface AgentArkLogoProps {
  /** 覆盖标志尺寸的 Tailwind 类。 */
  className?: string;
  /** 是否展示 AgentArk 字标；图标模式仍保留屏幕阅读器名称。 */
  showWordmark?: boolean;
}

/**
 * 渲染 AgentArk 图标与可选字标，不迁入 shadcn 示例品牌。
 *
 * @param props 可选尺寸类与字标开关。
 */
export function AgentArkLogo({ className, showWordmark = true }: AgentArkLogoProps) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-foreground", className)}>
      <Boxes aria-hidden="true" className="h-full w-auto" strokeWidth={2} />
      {showWordmark ? (
        <strong className="text-[1em] font-semibold leading-none tracking-tight">AgentArk</strong>
      ) : (
        <span className="sr-only">AgentArk</span>
      )}
    </span>
  );
}
