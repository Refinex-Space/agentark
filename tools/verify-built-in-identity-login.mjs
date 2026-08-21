#!/usr/bin/env node

import { randomBytes } from "node:crypto";
import { chmodSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const passwordFile = resolve(root, "deploy/compose/.secrets/identity-user-password");
const baseUrl = "http://127.0.0.1:8080";

let cookie = "";

/** 从响应吸收最新 HttpOnly Session Cookie，不读取其他 Cookie。 */
function updateCookie(response) {
  const setCookie = response.headers.get("set-cookie");
  if (setCookie) cookie = setCookie.split(";", 1)[0];
}

/** 发起同源请求并维护 Session Cookie。 */
async function request(path, init = {}) {
  const headers = new Headers(init.headers);
  if (cookie) headers.set("Cookie", cookie);
  const response = await fetch(`${baseUrl}${path}`, { ...init, headers, redirect: "manual" });
  updateCookie(response);
  return response;
}

/** 加载当前 CSRF 参数并证明 PASSWORD 登录模式。 */
async function session() {
  const response = await request("/api/v1/auth/session", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`session endpoint returned ${response.status}`);
  const payload = await response.json();
  if (payload.loginMode !== "PASSWORD") throw new Error("built-in password login is not enabled");
  return payload;
}

/** 使用当前 CSRF 参数提交 JSON。 */
async function post(path, payload, csrf, extraHeaders = {}) {
  return request(path, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      [csrf.csrfHeaderName]: csrf.csrfToken,
      ...extraHeaders,
    },
    body: JSON.stringify(payload),
  });
}

const currentPassword = readFileSync(passwordFile, "utf8").trim();
let csrf = await session();
let login = await post(
  "/api/v1/auth/login",
  { usernameOrEmail: "agentark-admin", password: currentPassword },
  csrf,
);

if (login.status === 428) {
  const replacement = randomBytes(32).toString("hex");
  const changed = await post(
    "/api/v1/auth/required-password-change",
    { newPassword: replacement },
    csrf,
  );
  if (!changed.ok) throw new Error(`required password change returned ${changed.status}`);
  const temporaryFile = `${passwordFile}.next`;
  writeFileSync(temporaryFile, `${replacement}\n`, { mode: 0o600 });
  chmodSync(temporaryFile, 0o600);
  renameSync(temporaryFile, passwordFile);
  csrf = await session();
  login = { status: 200 };
}

if (login.status !== 200) throw new Error(`password login returned ${login.status}`);

const authenticated = await session();
if (!authenticated.authenticated || authenticated.principal?.subject == null) {
  throw new Error("authenticated session projection is missing");
}

const accounts = await request("/api/v1/identity/accounts", {
  headers: { Accept: "application/json" },
});
if (!accounts.ok) throw new Error(`identity account list returned ${accounts.status}`);
const accountList = await accounts.json();
if (!Array.isArray(accountList) || !accountList.some((item) => item.username === "agentark-admin")) {
  throw new Error("bootstrap administrator is missing from account management API");
}

console.log("built-in identity login verified: password change, Redis session and account API passed");
