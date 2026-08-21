import type { ComponentProps, ReactNode } from "react";

import { cn } from "@/shared/lib/cn";

import { AgentArkLogo } from "./agentark-logo";

/** login-05 登录表单外壳属性。 */
export interface LoginFormProps extends ComponentProps<"div"> {
  /** 页面主标题。 */
  title: string;
  /** 主标题下方的流程说明。 */
  description: ReactNode;
  /** 表单后的安全边界说明。 */
  footer?: ReactNode;
}

/**
 * 按 shadcn login-05 的单列结构渲染 AgentArk 登录内容。
 *
 * @param props 标题、说明、字段内容与原生容器属性。
 */
export function LoginForm({
  className,
  title,
  description,
  footer,
  children,
  ...props
}: LoginFormProps) {
  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <div className="flex flex-col items-center gap-2 text-center">
        <AgentArkLogo className="h-8" showWordmark={false} />
        <h1 className="text-xl font-bold">{title}</h1>
        <LoginFieldDescription>{description}</LoginFieldDescription>
      </div>
      {children}
      {footer ? (
        <LoginFieldDescription className="px-6 text-center">{footer}</LoginFieldDescription>
      ) : null}
    </div>
  );
}

/** login-05 字段组属性。 */
export type LoginFieldGroupProps = ComponentProps<"div">;

/** 按 login-05 的垂直节奏组合登录字段。 */
export function LoginFieldGroup({ className, ...props }: LoginFieldGroupProps) {
  return (
    <div
      data-slot="field-group"
      className={cn("flex w-full flex-col gap-7", className)}
      {...props}
    />
  );
}

/** login-05 单个字段容器属性。 */
export type LoginFieldProps = ComponentProps<"div">;

/** 渲染垂直登录字段并保留分组语义。 */
export function LoginField({ className, ...props }: LoginFieldProps) {
  return (
    <div
      role="group"
      data-slot="field"
      className={cn("flex w-full flex-col gap-3", className)}
      {...props}
    />
  );
}

/** login-05 辅助说明属性。 */
export type LoginFieldDescriptionProps = ComponentProps<"p">;

/** 渲染登录页辅助说明。 */
export function LoginFieldDescription({ className, ...props }: LoginFieldDescriptionProps) {
  return (
    <p
      data-slot="field-description"
      className={cn("font-normal text-muted-foreground text-sm leading-normal", className)}
      {...props}
    />
  );
}

/** login-05 带文案分割线属性。 */
export type LoginFieldSeparatorProps = ComponentProps<"div">;

/** 渲染登录方式之间的居中文案分割线。 */
export function LoginFieldSeparator({ children, className, ...props }: LoginFieldSeparatorProps) {
  return (
    <div
      data-slot="field-separator"
      className={cn("-my-2 flex h-5 items-center text-sm", className)}
      {...props}
    >
      <span aria-hidden className="grow border-t" />
      <span className="px-2 text-muted-foreground">{children}</span>
      <span aria-hidden className="grow border-t" />
    </div>
  );
}
