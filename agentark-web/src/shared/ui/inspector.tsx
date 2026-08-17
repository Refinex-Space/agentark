import type { ReactNode } from "react";

/** Inspector 属性。 */
export interface InspectorProps {
  /** 面板标题。 */
  title: string;
  /** 面板辅助说明。 */
  description?: string;
  /** 面板详细内容。 */
  children: ReactNode;
}

/**
 * 渲染用于属性、契约或事件详情的稳定检查器容器。
 *
 * @param props 标题、说明和详细内容。
 */
export function Inspector({ title, description, children }: InspectorProps) {
  return (
    <div className="inspector">
      <header className="inspector__header">
        <p className="eyebrow">INSPECTOR</p>
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </header>
      <div className="inspector__body">{children}</div>
    </div>
  );
}
