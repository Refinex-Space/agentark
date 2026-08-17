import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";
import { configDefaults, defineConfig } from "vitest/config";

/**
 * 配置 AgentArk Web 的开发代理、构建入口和单元测试环境。
 */
export default defineConfig({
  // React 负责编译 JSX，Tailwind v4 直接接入 Vite 构建管线。
  plugins: [react(), tailwindcss()],
  // 固定源码别名，避免 Feature 层依赖脆弱的深层相对路径。
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  // 本地请求只经 Gateway，前端不直连 Control、Runtime 或 Scheduler。
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  // Vitest 使用浏览器语义环境并加载公共断言与清理逻辑。
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    exclude: [...configDefaults.exclude, "e2e/**"],
    css: true,
    coverage: {
      reporter: ["text", "html"],
    },
  },
});
