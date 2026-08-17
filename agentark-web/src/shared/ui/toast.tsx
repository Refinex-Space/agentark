import * as ToastPrimitive from "@radix-ui/react-toast";
import { X } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

import { Button } from "./button";

/** 通知等级。 */
export type ToastTone = "info" | "success" | "warning" | "danger";

/** 待展示通知。 */
interface ToastMessage {
  /** 通知唯一键。 */
  id: string;
  /** 简短标题。 */
  title: string;
  /** 可选详细说明。 */
  description?: string;
  /** 通知语义等级。 */
  tone: ToastTone;
}

/** 通知中心调用契约。 */
interface ToastContextValue {
  /** 向通知中心追加一条限时消息。 */
  notify: (message: Omit<ToastMessage, "id">) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

/**
 * 为应用提供不会遮挡焦点的全局通知中心。
 *
 * @param children 应用内容。
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<ToastMessage[]>([]);
  const notify = useCallback((message: Omit<ToastMessage, "id">) => {
    setMessages((current) => [...current.slice(-3), { ...message, id: crypto.randomUUID() }]);
  }, []);
  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider swipeDirection="right">
        {children}
        {messages.map((message) => (
          <ToastPrimitive.Root
            key={message.id}
            className="toast-root"
            data-tone={message.tone}
            duration={5000}
            onOpenChange={(open) => {
              if (!open) {
                setMessages((current) => current.filter((item) => item.id !== message.id));
              }
            }}
          >
            <ToastPrimitive.Title>{message.title}</ToastPrimitive.Title>
            {message.description ? (
              <ToastPrimitive.Description>{message.description}</ToastPrimitive.Description>
            ) : null}
            <ToastPrimitive.Close asChild>
              <Button variant="ghost" size="icon" aria-label="关闭通知">
                <X aria-hidden="true" size={16} />
              </Button>
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        ))}
        <ToastPrimitive.Viewport className="toast-viewport" />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}

/** 获取通知中心；必须在 ToastProvider 内调用。 */
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast 必须在 ToastProvider 内使用");
  }
  return context;
}
