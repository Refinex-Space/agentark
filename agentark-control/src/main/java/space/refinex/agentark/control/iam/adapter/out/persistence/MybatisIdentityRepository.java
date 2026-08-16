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

package space.refinex.agentark.control.iam.adapter.out.persistence;

import space.refinex.agentark.control.iam.application.port.IdentityRepository;
import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.kernel.id.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis 显式 SQL 实现身份、服务账号和成员关系持久化端口。
 *
 * @author refinex
 */
public final class MybatisIdentityRepository implements IdentityRepository {

    /**
     * 身份 Mapper。
     */
    private final IdentityMapper mapper;

    /**
     * 创建身份持久化适配器。
     *
     * @param mapper 身份 Mapper
     */
    public MybatisIdentityRepository(IdentityMapper mapper) {
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 幂等写入并回读最终 Issuer/Subject 映射。
     *
     * @param identity 待写入身份
     * @return 数据库唯一映射
     */
    @Override
    public UserIdentity upsertUserIdentity(UserIdentity identity) {
        mapper.upsertUserIdentity(new IamPersistenceRows.UserIdentityRow(
            identity.id().value(), identity.issuer(), identity.subject(),
            identity.displayName().orElse(null), identity.email().orElse(null),
            identity.status().name(), identity.lastSeenAt(), identity.version(),
            identity.createdAt(), identity.updatedAt()));
        return findUserIdentity(identity.issuer(), identity.subject()).orElseThrow();
    }

    /**
     * 按 Issuer/Subject 读取身份。
     *
     * @param issuer  Issuer
     * @param subject Subject
     * @return 身份或空
     */
    @Override
    public Optional<UserIdentity> findUserIdentity(String issuer, String subject) {
        return mapper.findUserIdentity(issuer, subject).map(this::userIdentity);
    }

    /**
     * 插入服务账号。
     *
     * @param serviceAccount 待持久化账号
     */
    @Override
    public void insertServiceAccount(ServiceAccount serviceAccount) {
        mapper.insertServiceAccount(new IamPersistenceRows.ServiceAccountRow(
            serviceAccount.id().value(), serviceAccount.organizationId().value(),
            serviceAccount.projectId().value(), serviceAccount.name(),
            serviceAccount.status().name(), serviceAccount.version(), serviceAccount.createdAt(),
            serviceAccount.updatedAt()));
    }

    /**
     * 按标识读取活动服务账号。
     *
     * @param serviceAccountId 服务账号标识
     * @return 服务账号或空
     */
    @Override
    public Optional<ServiceAccount> findServiceAccount(ServiceAccountId serviceAccountId) {
        return mapper.findServiceAccount(serviceAccountId.value()).map(this::serviceAccount);
    }

    /**
     * 判断多态主体是否活动。
     *
     * @param principalKind 主体类别
     * @param principalId   主体 UUIDv7
     * @return 主体活动时为 {@code true}
     */
    @Override
    public boolean principalExists(PrincipalKind principalKind, UUID principalId) {
        return switch (principalKind) {
            case USER -> mapper.countActiveUserIdentity(principalId) == 1;
            case SERVICE_ACCOUNT -> mapper.countActiveServiceAccount(principalId) == 1;
        };
    }

    /**
     * 按完整 Scope 列出服务账号。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 服务账号列表
     */
    @Override
    public List<ServiceAccount> listServiceAccounts(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listServiceAccounts(organizationId.value(), projectId.value(), limit).stream()
            .map(this::serviceAccount).toList();
    }

    /**
     * 插入项目成员关系。
     *
     * @param membership 待持久化成员关系
     */
    @Override
    public void insertMembership(Membership membership) {
        mapper.insertMembership(new IamPersistenceRows.MembershipRow(
            membership.id().value(), membership.organizationId().value(),
            membership.projectId().value(), membership.principalKind().name(),
            membership.principalId(), membership.status().name(), membership.version(),
            membership.createdAt(), membership.updatedAt()));
    }

    /**
     * 判断主体是否为项目活动成员。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @return 活动成员时为 {@code true}
     */
    @Override
    public boolean isActiveMember(
        OrganizationId organizationId,
        ProjectId projectId,
        PrincipalKind principalKind,
        UUID principalId) {
        return mapper.countActiveMembership(
            organizationId.value(), projectId.value(), principalKind.name(), principalId) == 1;
    }

    /**
     * 按完整 Scope 列出成员关系。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 成员关系列表
     */
    @Override
    public List<Membership> listMemberships(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listMemberships(organizationId.value(), projectId.value(), limit).stream()
            .map(this::membership).toList();
    }

    /**
     * 转换外部身份数据库行。
     *
     * @param row 数据库行
     * @return 身份领域对象
     */
    private UserIdentity userIdentity(IamPersistenceRows.UserIdentityRow row) {
        return new UserIdentity(new UserIdentityId(row.id()), row.issuer(), row.subject(),
            Optional.ofNullable(row.displayName()), Optional.ofNullable(row.email()),
            IamStatus.valueOf(row.status()), row.lastSeenAt(), row.version(), row.createdAt(),
            row.updatedAt());
    }

    /**
     * 转换服务账号数据库行。
     *
     * @param row 数据库行
     * @return 服务账号领域对象
     */
    private ServiceAccount serviceAccount(IamPersistenceRows.ServiceAccountRow row) {
        return new ServiceAccount(new ServiceAccountId(row.id()),
            new OrganizationId(row.organizationId()), new ProjectId(row.projectId()), row.name(),
            IamStatus.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 转换成员关系数据库行。
     *
     * @param row 数据库行
     * @return 成员关系领域对象
     */
    private Membership membership(IamPersistenceRows.MembershipRow row) {
        return new Membership(new MembershipId(row.id()),
            new OrganizationId(row.organizationId()), new ProjectId(row.projectId()),
            PrincipalKind.valueOf(row.principalType()), row.principalId(),
            IamStatus.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
    }
}
