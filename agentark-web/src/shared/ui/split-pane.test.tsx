import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SplitPane } from "./split-pane";

describe("SplitPane", () => {
  it("允许使用方向键和 Home/End 调整面板比例", () => {
    render(
      <SplitPane
        primary={<div>主面板</div>}
        secondary={<div>检查器</div>}
        secondaryLabel="检查器"
      />,
    );
    const separator = screen.getByRole("separator", { name: "调整工作区和检查器宽度" });
    expect(separator).toHaveAttribute("aria-valuenow", "62");
    fireEvent.keyDown(separator, { key: "ArrowLeft" });
    expect(separator).toHaveAttribute("aria-valuenow", "57");
    fireEvent.keyDown(separator, { key: "Home" });
    expect(separator).toHaveAttribute("aria-valuenow", "35");
    fireEvent.keyDown(separator, { key: "End" });
    expect(separator).toHaveAttribute("aria-valuenow", "75");
  });
});
