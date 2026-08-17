import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, LockKeyhole, ShieldCheck, Users } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";

import {
  useEnvironmentsQuery,
  useOrganizationsQuery,
  useProjectsQuery,
} from "@/entities/tenant/api/tenant-queries";
import { useTenant } from "@/entities/tenant/model/tenant-context";
import {
  createApiKey,
  createEnvironment,
  createMembership,
  createOrganization,
  createProject,
  createRole,
  createRoleBinding,
  createSecretBinding,
  createSecretMetadata,
  createServiceAccount,
  listApiKeys,
  listMemberships,
  listPermissions,
  listRoleBindings,
  listRoles,
  listSecretBindings,
  listSecretMetadata,
  listServiceAccounts,
  revokeApiKey,
} from "@/shared/api/generated/control/client";
import type {
  ApiKeyView,
  CreatedApiKeyResponse,
  CursorPageSecretBinding,
  CursorPageSecretMetadata,
  Membership,
  Permission,
  Role,
  RoleBinding,
  ServiceAccount,
} from "@/shared/api/generated/control/models";
import { unwrapGenerated, useApiRequest } from "@/shared/api/generated-client";
import { Button, DataTable, EmptyState, ProblemState, StatusBadge, Tabs } from "@/shared/ui";
import { PageHeader } from "@/widgets/app-shell/app-shell";

/** 从提交表单读取字符串字段。 */
function field(form: HTMLFormElement, name: string): string {
  const value = new FormData(form).get(name);
  return typeof value === "string" ? value.trim() : "";
}

/** 通用紧凑资源表单属性。 */
interface ResourceFormProps {
  /** 表单标题。 */
  title: string;
  /** 表单控件。 */
  children: ReactNode;
  /** 提交动作。 */
  onSubmit: (form: HTMLFormElement) => Promise<void>;
  /** 是否正在提交。 */
  pending: boolean;
  /** 提交按钮文本。 */
  submitLabel: string;
}

/** 渲染不持久化输入值的真实 API 表单。 */
function ResourceForm({ title, children, onSubmit, pending, submitLabel }: ResourceFormProps) {
  const submit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const form = event.currentTarget;
    void onSubmit(form).then(() => form.reset());
  };
  return (
    <form className="resource-form" onSubmit={submit}>
      <h3>{title}</h3>
      <div className="resource-form__fields">{children}</div>
      <Button type="submit" size="sm" disabled={pending}>
        {pending ? "正在提交" : submitLabel}
      </Button>
    </form>
  );
}

