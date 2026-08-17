import { createHash, generateKeyPairSync, randomBytes, sign } from "node:crypto";
import { Buffer } from "node:buffer";
import {
  closeSync,
  mkdirSync,
  mkdtempSync,
  openSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";
import { createConnection } from "node:net";

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = resolve(webRoot, "..");
const sessionFile = resolve(webRoot, "test-results/e2e-session.json");
const stackFile = resolve(webRoot, "test-results/e2e-stack.json");
const temporaryRoot = mkdtempSync(`${tmpdir()}/agentark-web-e2e-`);
const suffix = `${process.pid}-${randomBytes(4).toString("hex")}`;
const mysqlContainer = `agentark-e2e-mysql-${suffix}`;
const redisContainer = `agentark-e2e-redis-${suffix}`;
const childProcesses = [];
const serviceLogs = [];
const containers = [];
let cleaning = false;

/** 写入不含凭据的精确进程与容器清单，供 Playwright 父进程兜底清理。 */
function updateStackManifest() {
  mkdirSync(dirname(stackFile), { recursive: true });
  writeFileSync(
    stackFile,
    JSON.stringify({
      pids: childProcesses.map((child) => child.pid).filter(Boolean),
      containers: [...containers],
      logs: [...serviceLogs],
      temporaryRoot,
    }),
    { mode: 0o600 },
  );
}

/** 执行无敏感输出的同步命令并在失败时保留命令名称。 */
function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd ?? repositoryRoot,
    encoding: "utf8",
    input: options.input,
    env: options.env ?? process.env,
    stdio: options.stdio ?? [options.input ? "pipe" : "ignore", "pipe", "pipe"],
  });
  if (result.status !== 0) {
    const dockerDetail =
      command === "docker"
        ? String(result.stderr ?? "")
            .replace(/'[^']*'/g, "'<redacted>'")
            .trim()
            .split("\n")[0]
        : "";
    throw new Error(
      `${options.label ?? command} failed during E2E stack setup${dockerDetail ? `: ${dockerDetail}` : ""}`,
    );
  }
  return result.stdout?.trim() ?? "";
}

/** 检查固定应用端口未被用户进程占用，不尝试终止现有进程。 */
async function requireFreePort(port) {
  await new Promise((resolvePromise, rejectPromise) => {
    const socket = createConnection({ host: "127.0.0.1", port });
    socket.once("connect", () => {
      socket.destroy();
      rejectPromise(new Error(`E2E required port ${port} is already in use`));
    });
    socket.once("error", () => resolvePromise());
  });
}

/** 等待 HTTP Health 成功，子进程提前退出时立即报告。 */
async function waitForHealth(url, child, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child?.exitCode !== null) {
      const tail = child.agentarkLogPath ? sanitizedLogTail(child.agentarkLogPath) : "";
      throw new Error(
        `E2E service exited before becoming healthy: ${url}${tail ? `\n${tail}` : ""}`,
      );
    }
    try {
      const response = await globalThis.fetch(url);
      if (response.ok) return;
    } catch {
      // 服务仍在启动，继续有界轮询。
    }
    await new Promise((resolvePromise) => globalThis.setTimeout(resolvePromise, 500));
  }
  throw new Error(`E2E service health timeout: ${url}`);
}

/** 返回已移除连接串、凭据和长 Token 候选的日志尾部。 */
function sanitizedLogTail(path) {
  try {
    return readFileSync(path, "utf8")
      .replace(/jdbc:mysql:\/\/\S+/gi, "jdbc:mysql://<redacted>")
      .replace(/(password|token|secret)(\s*[=:]\s*)\S+/gi, "$1$2<redacted>")
      .replace(/[A-Za-z0-9_-]{80,}/g, "<redacted-long-value>")
      .trim()
      .split("\n")
      .slice(-20)
      .join("\n");
  } catch {
    return "";
  }
}

