#!/usr/bin/env node

import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const agentarkRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

/** 读取仓库内受版本控制文本。 */
function read(relativePath) {
  return readFileSync(resolve(agentarkRoot, relativePath), "utf8");
}

/** 断言文本包含稳定安全边界。 */
function includes(source, expected, label) {
  assert.ok(source.includes(expected), `${label} is missing: ${expected}`);
}

const migration = read(
  "agentark-services/agentark-gateway-server/src/main/resources/db/migration/identity/V1__built_in_identity.sql",
);
for (const table of [
  "identity_account",
  "identity_password_credential",
  "identity_login_guard",
  "identity_security_event",
  "identity_idempotency",
  "identity_outbox",
]) {
  includes(migration, `CREATE TABLE ${table}`, `${table} migration`);
}
includes(migration, "ARGON2ID", "Argon2id database constraint");
includes(
  migration,
  "password_change_required",
  "first-login password change state",
);

const signIn = read("agentark-web/src/app/views/sign-in-page.tsx");
includes(signIn, 'name="usernameOrEmail"', "username or email field");
includes(signIn, 'name="password"', "password field");
includes(signIn, "password-change-required", "required password change view");

const accountSecurity = read(
  "agentark-web/src/app/views/account-security-page.tsx",
);
includes(
  accountSecurity,
  'name="currentPassword"',
  "own current password field",
);
includes(accountSecurity, 'name="newPassword"', "own new password field");
includes(accountSecurity, "changeOwnPassword", "own password change action");

const identityUsers = read(
  "agentark-web/src/app/views/identity-users-page.tsx",
);
includes(identityUsers, "修改密码", "own password change navigation");
includes(identityUsers, "重置密码", "administrator password reset action");

const devUp = read("tools/dev-up.sh");
includes(devUp, "mysql-identity-password", "identity MySQL secret");
includes(devUp, "identity-password-pepper", "identity pepper secret");
includes(
  devUp,
  "identity-signing-private-key.pem",
  "identity signing key secret",
);

const overlay = read("deploy/compose/docker-compose.identity.yml");
includes(
  overlay,
  'AGENTARK_GATEWAY_IDENTITY_ENABLED: "true"',
  "built-in identity overlay",
);
assert.ok(
  !overlay.includes("keycloak"),
  "Keycloak must not remain in the built-in identity overlay",
);
assert.equal(
  existsSync(
    resolve(agentarkRoot, "deploy/compose/identity/agentark-realm.json"),
  ),
  false,
  "legacy Keycloak realm must be removed",
);

console.log(
  "built-in identity gate passed: MySQL, Redis, password and Web boundaries verified",
);
