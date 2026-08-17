import { AlertTriangle, Inbox, LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";

import { Button } from "./button";

/** 通用状态容器属性。 */
interface StateProps {
  /** 状态标题。 */
  title: string;
  /** 状态说明。 */
  description: string;
  /** 可选操作区域。 */
  action?: ReactNode;
}

/**
 * 渲染无数据状态。
 *
 * @param props 标题、说明和可选操作。
 */
export function EmptyState({ title, description, action }: StateProps) {
  return (
    <div className="state-panel">
      <Inbox aria-hidden="true" />
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

/** 错误状态属性。 */
export interface ErrorStateProps extends StateProps {
  /** 可选重试回调。 */
  onRetry?: () => void;
}

/**
 * 渲染不会吞掉上下文的可恢复错误状态。
 *
 * @param props 标题、说明和可选重试回调。
 */
export function ErrorState({ title, description, action, onRetry }: ErrorStateProps) {
  return (
    <div className="state-panel state-panel--error" role="alert">
      <AlertTriangle aria-hidden="true" />
      <h3>{title}</h3>
      <p>{description}</p>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          重试
        </Button>
      ) : (
        action
      )}
    </div>
  );
}

/**
 * 渲染带可访问状态文本的加载占位。
 *
 * @param label 当前加载动作说明。
 */
export function LoadingState({ label = "正在加载" }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <LoaderCircle className="spin" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

/**
 * 渲染不触发动画敏感问题的骨架占位。
 *
 * @param lines 占位行数，最少为一行。
 */
export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="skeleton" aria-hidden="true">
      {Array.from({ length: Math.max(1, lines) }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}
