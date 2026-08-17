import { defineConfig, devices } from "@playwright/test";

/**
 * 在 Chromium 中验证控制台基础导航、主题、键盘操作和视觉基线。
 */
export default defineConfig({
  // E2E 用例只放在独立目录，避免与 Vitest 单元测试混跑。
  testDir: "./e2e",
  // CI 禁止残留 test.only，本地失败时保留追踪供定位。
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://127.0.0.1:4173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  // 复用 Vite Preview，确保浏览器验证的是生产构建结果。
  webServer: {
    command: "pnpm preview --host 127.0.0.1 --port 4173",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !process.env.CI,
  },
});
