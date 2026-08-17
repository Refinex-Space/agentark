import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const committedOutput = resolve(webRoot, "src/shared/api/generated");
const checkOnly = process.argv.includes("--check");
const temporaryRoot = checkOnly ? mkdtempSync(join(tmpdir(), "agentark-openapi-")) : undefined;
const generationOutput = temporaryRoot ?? committedOutput;

/**
 * 递归读取目录中的相对文件和内容，用于证明生成结果可重现。
 *
 * @param {string} root 待读取目录。
 * @param {string} current 当前递归目录。
 * @returns {Map<string, string>} 相对路径到文本内容的稳定映射。
 */
function readTree(root, current = root) {
  const result = new Map();
  if (!existsSync(current)) {
    return result;
  }
  for (const entry of readdirSync(current, { withFileTypes: true }).sort((a, b) =>
    a.name.localeCompare(b.name),
  )) {
    const absolutePath = join(current, entry.name);
    if (entry.isDirectory()) {
      for (const [path, content] of readTree(root, absolutePath)) {
        result.set(path, content);
      }
    } else {
      result.set(absolutePath.slice(root.length + 1), readFileSync(absolutePath, "utf8"));
    }
  }
  return result;
}

/**
 * 比较已提交目录与临时生成目录，发现缺失、冗余或内容漂移时终止。
 *
 * @param {string} expected 已提交的期望目录。
 * @param {string} actual 临时生成目录。
 */
function assertSameTree(expected, actual) {
  const expectedTree = readTree(expected);
  const actualTree = readTree(actual);
  const paths = new Set([...expectedTree.keys(), ...actualTree.keys()]);
  const differences = [...paths].filter((path) => expectedTree.get(path) !== actualTree.get(path));
  if (differences.length > 0) {
    throw new Error(`OpenAPI 生成结果存在漂移：${differences.join(", ")}`);
  }
}

try {
  if (!checkOnly && existsSync(committedOutput)) {
    // 只删除受控生成目录，确保已从契约移除的旧文件不会残留。
    rmSync(committedOutput, { recursive: true, force: true });
  }
  const result = spawnSync("orval", ["--config", "orval.config.ts", "--fail-on-warnings"], {
    cwd: webRoot,
    encoding: "utf8",
    stdio: "inherit",
    env: { ...process.env, AGENTARK_OPENAPI_OUTPUT_ROOT: generationOutput },
  });
  if (result.status !== 0) {
    throw new Error(`OpenAPI Client 生成失败，退出码 ${String(result.status)}`);
  }
  const formatResult = spawnSync(
    "prettier",
    ["--config", "prettier.config.mjs", "--ignore-path", "/dev/null", "--write", generationOutput],
    {
      cwd: webRoot,
      encoding: "utf8",
      stdio: "inherit",
    },
  );
  if (formatResult.status !== 0) {
    throw new Error(`OpenAPI Client 格式化失败，退出码 ${String(formatResult.status)}`);
  }
  if (temporaryRoot) {
    assertSameTree(committedOutput, temporaryRoot);
  }
  console.info(checkOnly ? "OpenAPI Client 无生成漂移。" : "OpenAPI Client 已生成。");
} finally {
  if (temporaryRoot) {
    rmSync(temporaryRoot, { recursive: true, force: true });
  }
}
