import eslint from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

/**
 * 对手工维护的 TypeScript/React 源码执行类型安全和 Hooks 规则。
 */
export default tseslint.config(
  // 构建输出、测试报告和机器生成 OpenAPI 类型不参与手工源码检查。
  {
    ignores: ["dist", "coverage", "playwright-report", "test-results", "src/shared/api/generated"],
  },
  eslint.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked.map((config) => ({
    ...config,
    files: ["**/*.{ts,tsx}"],
  })),
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.flat.recommended.rules,
      // Provider 与对应 Hook 同文件是本工程约定，不以导出形态破坏领域聚合。
      "react-refresh/only-export-components": "off",
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/no-floating-promises": "error",
      "@typescript-eslint/no-misused-promises": "error",
    },
  },
  {
    // 配置与测试允许 void Promise 回调，但仍禁止未处理的业务 Promise。
    files: ["**/*.test.{ts,tsx}", "e2e/**/*.ts", "playwright.config.ts", "vite.config.ts"],
    rules: {
      "@typescript-eslint/no-misused-promises": "off",
    },
  },
  {
    // Node.js 生成脚本显式只开放进程参数和受控控制台输出。
    files: ["tools/**/*.mjs"],
    languageOptions: {
      globals: {
        process: "readonly",
        console: "readonly",
      },
    },
  },
);
