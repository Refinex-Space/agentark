import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ThemeProvider, useTheme } from "./theme-provider";

/** 暴露主题状态的测试组件。 */
function ThemeProbe() {
  const { mode, resolved, setMode } = useTheme();
  return (
    <button type="button" onClick={() => setMode("dark")}>
      {mode}/{resolved}
    </button>
  );
}

describe("ThemeProvider", () => {
  it("默认跟随系统，并只持久化非敏感主题偏好", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByRole("button")).toHaveTextContent("system/light");
    await user.click(screen.getByRole("button"));
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(localStorage.getItem("agentark.theme")).toBe("dark");
  });
});
