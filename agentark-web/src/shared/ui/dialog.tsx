import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";

import { Button } from "./button";

/** 设计系统对话框属性。 */
export interface DialogProps {
  /** 打开对话框的可聚焦触发器。 */
  trigger: ReactNode;
  /** 无歧义的对话框标题。 */
  title: string;
  /** 可选的上下文说明。 */
  description?: string;
  /** 对话框主体。 */
  children: ReactNode;
}

/**
 * 渲染带焦点圈定、Esc 关闭和可访问标题的模态对话框。
 *
 * @param props 触发器、标题、说明和内容。
 */
export function Dialog({ trigger, title, description, children }: DialogProps) {
  return (
    <DialogPrimitive.Root>
      <DialogPrimitive.Trigger asChild>{trigger}</DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content className="dialog-content">
          <div className="dialog-heading">
            <div>
              <DialogPrimitive.Title>{title}</DialogPrimitive.Title>
              {description ? (
                <DialogPrimitive.Description>{description}</DialogPrimitive.Description>
              ) : null}
            </div>
            <DialogPrimitive.Close asChild>
              <Button variant="ghost" size="icon" aria-label="关闭对话框">
                <X aria-hidden="true" size={18} />
              </Button>
            </DialogPrimitive.Close>
          </div>
          {children}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
