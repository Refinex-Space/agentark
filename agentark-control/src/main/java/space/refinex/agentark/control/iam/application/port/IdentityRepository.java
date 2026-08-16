/*
 * Copyright 2026 refinex.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package space.refinex.agentark.control.iam.application.port;

import space.refinex.agentark.control.iam.domain.Membership;
import space.refinex.agentark.control.iam.domain.PrincipalKind;
import space.refinex.agentark.control.iam.domain.ServiceAccount;
import space.refinex.agentark.control.iam.domain.UserIdentity;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义外部身份、服务账号和项目成员关系的持久化端口。
 *
 * @author refinex
 */
public interface IdentityRepository {

    /**
     * 创建或刷新 Issuer/Subject 身份映射，不保存 Token。
     *
     * @param identity 待写入身份映射
     * @return 唯一约束竞争后数据库中的最终映射
     */
    UserIdentity upsertUserIdentity(UserIdentity identity);

    /**
     * 按 Issuer 与 Subject 读取身份映射。
     *
     * @param issuer  已验证 Issuer
     * @param subject 稳定 Subject
     * @return 存在时返回映射
     */
    Optional<UserIdentity> findUserIdentity(String issuer, String subject);

    /**
     * 插入项目服务账号。
     *
     * @param serviceAccount 待持久化账号
     */
    void insertServiceAccount(ServiceAccount serviceAccount);

    /**
     * 按标识读取服务账号。
     *
     * @param serviceAccountId 服务账号标识
     * @return 存在时返回账号
     */
    Optional<ServiceAccount> findServiceAccount(ServiceAccountId serviceAccountId);

    /**
     * 判断多态主体标识是否指向活动用户身份或服务账号。
     *
     * @param principalKind 主体类别
     * @param principalId   主体 UUIDv7
     * @return 主体存在且活动时为 {@code true}
     */
    boolean principalExists(PrincipalKind principalKind, UUID principalId);

    /**
     * 列出指定项目的服务账号。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 按名称排序的账号
     */
    List<ServiceAccount> listServiceAccounts(
        OrganizationId organizationId, ProjectId projectId, int limit);

    /**
     * 插入成员关系。
     *
     * @param membership 待持久化成员关系
     */
    void insertMembership(Membership membership);

    /**
     * 判断主体是否为项目活动成员。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @return 活动成员时为 {@code true}
     */
    boolean isActiveMember(
        OrganizationId organizationId,
        ProjectId projectId,
        PrincipalKind principalKind,
        UUID principalId);

    /**
     * 列出项目成员关系。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 按创建时刻排序的成员关系
     */
    List<Membership> listMemberships(
        OrganizationId organizationId, ProjectId projectId, int limit);
}