/** 等待 Docker 内 MySQL 接受连接。 */
async function waitForMysql() {
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const result = spawnSync(
      "docker",
      ["exec", mysqlContainer, "mysql", "-uroot", "-e", "SELECT 1"],
      { stdio: "ignore" },
    );
    if (result.status === 0) return;
    await new Promise((resolvePromise) => globalThis.setTimeout(resolvePromise, 500));
  }
  throw new Error("E2E MySQL health timeout");
}

/** 从 Docker 动态端口映射中读取宿主端口。 */
function mappedPort(container, port) {
  const output = run("docker", ["port", container, `${port}/tcp`], {
    label: `read ${container} port mapping`,
  });
  const value = output.split("\n")[0]?.match(/:(\d+)$/)?.[1];
  if (!value) throw new Error(`Docker port mapping missing for ${container}`);
  return Number(value);
}

/** 使用 stdin 执行 MySQL，不把口令或 SQL 放入命令行。 */
function mysql(sql, database) {
  return run(
    "docker",
    [
      "exec",
      "-i",
      mysqlContainer,
      "mysql",
      "-uroot",
      "--batch",
      "--skip-column-names",
      ...(database ? [database] : []),
    ],
    { input: sql, label: "execute temporary MySQL statement" },
  );
}

/** 启动一个使用 Test Classpath 主类的真实 Spring Boot 服务。 */
function startService(module, mainClass, environment) {
  const logPath = resolve(temporaryRoot, `${module.split("/").at(-1)}.log`);
  const log = openSync(logPath, "w", 0o600);
  const child = spawn(
    resolve(repositoryRoot, "mvnw"),
    [
      "-q",
      "-pl",
      module,
      "-DskipTests",
      "-Dexec.classpathScope=test",
      `-Dexec.mainClass=${mainClass}`,
      "org.codehaus.mojo:exec-maven-plugin:3.6.3:java",
    ],
    {
      cwd: repositoryRoot,
      env: { ...process.env, ...environment },
      stdio: ["ignore", log, log],
      detached: true,
    },
  );
  child.agentarkLogPath = logPath;
  serviceLogs.push(logPath);
  closeSync(log);
  childProcesses.push(child);
  updateStackManifest();
  return child;
}

