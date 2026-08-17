import { createRequire } from "node:module";
import { expect, test } from "@playwright/test";

const require = createRequire(import.meta.url);
const axePath = require.resolve("axe-core/axe.min.js");

test("设计系统支持主题、键盘分栏和 Dialog 焦点恢复", async ({ page }) => {
  await page.goto("/design-system");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("AgentArk Interface Language");

  const themeButton = page.getByRole("button", { name: /切换主题/ });
  await themeButton.click();
  await page.getByRole("menuitem", { name: "深色" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  const separator = page.getByRole("separator", { name: "调整工作区和检查器宽度" });
  await separator.focus();
  await separator.press("ArrowLeft");
  await expect(separator).toHaveAttribute("aria-valuenow", "57");

  const dialogTrigger = page.getByRole("button", { name: "打开 Dialog" });
  await dialogTrigger.click();
  await expect(page.getByRole("dialog", { name: "发布不可变 Revision" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialogTrigger).toBeFocused();
});

test("设计系统在桌面和窄屏没有严重可访问性或横向溢出", async ({ page }) => {
  await page.goto("/design-system");
  await page.addScriptTag({ path: axePath });
  const seriousViolations = await page.evaluate(async () => {
    const axe = (
      window as unknown as Window & {
        axe: {
          run(): Promise<{ violations: Array<{ id: string; impact: string | null }> }>;
        };
      }
    ).axe;
    const result = await axe.run();
    return result.violations.filter(
      (violation) => violation.impact === "serious" || violation.impact === "critical",
    );
  });
  expect(seriousViolations).toEqual([]);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("navigation", { name: "主导航" })).toBeVisible();
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  expect(hasHorizontalOverflow).toBe(false);
});
