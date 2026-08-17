import {
  Boxes,
  CheckSquare2,
  ChevronsUpDown,
  Gauge,
  Hammer,
  LayoutDashboard,
  Moon,
  Palette,
  RadioTower,
  Rocket,
  Search,
  ScanSearch,
  ShieldCheck,
  Sun,
} from "lucide-react";
import { useEffect, type ReactNode } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";

import { useFeatureFlags } from "@/app/config/feature-flags";
import { useTheme, type ThemeMode } from "@/app/theme/theme-provider";
import { useAuthSession } from "@/entities/auth/model/auth-session";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import { cn } from "@/shared/lib/cn";
import { ActionMenu, Button, Popover, StatusBadge } from "@/shared/ui";
import { TenantBootstrap, TenantSwitcher } from "@/widgets/tenant-switcher/tenant-switcher";

/** 侧边导航项。 */
interface NavigationItem {
  /** 路由地址。 */
  to: string;
  /** 用户可见名称。 */
  label: string;
  /** 导航图标。 */
  icon: typeof LayoutDashboard;
  /** 是否要求对应 Feature Flag。 */
  feature?: "runtimeWorkspace";
}

const navigation: NavigationItem[] = [
  { to: "/", label: "总览", icon: LayoutDashboard },
  { to: "/build", label: "构建", icon: Hammer },
  { to: "/release", label: "发布", icon: Rocket },
  { to: "/runtime", label: "运行工作区", icon: RadioTower, feature: "runtimeWorkspace" },
  { to: "/approvals", label: "审批中心", icon: CheckSquare2 },
  { to: "/operate", label: "运行治理", icon: Gauge },
  { to: "/govern", label: "IAM 与租户", icon: ShieldCheck },
  { to: "/observe", label: "治理与观测", icon: ScanSearch },
  { to: "/design-system", label: "设计系统", icon: Palette },
];

/**
 * 渲染统一 Sidebar、Header、租户上下文和路由内容容器。
 */
export function AppShell() {
  const flags = useFeatureFlags();
  const { session, signOut } = useAuthSession();
  const { selection } = useTenant();
  const { resolved, setMode } = useTheme();
  const navigate = useNavigate();

  useEffect(() => {
    /**
     * 处理全局命令快捷键；输入框中仍允许浏览器默认行为。
     *
     * @param event 全局键盘事件。
     */
    const onKeyDown = (event: KeyboardEvent): void => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        document.getElementById("global-command-trigger")?.click();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const visibleNavigation = navigation.filter((item) => !item.feature || flags[item.feature]);
  const principalName =
    session.status === "authenticated" ? session.principal.displayName : "未登录";

  /** 更新主题并由 ThemeProvider 持久化非敏感偏好。 */
  const chooseTheme = (next: ThemeMode): void => setMode(next);

  return (
    <div className="app-shell">
      <TenantBootstrap />
      <a className="skip-link" href="#main-content">
        跳到主要内容
      </a>
      <aside className="sidebar" aria-label="控制台导航区">
        <Link className="brand" to="/" aria-label="AgentArk 控制台首页">
          <span className="brand__mark" aria-hidden="true">
            <Boxes size={21} />
          </span>
          <span>
            <strong>AgentArk</strong>
            <small>CONTROL CONSOLE</small>
          </span>
        </Link>

        <nav className="sidebar__nav" aria-label="主导航">
          <p className="sidebar__label">工作台</p>
          {visibleNavigation.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
                className={({ isActive }) => cn("nav-item", isActive && "nav-item--active")}
              >
                <Icon aria-hidden="true" size={18} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="sidebar__footer">
          <StatusBadge tone="success">系统基线就绪</StatusBadge>
          <p>Phase 19 · Governance</p>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <Popover
            ariaLabel="当前租户上下文"
            trigger={
              <Button variant="ghost" className="tenant-trigger">
                <span>
                  <small>当前上下文</small>
                  <strong>{selection.projectId ? "已选择项目" : "尚未选择项目"}</strong>
                </span>
                <ChevronsUpDown aria-hidden="true" size={16} />
              </Button>
            }
          >
            <div className="context-popover">
              <p className="eyebrow">TENANT CONTEXT</p>
              <strong>Organization / Project / Environment</strong>
              <TenantSwitcher />
            </div>
          </Popover>

          <div className="topbar__actions">
            {flags.commandPalette ? (
              <Popover
                ariaLabel="命令面板"
                trigger={
                  <Button
                    id="global-command-trigger"
                    variant="secondary"
                    className="command-trigger"
                  >
                    <Search aria-hidden="true" size={16} />
                    <span>搜索与命令</span>
                    <kbd>⌘ K</kbd>
                  </Button>
                }
              >
                <div className="command-palette">
                  <label>
                    <span className="sr-only">筛选命令</span>
                    <input autoFocus placeholder="跳转到产品区域…" />
                  </label>
                  <nav aria-label="快速跳转">
                    {visibleNavigation.map((item) => {
                      const Icon = item.icon;
                      return (
                        <Link key={item.to} to={item.to}>
                          <Icon aria-hidden="true" size={16} />
                          {item.label}
                        </Link>
                      );
                    })}
                  </nav>
                </div>
              </Popover>
            ) : null}
            <ActionMenu
              ariaLabel="主题设置"
              trigger={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`切换主题，当前${resolved === "dark" ? "深色" : "浅色"}`}
                >
                  {resolved === "dark" ? (
                    <Moon aria-hidden="true" size={18} />
                  ) : (
                    <Sun aria-hidden="true" size={18} />
                  )}
                </Button>
              }
              items={[
                { key: "light", label: "浅色", onSelect: () => chooseTheme("light") },
                { key: "dark", label: "深色", onSelect: () => chooseTheme("dark") },
                { key: "system", label: "跟随系统", onSelect: () => chooseTheme("system") },
              ]}
            />
            <ActionMenu
              ariaLabel="账户菜单"
              trigger={
                <Button variant="ghost" className="account-trigger">
                  <span className="avatar" aria-hidden="true">
                    {principalName.slice(0, 1).toUpperCase()}
                  </span>
                  <span>{principalName}</span>
                </Button>
              }
              items={
                session.status === "authenticated"
                  ? [
                      {
                        key: "sign-out",
                        label: session.principal.kind === "preview" ? "退出预览" : "退出登录",
                        onSelect: () => {
                          signOut();
                          void navigate("/sign-in");
                        },
                      },
                    ]
                  : [{ key: "sign-in", label: "登录", onSelect: () => void navigate("/sign-in") }]
              }
            />
          </div>
        </header>

        <main id="main-content" className="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** 页面标题容器属性。 */
export interface PageHeaderProps {
  /** 页面所属区域短标签。 */
  eyebrow: string;
  /** 页面标题。 */
  title: string;
  /** 页面一句话说明。 */
  description: string;
  /** 页面级操作。 */
  actions?: ReactNode;
}

/**
 * 渲染统一页面标题、说明和操作区。
 *
 * @param props 短标签、标题、说明和可选操作。
 */
export function PageHeader({ eyebrow, title, description, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions ? <div className="page-header__actions">{actions}</div> : null}
    </header>
  );
}