/** 生成符合 UUIDv7 版本与 Variant 位的测试标识。 */
function uuidV7() {
  const bytes = randomBytes(16);
  let timestamp = BigInt(Date.now());
  for (let index = 5; index >= 0; index -= 1) {
    bytes[index] = Number(timestamp & 0xffn);
    timestamp >>= 8n;
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 创建 RS256 JWT，Token 只写入权限为 0600 的忽略文件。 */
function jwt(privateKey, payload) {
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = sign("RSA-SHA256", Buffer.from(`${header}.${body}`), privateKey).toString(
    "base64url",
  );
  return `${header}.${body}.${signature}`;
}

/** 停止测试进程和临时容器，不删除用户现有 Compose 数据。 */
function cleanup() {
  if (cleaning) return;
  cleaning = true;
  for (const child of childProcesses.reverse()) {
    if (child.pid && child.exitCode === null) {
      try {
        process.kill(-child.pid, "SIGTERM");
      } catch {
        // 进程已退出时无需重复终止。
      }
    }
  }
  for (const container of containers.reverse()) {
    spawnSync("docker", ["rm", "-f", container], { stdio: "ignore" });
  }
  if (serviceLogs.length > 0) {
    mkdirSync(dirname(sessionFile), { recursive: true });
    writeFileSync(
      resolve(dirname(sessionFile), "e2e-backend.log"),
      serviceLogs.map((path) => sanitizedLogTail(path)).join("\n\n"),
      { mode: 0o600 },
    );
  }
  rmSync(sessionFile, { force: true });
  rmSync(temporaryRoot, { recursive: true, force: true });
}

process.once("SIGINT", () => {
  cleanup();
  process.exit(130);
});
process.once("SIGTERM", () => {
  cleanup();
  process.exit(143);
});
process.once("exit", cleanup);

try {
  for (const port of [8080, 8081, 8082, 8083]) await requireFreePort(port);
  run(
    resolve(repositoryRoot, "mvnw"),
    [
      "-q",
      "-pl",
      "agentark-services/agentark-gateway-server,agentark-services/agentark-control-server,agentark-services/agentark-runtime-server,agentark-services/agentark-scheduler-server",
      "-am",
      "-DskipTests",
      "install",
    ],
    { stdio: "inherit" },
  );

  const controlPassword = randomBytes(24).toString("hex");
  const runtimePassword = randomBytes(24).toString("hex");
  const schedulerPassword = randomBytes(24).toString("hex");
  const redisPassword = randomBytes(24).toString("hex");
  const redisConfig = resolve(temporaryRoot, "redis.conf");
  writeFileSync(redisConfig, `appendonly no\nrequirepass ${redisPassword}\n`, { mode: 0o600 });

  run(
    "docker",
    [
      "run",
      "-d",
      "--rm",
      "--name",
      mysqlContainer,
      "-p",
      "127.0.0.1::3306",
      "-e",
      "MYSQL_ALLOW_EMPTY_PASSWORD=yes",
      "mysql:8.4.11",
      "--default-time-zone=+00:00",
      "--log-bin-trust-function-creators=ON",
    ],
    { label: "start temporary MySQL" },
  );
  containers.push(mysqlContainer);
  updateStackManifest();
  await waitForMysql();
  const mysqlPort = mappedPort(mysqlContainer, 3306);
  mysql(`
    CREATE DATABASE agentark_control CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE DATABASE agentark_runtime CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE DATABASE agentark_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE USER 'agentark_control'@'%' IDENTIFIED BY '${controlPassword}';
    CREATE USER 'agentark_runtime'@'%' IDENTIFIED BY '${runtimePassword}';
    CREATE USER 'agentark_scheduler'@'%' IDENTIFIED BY '${schedulerPassword}';
    GRANT ALL PRIVILEGES ON agentark_control.* TO 'agentark_control'@'%';
    GRANT ALL PRIVILEGES ON agentark_runtime.* TO 'agentark_runtime'@'%';
    GRANT ALL PRIVILEGES ON agentark_scheduler.* TO 'agentark_scheduler'@'%';
  `);

  run(
    "docker",
    [
      "run",
      "-d",
      "--rm",
      "--name",
      redisContainer,
      "-p",
      "127.0.0.1::6379",
      "-v",
      `${redisConfig}:/usr/local/etc/redis/redis.conf:ro`,
      "redis:8.10.0",
      "redis-server",
      "/usr/local/etc/redis/redis.conf",
    ],
    { label: "start temporary Redis" },
  );
  containers.push(redisContainer);
  updateStackManifest();
  const redisPort = mappedPort(redisContainer, 6379);

  const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const publicKeyValue = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const issuer = "https://e2e.agentark.invalid";
  const commonSecurity = {
    SPRING_PROFILES_ACTIVE: "local",
    AGENTARK_E2E_PUBLIC_KEY: publicKeyValue,
    AGENTARK_E2E_ISSUER: issuer,
  };
  const jdbc = (schema) =>
    `jdbc:mysql://127.0.0.1:${mysqlPort}/${schema}?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8`;
  const control = startService(
    "agentark-services/agentark-control-server",
    "space.refinex.agentark.server.control.ControlE2eApplication",
    {
      ...commonSecurity,
      AGENTARK_CONTROL_DB_URL: jdbc("agentark_control"),
      AGENTARK_CONTROL_DB_USERNAME: "agentark_control",
      AGENTARK_CONTROL_DB_PASSWORD: controlPassword,
      AGENTARK_LOCAL_SECURITY_ENABLED: "true",
      AGENTARK_IAM_DEV_BOOTSTRAP_ENABLED: "true",
      AGENTARK_IAM_DEV_ISSUER: issuer,
      AGENTARK_IAM_DEV_SUBJECT: "e2e-operator",
      AGENTARK_IAM_DEV_ORGANIZATION_SLUG: "e2e-org",
      AGENTARK_IAM_DEV_PROJECT_SLUG: "e2e-project",
      AGENTARK_IAM_DEV_ENVIRONMENT_KEY: "e2e",
      AGENTARK_LOCAL_OBJECT_ROOT: resolve(temporaryRoot, "control-objects"),
    },
  );
  await waitForHealth("http://127.0.0.1:8081/actuator/health", control);

  const ids = mysql(`
    SELECT LOWER(BIN_TO_UUID(o.id)), LOWER(BIN_TO_UUID(p.id)), LOWER(BIN_TO_UUID(e.id))
    FROM agentark_control.organization o
    JOIN agentark_control.project p ON p.organization_id = o.id
    JOIN agentark_control.environment e ON e.project_id = p.id
    WHERE o.slug = 'e2e-org' AND p.slug = 'e2e-project' AND e.environment_key = 'e2e';
  `).split("\t");
  if (ids.length !== 3) throw new Error("E2E bootstrap tenant IDs are unavailable");
  const [organizationId, projectId, environmentId] = ids;
  const now = Math.floor(Date.now() / 1000);
  const audience = [
    "agentark-gateway",
    "agentark-control",
    "agentark-runtime",
    "agentark-scheduler",
  ];
  const userToken = jwt(privateKey, {
    iss: issuer,
    sub: "e2e-operator",
    aud: audience,
    iat: now - 5,
    exp: now + 3600,
    scope:
      "runtime:execute runtime:read runtime:cancel runtime:approve scheduler:read scheduler:manage scheduler:redrive",
    org_id: organizationId,
    project_id: projectId,
  });
  const platformToken = jwt(privateKey, {
    iss: issuer,
    sub: "e2e-operator",
    aud: audience,
    iat: now - 5,
    exp: now + 3600,
    scope: "organization:create",
  });
  const serviceToken = jwt(privateKey, {
    iss: issuer,
    sub: "e2e-runtime-service",
    aud: audience,
    iat: now - 5,
    exp: now + 3600,
    principal_type: "SERVICE",
    service_id: "agentark-e2e-runtime",
    scope: "runtime:execute runtime:read scheduler:manage",
    org_id: organizationId,
    project_id: projectId,
  });

  const runtime = startService(
    "agentark-services/agentark-runtime-server",
    "space.refinex.agentark.server.runtime.RuntimeE2eApplication",
    {
      ...commonSecurity,
      AGENTARK_RUNTIME_DB_URL: jdbc("agentark_runtime"),
      AGENTARK_RUNTIME_DB_USERNAME: "agentark_runtime",
      AGENTARK_RUNTIME_DB_PASSWORD: runtimePassword,
      AGENTARK_REDIS_HOST: "127.0.0.1",
      AGENTARK_REDIS_PORT: String(redisPort),
      AGENTARK_REDIS_PASSWORD: redisPassword,
      AGENTARK_RUNTIME_SECURITY_ENABLED: "true",
      AGENTARK_CONTROL_BASE_URL: "http://127.0.0.1:8081",
      AGENTARK_RUNTIME_INTERNAL_TOKEN: serviceToken,
      AGENTARK_RUNTIME_WORKER_ENABLED: "true",
      AGENTARK_RUNTIME_WORKER_POLL_DELAY: "100ms",
      AGENTARK_RUNTIME_HEARTBEAT_DELAY: "1s",
      AGENTARK_RUNTIME_OBJECT_ROOT: resolve(temporaryRoot, "runtime-objects"),
    },
  );
  await waitForHealth("http://127.0.0.1:8082/actuator/health", runtime);

  const scheduler = startService(
    "agentark-services/agentark-scheduler-server",
    "space.refinex.agentark.server.scheduler.SchedulerE2eApplication",
    {
      ...commonSecurity,
      AGENTARK_SCHEDULER_DB_URL: jdbc("agentark_scheduler"),
      AGENTARK_SCHEDULER_DB_USERNAME: "agentark_scheduler",
      AGENTARK_SCHEDULER_DB_PASSWORD: schedulerPassword,
      AGENTARK_SECURITY_ENABLED: "true",
      AGENTARK_CONTROL_BASE_URL: "http://127.0.0.1:8081",
      AGENTARK_RUNTIME_BASE_URL: "http://127.0.0.1:8082",
      AGENTARK_SCHEDULER_INTERNAL_TOKEN: serviceToken,
      AGENTARK_SCHEDULER_WORKER_ENABLED: "false",
    },
  );
  await waitForHealth("http://127.0.0.1:8083/actuator/health", scheduler);

  const attemptId = uuidV7();
  const jobId = uuidV7();
  const deadLetterId = uuidV7();
  const payloadHash = createHash("sha256").update("{}").digest("hex");
  mysql(`
    INSERT INTO agentark_scheduler.job
      (id, organization_id, project_id, type, business_key, payload_json, payload_hash,
       status, priority, available_at, retry_policy_json, idempotency_capability,
       current_attempt, current_fencing_token, error_code, created_at, updated_at)
    VALUES
      (UUID_TO_BIN('${jobId}'), UUID_TO_BIN('${organizationId}'), UUID_TO_BIN('${projectId}'),
       'OUTBOUND_WEBHOOK', 'e2e-dead-letter', JSON_OBJECT(), UNHEX('${payloadHash}'),
       'DEAD_LETTERED', 0, UTC_TIMESTAMP(6),
       JSON_OBJECT('maxAttempts', 1, 'initialBackoffMillis', 100, 'maxBackoffMillis', 100,
                   'multiplier', 1.0, 'jitterRatio', 0.0, 'timeoutMillis', 1000),
       'NONE', 1, 1, 'E2E_PROVIDER_FAILURE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
    INSERT INTO agentark_scheduler.job_attempt
      (id, job_id, attempt_number, owner, fencing_token, status, started_at, ended_at, error_code)
    VALUES
      (UUID_TO_BIN('${attemptId}'), UUID_TO_BIN('${jobId}'), 1, 'e2e-fixture', 1,
       'FAILED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'E2E_PROVIDER_FAILURE');
    INSERT INTO agentark_scheduler.dead_letter
      (id, job_id, final_attempt_id, reason, redrive_count, status, created_at, updated_at)
    VALUES
      (UUID_TO_BIN('${deadLetterId}'), UUID_TO_BIN('${jobId}'), UUID_TO_BIN('${attemptId}'),
       'E2E_PROVIDER_FAILURE', 0, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
  `);

  const gateway = startService(
    "agentark-services/agentark-gateway-server",
    "space.refinex.agentark.server.gateway.GatewayE2eApplication",
    {
      ...commonSecurity,
      AGENTARK_GATEWAY_SECURITY_ENABLED: "true",
      AGENTARK_CONTROL_BASE_URL: "http://127.0.0.1:8081",
      AGENTARK_RUNTIME_BASE_URL: "http://127.0.0.1:8082",
      AGENTARK_SCHEDULER_BASE_URL: "http://127.0.0.1:8083",
      AGENTARK_GATEWAY_RATE_LIMIT_ENABLED: "false",
    },
  );
  await waitForHealth("http://127.0.0.1:8080/actuator/health", gateway);

  mkdirSync(dirname(sessionFile), { recursive: true });
  writeFileSync(
    sessionFile,
    JSON.stringify({
      userToken,
      platformToken,
      organizationId,
      projectId,
      environmentId,
      seededJobId: jobId,
    }),
    { mode: 0o600 },
  );
  console.info("AgentArk real E2E backend is ready.");
  await new Promise(() => {});
} catch (error) {
  console.error(error instanceof Error ? error.message : "E2E stack failed");
  cleanup();
  process.exit(1);
}
