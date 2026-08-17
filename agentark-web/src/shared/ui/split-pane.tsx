import { useId, useState, type ReactNode } from "react";

/** 可访问分栏属性。 */
export interface SplitPaneProps {
  /** 主工作区内容。 */
  primary: ReactNode;
  /** Inspector 等次要内容。 */
  secondary: ReactNode;
  /** 初始主区域百分比。 */
  initialPercent?: number;
  /** 次要区域的可访问名称。 */
  secondaryLabel: string;
}

/**
 * 渲染可用方向键调整的双栏工作区；窄屏自动堆叠。
 *
 * @param props 两个面板、初始比例和次要面板标签。
 */
export function SplitPane({
  primary,
  secondary,
  initialPercent = 62,
  secondaryLabel,
}: SplitPaneProps) {
  const [percent, setPercent] = useState(initialPercent);
  const secondaryId = useId();

  /**
   * 使用方向键微调分栏比例，并限制最小可用宽度。
   *
   * @param event 分隔条键盘事件。
   */
  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      setPercent((value) => Math.max(35, value - 5));
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      setPercent((value) => Math.min(75, value + 5));
    } else if (event.key === "Home") {
      event.preventDefault();
      setPercent(35);
    } else if (event.key === "End") {
      event.preventDefault();
      setPercent(75);
    }
  };

  return (
    <div
      className="split-pane"
      style={{ "--split-percent": `${String(percent)}%` } as React.CSSProperties}
    >
      <section className="split-pane__primary">{primary}</section>
      <div
        className="split-pane__handle"
        role="separator"
        tabIndex={0}
        aria-orientation="vertical"
        aria-valuemin={35}
        aria-valuemax={75}
        aria-valuenow={percent}
        aria-controls={secondaryId}
        aria-label="调整工作区和检查器宽度"
        onKeyDown={handleKeyDown}
      />
      <aside id={secondaryId} className="split-pane__secondary" aria-label={secondaryLabel}>
        {secondary}
      </aside>
    </div>
  );
}
