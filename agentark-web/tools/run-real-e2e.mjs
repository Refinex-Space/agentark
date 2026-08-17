import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const stackFile = resolve("test-results/e2e-stack.json");
const sessionFile = resolve("test-results/e2e-session.json");

/** 只清理由 E2E Manifest 精确记录且名称符合随机测试前缀的资源。 */
async function cleanup() {
  if (!existsSync(stackFile)) return;
  let manifest;
  try {
    manifest = JSON.parse(readFileSync(stackFile, "utf8"));
  } catch {
    throw new Error("E2E cleanup manifest is invalid");
  }
  const pids = Array.isArray(manifest.pids)
    ? manifest.pids.filter((pid) => Number.isSafeInteger(pid) && pid > 1)
    : [];
  const containers = Array.isArray(manifest.containers)
    ? manifest.containers.filter(
        (name) =>
          typeof name === "string" && /^agentark-e2e-(mysql|redis)-[0-9]+-[0-9a-f]{8}$/.test(name),
      )
    : [];
  const logs = Array.isArray(manifest.logs)
    ? manifest.logs.filter(
        (path) => typeof path === "string" && path.includes("/agentark-web-e2e-"),
      )
    : [];
  if (logs.length > 0) {
    const sanitized = logs
      .filter(existsSync)
      .map((path) =>
        readFileSync(path, "utf8")
          .replace(/jdbc:mysql:\/\/\S+/gi, "jdbc:mysql://<redacted>")
          .replace(/(password|token|secret)(\s*[=:]\s*)\S+/gi, "$1$2<redacted>")
          .replace(/[A-Za-z0-9_-]{80,}/g, "<redacted-long-value>"),
      )
      .join("\n\n");
    mkdirSync(dirname(stackFile), { recursive: true });
    writeFileSync(resolve(dirname(stackFile), "e2e-backend.log"), sanitized, { mode: 0o600 });
  }
  for (const pid of pids) {
    try {
      process.kill(-pid, "SIGTERM");
    } catch {
      // 已退出的测试进程不需要再次终止。
    }
  }
  await new Promise((resolvePromise) => globalThis.setTimeout(resolvePromise, 1500));
  for (const pid of pids) {
    try {
      process.kill(-pid, "SIGKILL");
    } catch {
      // 已优雅退出时无需强制终止。
    }
  }
  for (const container of containers) {
    spawnSync("docker", ["rm", "-f", container], { stdio: "ignore" });
  }
  rmSync(stackFile, { force: true });
  rmSync(sessionFile, { force: true });
  if (
    typeof manifest.temporaryRoot === "string" &&
    manifest.temporaryRoot.includes("/agentark-web-e2e-")
  ) {
    rmSync(manifest.temporaryRoot, { recursive: true, force: true });
  }
}

await cleanup();
const result = spawnSync("playwright", ["test", ...process.argv.slice(2)], {
  stdio: "inherit",
  env: { ...process.env, AGENTARK_REAL_E2E: "1" },
});
await cleanup();
process.exit(result.status ?? 1);
