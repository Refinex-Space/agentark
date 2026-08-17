import axe from "axe-core";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import DesignSystemPage from "./design-system-page";
import { ToastProvider } from "@/shared/ui";

/** 渲染带通知上下文的设计系统页。 */
function renderPage() {
  return render(
    <ToastProvider>
      <DesignSystemPage />
    </ToastProvider>,
  );
}

describe("Design System 测试页", () => {
  it("覆盖核心组件和模态焦点恢复", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "AgentArk Interface Language",
    );
    const trigger = screen.getByRole("button", { name: "打开 Dialog" });
    await user.click(trigger);
    expect(screen.getByRole("dialog", { name: "发布不可变 Revision" })).toBeVisible();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "发布不可变 Revision" })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("没有 serious 或 critical 级别的基础可访问性问题", async () => {
    const { container } = renderPage();
    const results = await axe.run(container, {
      // jsdom 没有 Canvas，颜色对比度由真实 Chromium E2E 覆盖。
      rules: { "color-contrast": { enabled: false } },
    });
    expect(
      results.violations.filter(
        (violation) => violation.impact === "serious" || violation.impact === "critical",
      ),
    ).toEqual([]);
  });
});
