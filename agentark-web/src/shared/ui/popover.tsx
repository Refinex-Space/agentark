import * as PopoverPrimitive from "@radix-ui/react-popover";
import type { ReactNode } from "react";

/** 设计系统 Popover 属性。 */
export interface PopoverProps {
  /** 打开浮层的触发器。 */
  trigger: ReactNode;
  /** 浮层内容。 */
  children: ReactNode;
  /** 辅助技术使用的内容标签。 */
  ariaLabel: string;
}

/**
 * 渲染自动处理焦点和碰撞位置的轻量浮层。
 *
 * @param props 触发器、内容和可访问标签。
 */
export function Popover({ trigger, children, ariaLabel }: PopoverProps) {
  return (
    <PopoverPrimitive.Root>
      <PopoverPrimitive.Trigger asChild>{trigger}</PopoverPrimitive.Trigger>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content className="popover-content" sideOffset={8} aria-label={ariaLabel}>
          {children}
          <PopoverPrimitive.Arrow className="popover-arrow" />
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
