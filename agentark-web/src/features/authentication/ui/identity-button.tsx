import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ButtonHTMLAttributes } from "react";

import { cn } from "@/shared/lib/cn";

/** login-05 登录入口按钮变体。 */
const identityButtonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium outline-none transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary/90",
        ghost: "hover:bg-accent hover:text-accent-foreground",
        secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
        outline:
          "border border-input bg-background text-foreground shadow-xs hover:bg-accent hover:text-accent-foreground",
      },
      size: {
        default: "h-9 px-4 py-2 has-[>svg]:px-3",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  },
);

/** 身份入口按钮属性，对应 shadcn login-05 使用的 Button 变体。 */
export interface IdentityButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof identityButtonVariants> {
  /** 将按钮语义与样式合并到唯一子元素。 */
  asChild?: boolean;
}

/**
 * 渲染 login-05 使用的中性身份入口按钮，不复用控制台蓝色主按钮。
 *
 * @param props 原生按钮属性与变体。
 */
export function IdentityButton({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: IdentityButtonProps) {
  const Component = asChild ? Slot : "button";
  return (
    <Component className={cn(identityButtonVariants({ variant, size }), className)} {...props} />
  );
}
