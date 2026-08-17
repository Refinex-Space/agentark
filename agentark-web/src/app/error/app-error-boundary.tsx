import { Component, type ErrorInfo, type ReactNode } from "react";

import { ApiProblemError } from "@/shared/api/problem-detail";
import { ErrorState } from "@/shared/ui";

/** 全局错误边界属性。 */
interface AppErrorBoundaryProps {
  /** 受保护的应用内容。 */
  children: ReactNode;
}

/** 全局错误边界状态。 */
interface AppErrorBoundaryState {
  /** 最近一次未被局部处理的异常。 */
  error: Error | null;
}

/** 捕获渲染异常并以稳定 Problem Detail 风格降级。 */
export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  /** 初始化为空错误状态。 */
  override state: AppErrorBoundaryState = { error: null };

  /**
   * 将渲染异常写入边界状态。
   *
   * @param error React 捕获的渲染异常。
   */
  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  /**
   * 在开发控制台记录非敏感错误摘要；不记录请求正文或凭据。
   *
   * @param error React 捕获的渲染异常。
   * @param info React 组件栈。
   */
  override componentDidCatch(error: Error, info: ErrorInfo): void {
    if (import.meta.env.DEV) {
      console.error("AgentArk Web 渲染失败", error.name, info.componentStack);
    }
  }

  /** 清除当前异常并重新渲染应用。 */
  private readonly reset = (): void => this.setState({ error: null });

  /** 渲染应用内容或安全降级页面。 */
  override render(): ReactNode {
    if (!this.state.error) {
      return this.props.children;
    }
    const error = this.state.error;
    const detail =
      error instanceof ApiProblemError
        ? `${error.problem.detail ?? error.message}${error.problem.traceId ? `（Trace ${error.problem.traceId}）` : ""}`
        : "页面发生未处理错误。敏感请求内容不会在此展示。";
    return (
      <main className="fatal-error">
        <ErrorState title="控制台暂时不可用" description={detail} onRetry={this.reset} />
      </main>
    );
  }
}
