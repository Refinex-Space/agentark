import * as TabsPrimitive from "@radix-ui/react-tabs";
import type { ReactNode } from "react";

/** 单个 Tab 的标题和内容。 */
export interface TabItem {
  /** 稳定 Tab 值。 */
  value: string;
  /** Tab 用户可见标题。 */
  label: string;
  /** Tab 面板内容。 */
  content: ReactNode;
}

/** 设计系统 Tabs 属性。 */
export interface TabsProps {
  /** 初始激活值。 */
  defaultValue: string;
  /** Tab 集合。 */
  items: TabItem[];
  /** Tab 列表标签。 */
  ariaLabel: string;
}

/**
 * 渲染支持方向键切换和明确面板关联的 Tabs。
 *
 * @param props 初始值、项目和可访问标签。
 */
export function Tabs({ defaultValue, items, ariaLabel }: TabsProps) {
  return (
    <TabsPrimitive.Root defaultValue={defaultValue}>
      <TabsPrimitive.List className="tabs-list" aria-label={ariaLabel}>
        {items.map((item) => (
          <TabsPrimitive.Trigger className="tabs-trigger" key={item.value} value={item.value}>
            {item.label}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>
      {items.map((item) => (
        <TabsPrimitive.Content className="tabs-content" key={item.value} value={item.value}>
          {item.content}
        </TabsPrimitive.Content>
      ))}
    </TabsPrimitive.Root>
  );
}
