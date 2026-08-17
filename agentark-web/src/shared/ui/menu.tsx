import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import type { ReactNode } from "react";

/** 单个菜单操作。 */
export interface MenuItem {
  /** 稳定菜单键。 */
  key: string;
  /** 用户可见操作名。 */
  label: string;
  /** 是否为破坏性操作。 */
  danger?: boolean;
  /** 选择操作时执行的同步回调。 */
  onSelect: () => void;
}

/** 设计系统操作菜单属性。 */
export interface ActionMenuProps {
  /** 打开菜单的触发器。 */
  trigger: ReactNode;
  /** 菜单操作集合。 */
  items: MenuItem[];
  /** 辅助技术使用的菜单标签。 */
  ariaLabel: string;
}

/**
 * 渲染支持方向键、Esc 与焦点恢复的操作菜单。
 *
 * @param props 菜单触发器、操作集合和标签。
 */
export function ActionMenu({ trigger, items, ariaLabel }: ActionMenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className="menu-content" sideOffset={8} aria-label={ariaLabel}>
          {items.map((item) => (
            <DropdownMenu.Item
              key={item.key}
              className="menu-item"
              data-danger={item.danger || undefined}
              onSelect={item.onSelect}
            >
              {item.label}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
