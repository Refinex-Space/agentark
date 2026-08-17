import { defineConfig, devices } from "@playwright/test";

const realBackend = process.env.AGENTARK_REAL_E2E === "1";

/**
 * 在 Chromium 中验证控制台基础导航、主题、键盘操作和视觉基线。
 */
export default defineConfig({
  // E2E 用例只放在独立目录，避免与 Vitest 单元测试混跑。
  testDir: "./e2e",
  // 普通生产构建与真实四服务链路分别运行，避免重复启动或把 Test Mode 混入基线。
  testIgnore: realBackend ? "**/web-foundation.spec.ts" : "**/real-product-flow.spec.ts",
  // CI 禁止残留 test.only，本地失败时保留追踪供定位。
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: realBackend ? "http://localhost:5173" : "http://127.0.0.1:4173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  // 默认复用生产 Preview；Phase 18 真实 E2E 同时启动临时后端和 e2e mode Vite。
  webServer: realBackend
    ? [
        {
          command: "node tools/e2e-stack.mjs",
          url: "http://127.0.0.1:8080/actuator/health",
          reuseExistingServer: false,
          timeout: 240_000,
        },
        {
          command: "vite --mode e2e --host 127.0.0.1 --port 5173",
          url: "http://localhost:5173",
          reuseExistingServer: false,
          timeout: 60_000,
        },
      ]
    : {
        command: "pnpm preview --host 127.0.0.1 --port 4173",
        url: "http://127.0.0.1:4173",
        reuseExistingServer: !process.env.CI,
      },
});
