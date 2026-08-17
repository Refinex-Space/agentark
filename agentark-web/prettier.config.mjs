/** @type {import("prettier").Config} */
const config = {
  // 统一手工维护的 TypeScript、样式和文档行宽。
  printWidth: 100,
  // 与仓库 Java 风格一致，避免无意义的 Tab 差异。
  tabWidth: 2,
  // 保留现代 JavaScript 的稳定尾逗号，降低增量 Diff。
  trailingComma: "all",
  // TypeScript 使用分号明确语句边界。
  semi: true,
  // JavaScript/TypeScript 字符串统一双引号。
  singleQuote: false,
};

export default config;
