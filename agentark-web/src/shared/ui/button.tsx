import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ButtonHTMLAttributes } from "react";

import { cn } from "@/shared/lib/cn";

const buttonVariants = cva("ui-button", {
  variants: {
    variant: {
      primary: "ui-button--primary",
      secondary: "ui-button--secondary",
      ghost: "ui-button--ghost",
      danger: "ui-button--danger",
    },
    size: {
      sm: "ui-button--sm",
      md: "ui-button--md",
      lg: "ui-button--lg",
      icon: "ui-button--icon",
    },
  },
  defaultVariants: { variant: "primary", size: "md" },
});

/** AgentArk 按钮属性，支持语义变体、尺寸和 Radix Slot 组合。 */
export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  /** 将按钮语义与样式合并到唯一子元素，常用于路由链接。 */
  asChild?: boolean;
}

/**
 * 渲染具有统一焦点、禁用和状态样式的按钮。
 *
 * @param props 原生按钮属性与设计系统变体。
 */
export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Component = asChild ? Slot : "button";
  return <Component className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
