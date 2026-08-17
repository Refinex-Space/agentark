import { CircleCheck, CircleDashed, CircleX, Clock3 } from "lucide-react";

import { cn } from "@/shared/lib/cn";

/** 时间线节点状态。 */
export type TimelineStatus = "pending" | "running" | "success" | "failure";

/** 时间线节点。 */
export interface TimelineItem {
  /** 稳定节点键。 */
  id: string;
  /** 节点标题。 */
  title: string;
  /** 可选细节。 */
  detail?: string;
  /** 节点状态。 */
  status: TimelineStatus;
  /** 可选时间文本。 */
  time?: string;
}

/** 时间线属性。 */
export interface TimelineProps {
  /** 按事实发生顺序排列的节点。 */
  items: TimelineItem[];
  /** 时间线的可访问标签。 */
  ariaLabel: string;
}

const icons = {
  pending: CircleDashed,
  running: Clock3,
  success: CircleCheck,
  failure: CircleX,
};

/**
 * 渲染带文本状态的运行时间线，不单独依赖颜色传达结果。
 *
 * @param props 时间线节点和标签。
 */
export function Timeline({ items, ariaLabel }: TimelineProps) {
  return (
    <ol className="timeline" aria-label={ariaLabel}>
      {items.map((item) => {
        const Icon = icons[item.status];
        return (
          <li key={item.id} className={cn("timeline__item", `timeline__item--${item.status}`)}>
            <Icon className="timeline__icon" aria-hidden="true" size={18} />
            <div className="timeline__content">
              <div className="timeline__heading">
                <strong>{item.title}</strong>
                {item.time ? <time>{item.time}</time> : null}
              </div>
              {item.detail ? <p>{item.detail}</p> : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