/** Govern/IAM 核心页面，所有列表与写操作均调用真实 Control API。 */
export default function GovernPage() {
  const queryClient = useQueryClient();
  const request = useApiRequest();
  const { selection, select } = useTenant();
  const organizations = useOrganizationsQuery();
  const projects = useProjectsQuery();
  const environments = useEnvironmentsQuery();
  const projectId = selection.projectId;
  const environmentId = selection.environmentId;
  const enabled = Boolean(projectId);
  const [createdKey, setCreatedKey] = useState<CreatedApiKeyResponse | null>(null);

  const memberships = useQuery<Membership[]>({
    queryKey: ["iam", "memberships", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listMemberships(projectId!, request), [200]),
  });
  const roles = useQuery<Role[]>({
    queryKey: ["iam", "roles", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listRoles(projectId!, request), [200]),
  });
  const bindings = useQuery<RoleBinding[]>({
    queryKey: ["iam", "role-bindings", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listRoleBindings(projectId!, request), [200]),
  });
  const permissions = useQuery<Permission[]>({
    queryKey: ["iam", "permissions", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listPermissions(projectId!, request), [200]),
  });
  const serviceAccounts = useQuery<ServiceAccount[]>({
    queryKey: ["iam", "service-accounts", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listServiceAccounts(projectId!, request), [200]),
  });
  const apiKeys = useQuery<ApiKeyView[]>({
    queryKey: ["iam", "api-keys", projectId],
    enabled,
    queryFn: async () => unwrapGenerated(await listApiKeys(projectId!, request), [200]),
  });
  const secrets = useQuery<CursorPageSecretMetadata>({
    queryKey: ["secrets", projectId],
    enabled,
    queryFn: async () =>
      unwrapGenerated(await listSecretMetadata(projectId!, { limit: 100 }, request), [200]),
  });
  const secretBindings = useQuery<CursorPageSecretBinding>({
    queryKey: ["secret-bindings", projectId, environmentId],
    enabled: Boolean(projectId && environmentId),
    queryFn: async () =>
      unwrapGenerated(
        await listSecretBindings(projectId!, environmentId!, { limit: 100 }, request),
        [200],
      ),
  });

  const invalidate = async (...keys: string[]): Promise<void> => {
    await Promise.all(keys.map((key) => queryClient.invalidateQueries({ queryKey: [key] })));
  };

  const organizationCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createOrganization({ slug: field(form, "slug"), name: field(form, "name") }, request),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const projectCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createProject(
          selection.organizationId!,
          { slug: field(form, "slug"), name: field(form, "name") },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const environmentCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createEnvironment(
          projectId!,
          { key: field(form, "key"), name: field(form, "name") },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const membershipCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createMembership(
          projectId!,
          { principalKind: "USER", principalId: field(form, "principalId") },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const roleCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createRole(
          projectId!,
          {
            key: field(form, "key"),
            name: field(form, "name"),
            permissions: field(form, "permissions")
              .split(",")
              .map((value) => value.trim()),
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const bindingCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createRoleBinding(
          projectId!,
          {
            roleId: field(form, "roleId"),
            principalKind: "USER",
            principalId: field(form, "principalId"),
            scopeType: environmentId ? "ENVIRONMENT" : "PROJECT",
            scopeId: environmentId ?? projectId!,
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const serviceAccountCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createServiceAccount(projectId!, { name: field(form, "name") }, request),
        [201],
      ),
    onSuccess: async () => invalidate("iam"),
  });
  const apiKeyCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated<CreatedApiKeyResponse>(
        await createApiKey(
          projectId!,
          {
            serviceAccountId: field(form, "serviceAccountId"),
            name: field(form, "name"),
            scopes: field(form, "scopes")
              .split(",")
              .map((value) => value.trim()),
          },
          request,
        ),
        [201],
      ),
    onSuccess: async (value) => {
      setCreatedKey(value);
      await invalidate("iam");
    },
  });
  const secretCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createSecretMetadata(
          projectId!,
          {
            key: field(form, "key"),
            name: field(form, "name"),
            provider: "LOCAL_FILE",
            externalPath: field(form, "externalPath"),
            scope: environmentId ? "ENVIRONMENT" : "PROJECT",
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("secrets"),
  });
  const secretBindingCreate = useMutation({
    mutationFn: async (form: HTMLFormElement) =>
      unwrapGenerated(
        await createSecretBinding(
          projectId!,
          environmentId!,
          {
            secretMetadataId: field(form, "secretMetadataId"),
            bindingKey: field(form, "bindingKey"),
          },
          request,
        ),
        [201],
      ),
    onSuccess: async () => invalidate("secret-bindings"),
  });

  const noProject = !projectId ? (
    <EmptyState
      title="先选择 Project"
      description="Organization、Project 和 Environment 选择来自真实 IAM API。"
    />
  ) : null;

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="GOVERN / IAM"
        title="租户、身份与秘密引用"
        description="所有写操作由 Control 授权；Header 只表达当前选择，Secret 值永不回显。"
        actions={
          <StatusBadge tone={projectId ? "success" : "warning"}>
            {projectId ? "PROJECT SELECTED" : "SELECT CONTEXT"}
          </StatusBadge>
        }
      />
      <Tabs
        defaultValue="context"
        ariaLabel="Govern 资源分类"
        items={[
          {
            value: "context",
            label: "组织与环境",
            content: (
              <div className="resource-grid">
                <ResourceForm
                  title="创建 Organization"
                  pending={organizationCreate.isPending}
                  submitLabel="创建 Organization"
                  onSubmit={async (form) => {
                    await organizationCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Slug</span>
                    <input name="slug" required pattern="[a-z][a-z0-9-]{2,63}" />
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="创建 Project"
                  pending={projectCreate.isPending}
                  submitLabel="创建 Project"
                  onSubmit={async (form) => {
                    await projectCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Slug</span>
                    <input name="slug" required disabled={!selection.organizationId} />
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required disabled={!selection.organizationId} />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="创建 Environment"
                  pending={environmentCreate.isPending}
                  submitLabel="创建 Environment"
                  onSubmit={async (form) => {
                    await environmentCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Key</span>
                    <input name="key" required disabled={!projectId} />
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required disabled={!projectId} />
                  </label>
                </ResourceForm>
                <article className="panel resource-panel">
                  <h3>当前资源范围</h3>
                  <DataTable
                    caption="Organization 列表"
                    rows={organizations.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "Organization", render: (row) => row.name },
                      { key: "status", header: "状态", render: (row) => row.status },
                      {
                        key: "select",
                        header: "操作",
                        render: (row) => (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => select({ organizationId: row.id })}
                          >
                            选择
                          </Button>
                        ),
                      },
                    ]}
                  />
                  <DataTable
                    caption="Project 列表"
                    rows={projects.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "Project", render: (row) => row.name },
                      { key: "status", header: "状态", render: (row) => row.status },
                      {
                        key: "select",
                        header: "操作",
                        render: (row) => (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() =>
                              select({ organizationId: row.organizationId, projectId: row.id })
                            }
                          >
                            选择
                          </Button>
                        ),
                      },
                    ]}
                  />
                  <DataTable
                    caption="Environment 列表"
                    rows={environments.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "Environment", render: (row) => row.name },
                      { key: "status", header: "状态", render: (row) => row.status },
                      {
                        key: "select",
                        header: "操作",
                        render: (row) => (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => select({ ...selection, environmentId: row.id })}
                          >
                            选择
                          </Button>
                        ),
                      },
                    ]}
                  />
                </article>
              </div>
            ),
          },
          {
            value: "access",
            label: "成员与角色",
            content: noProject ?? (
              <div className="resource-grid">
                <ResourceForm
                  title="添加 Membership"
                  pending={membershipCreate.isPending}
                  submitLabel="添加成员"
                  onSubmit={async (form) => {
                    await membershipCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>User Identity UUIDv7</span>
                    <input name="principalId" required />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="创建 Custom Role"
                  pending={roleCreate.isPending}
                  submitLabel="创建角色"
                  onSubmit={async (form) => {
                    await roleCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Key</span>
                    <input name="key" required />
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required />
                  </label>
                  <label>
                    <span>权限（逗号分隔）</span>
                    <input name="permissions" required placeholder="agent:read,deployment:read" />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="创建 Role Binding"
                  pending={bindingCreate.isPending}
                  submitLabel="绑定角色"
                  onSubmit={async (form) => {
                    await bindingCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Role</span>
                    <select name="roleId" required>
                      {roles.data?.map((role) => (
                        <option key={role.id} value={role.id}>
                          {role.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>User Identity UUIDv7</span>
                    <input name="principalId" required />
                  </label>
                </ResourceForm>
                <article className="panel resource-panel">
                  <header className="panel__header">
                    <div>
                      <p className="eyebrow">ACCESS</p>
                      <h3>成员、角色与绑定</h3>
                    </div>
                    <Users size={18} />
                  </header>
                  {memberships.error ? (
                    <ProblemState
                      error={memberships.error}
                      onRetry={() => void memberships.refetch()}
                    />
                  ) : (
                    <DataTable
                      caption="Membership"
                      rows={memberships.data ?? []}
                      getRowKey={(row) => row.id}
                      columns={[
                        { key: "principal", header: "主体", render: (row) => row.principalId },
                        { key: "kind", header: "类型", render: (row) => row.principalKind },
                        { key: "status", header: "状态", render: (row) => row.status },
                      ]}
                    />
                  )}
                  <DataTable
                    caption="Role"
                    rows={roles.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "角色", render: (row) => row.name },
                      {
                        key: "permissions",
                        header: "权限",
                        render: (row) => row.permissionKeys.join(", "),
                      },
                      { key: "status", header: "状态", render: (row) => row.status },
                    ]}
                  />
                  <DataTable
                    caption="Role Binding"
                    rows={bindings.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "role", header: "Role ID", render: (row) => row.roleId },
                      { key: "principal", header: "主体", render: (row) => row.principalId },
                      {
                        key: "scope",
                        header: "Scope",
                        render: (row) => `${row.scopeType}:${row.scopeId}`,
                      },
                    ]}
                  />
                  <p className="muted-copy">
                    权限注册项：{permissions.data?.length ?? 0}
                    。列表拒绝和直接对象拒绝都由服务端执行，UI 隐藏不是授权。
                  </p>
                </article>
              </div>
            ),
          },
          {
            value: "credentials",
            label: "服务身份",
            content: noProject ?? (
              <div className="resource-grid">
                <ResourceForm
                  title="创建 Service Account"
                  pending={serviceAccountCreate.isPending}
                  submitLabel="创建账号"
                  onSubmit={async (form) => {
                    await serviceAccountCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>稳定名称</span>
                    <input name="name" required />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="创建 API Key"
                  pending={apiKeyCreate.isPending}
                  submitLabel="创建一次性 Key"
                  onSubmit={async (form) => {
                    await apiKeyCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Service Account</span>
                    <select name="serviceAccountId" required>
                      {serviceAccounts.data?.map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required />
                  </label>
                  <label>
                    <span>Scopes</span>
                    <input name="scopes" required placeholder="runtime:execute,runtime:read" />
                  </label>
                </ResourceForm>
                {createdKey ? (
                  <article className="one-time-secret" role="alert">
                    <KeyRound size={20} />
                    <div>
                      <strong>API Key 只展示这一次</strong>
                      <code>{createdKey.plaintext}</code>
                      <p>复制后立即关闭；页面不会持久化或再次查询明文。</p>
                    </div>
                    <Button size="sm" variant="secondary" onClick={() => setCreatedKey(null)}>
                      我已保存
                    </Button>
                  </article>
                ) : null}
                <article className="panel resource-panel">
                  <h3>Service Account / API Key</h3>
                  <DataTable
                    caption="服务账号"
                    rows={serviceAccounts.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "账号", render: (row) => row.name },
                      { key: "status", header: "状态", render: (row) => row.status },
                      { key: "id", header: "ID", render: (row) => row.id },
                    ]}
                  />
                  <DataTable
                    caption="API Key 元数据"
                    rows={apiKeys.data ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "名称", render: (row) => row.name },
                      { key: "prefix", header: "前缀", render: (row) => row.prefix },
                      {
                        key: "status",
                        header: "状态",
                        render: (row) => (row.revokedAt ? "REVOKED" : "ACTIVE"),
                      },
                      {
                        key: "revoke",
                        header: "操作",
                        render: (row) => (
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={Boolean(row.revokedAt)}
                            onClick={() =>
                              void revokeApiKey(
                                projectId!,
                                row.id,
                                { expectedVersion: row.version },
                                request,
                              ).then((response) => {
                                unwrapGenerated<void>(response, [204]);
                                return invalidate("iam");
                              })
                            }
                          >
                            吊销
                          </Button>
                        ),
                      },
                    ]}
                  />
                </article>
              </div>
            ),
          },
          {
            value: "secrets",
            label: "Secret 引用",
            content: noProject ?? (
              <div className="resource-grid">
                <ResourceForm
                  title="注册 Secret Metadata"
                  pending={secretCreate.isPending}
                  submitLabel="注册引用"
                  onSubmit={async (form) => {
                    await secretCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Key</span>
                    <input name="key" required />
                  </label>
                  <label>
                    <span>名称</span>
                    <input name="name" required />
                  </label>
                  <label>
                    <span>外部路径</span>
                    <input name="externalPath" required placeholder="/local/agentark/model" />
                  </label>
                </ResourceForm>
                <ResourceForm
                  title="绑定到 Environment"
                  pending={secretBindingCreate.isPending}
                  submitLabel="创建 Binding"
                  onSubmit={async (form) => {
                    await secretBindingCreate.mutateAsync(form);
                  }}
                >
                  <label>
                    <span>Secret Metadata</span>
                    <select name="secretMetadataId" required disabled={!environmentId}>
                      {secrets.data?.items.map((secret) => (
                        <option key={secret.id} value={secret.id}>
                          {secret.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span>Binding Key</span>
                    <input name="bindingKey" required disabled={!environmentId} />
                  </label>
                </ResourceForm>
                <article className="panel resource-panel">
                  <header className="panel__header">
                    <div>
                      <p className="eyebrow">SECRET REFERENCE ONLY</p>
                      <h3>Metadata 与 Binding</h3>
                    </div>
                    <LockKeyhole size={18} />
                  </header>
                  <DataTable
                    caption="Secret Metadata"
                    rows={secrets.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "name", header: "名称", render: (row) => row.name },
                      { key: "provider", header: "Provider", render: (row) => row.provider },
                      { key: "path", header: "外部路径", render: (row) => row.externalPath },
                      { key: "status", header: "状态", render: (row) => row.status },
                    ]}
                  />
                  <DataTable
                    caption="Secret Binding"
                    rows={secretBindings.data?.items ?? []}
                    getRowKey={(row) => row.id}
                    columns={[
                      { key: "key", header: "Binding", render: (row) => row.bindingKey },
                      {
                        key: "ref",
                        header: "Secret Metadata ID",
                        render: (row) => row.secretMetadataId,
                      },
                      {
                        key: "environment",
                        header: "Environment",
                        render: (row) => row.environmentId,
                      },
                    ]}
                  />
                  <p className="muted-copy">
                    <ShieldCheck size={14} /> 控制台永不请求或展示 Secret Value。
                  </p>
                </article>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
