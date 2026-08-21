# shadcn login-05 登录入口迁入 QA

## 证据

- 生成命令：`npx shadcn@latest add login-05`，当日解析版本 `shadcn@4.18.0`
- Registry：`new-york-v4/login-05.json`，SHA-256 `2679ef5ce4e3967084fb18981ce39659c79b495ac873d2307905b8645d3d9a23`
- React 落点：`agentark-web/src/app/views/sign-in-page.tsx`、`src/features/authentication/ui/login-form.tsx`
- 首次改密落点：`agentark-web/src/app/views/sign-in-page.tsx`
- 用户治理落点：`agentark-web/src/app/views/identity-users-page.tsx`
- 密码只由原生表单瞬时读取并提交同源 Gateway，不进入 React State/Web Storage

## Findings

- CLI 直接应用会覆盖现有 `Button`/`Input`，并新增聚合 `radix-ui`；本次保留现有共享组件和锁文件。
- 示例 Apple/Google Provider 与条款链接未接入，不能原样保留。
- React 登录与首次改密共同使用 `login-05` 的居中 `max-w-sm`、品牌区和全宽主操作。
- 内置 Identity 由 `dev-up.sh` 默认启动，MySQL 保存 Argon2id 摘要，Redis 保存 HttpOnly Session；`--no-identity` 只用于纯 API 或外部 OIDC。
- 390px 与桌面视口通过真实 Chromium 核对布局、对比度和无横向溢出。

final result: built-in MySQL identity and login-05 password flow aligned
