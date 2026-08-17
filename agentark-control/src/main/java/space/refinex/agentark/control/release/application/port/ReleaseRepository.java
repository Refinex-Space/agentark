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

package space.refinex.agentark.control.release.application.port;

import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.ReleaseModels.*;
import space.refinex.agentark.kernel.id.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Draft、不可变 Revision/Snapshot、Deployment 历史和 Outbox 的 Control 持久化端口。
 *
 * @author refinex
 */
public interface ReleaseRepository {

    /**
     * @param draft 首次创建的 Draft @param actor 操作主体
     */
    void insertDraft(AgentDraft draft, String actor);

    /**
     * @param projectId 项目标识 @param agentId Agent 标识 @return 同项目 Draft
     */
    Optional<AgentDraft> findDraft(ProjectId projectId, AgentId agentId);

    /**
     * @param projectId       项目标识
     * @param agentId         Agent 标识
     * @param spec            新 Draft 规范
     * @param expectedVersion 预期乐观锁版本
     * @param now             更新时间
     * @param actor           操作主体
     * @return 更新行数
     */
    int updateDraft(
        ProjectId projectId,
        AgentId agentId,
        AgentDraftSpec spec,
        long expectedVersion,
        Instant now,
        String actor);

    /**
     * @param projectId 项目标识 @param agentId Agent 标识 @return 锁定后的 Draft
     */
    Optional<AgentDraft> lockDraft(ProjectId projectId, AgentId agentId);

    /**
     * @param projectId 项目标识 @param agentId Agent 标识 @return 下一 Revision 序号
     */
    long nextRevisionNumber(ProjectId projectId, AgentId agentId);

    /**
     * @param report Draft 校验报告 @param projectId 项目标识 @param actor 操作主体
     */
    void insertValidationReport(ValidationReport report, ProjectId projectId, String actor);

    /**
     * @param projectId 项目标识 @param agentId Agent 标识 @param key 幂等键 @return 已有操作
     */
    Optional<PublishOperation> findPublishOperation(
        ProjectId projectId, AgentId agentId, String key);

    /**
     * 在调用方本地事务中原子插入 Revision、Snapshot、发布操作、校验报告和 Outbox。
     *
     * @param snapshot  不可变 Snapshot
     * @param operation 成功发布操作
     * @param report    发布校验报告
     * @param outbox    发布事件
     * @param actor     操作主体
     */
    void insertPublished(
        StoredSnapshot snapshot,
        PublishOperation operation,
        ValidationReport report,
        OutboxEvent outbox,
        String actor);

    /**
     * @param projectId 项目标识 @param revisionId Revision 标识 @return 同项目 Snapshot
     */
    Optional<StoredSnapshot> findSnapshot(ProjectId projectId, RevisionId revisionId);

    /**
     * @param revisionId Revision 标识 @return Internal API 使用的 Snapshot
     */
    Optional<StoredSnapshot> findSnapshotInternal(RevisionId revisionId);

    /**
     * @param projectId 项目标识 @param agentId Agent 标识 @return Revision 列表
     */
    List<AgentRevision> listRevisions(ProjectId projectId, AgentId agentId);

    /**
     * 按 UUIDv7 顺序列出 Environment 内的 Deployment。
     *
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param afterId       不包含当前值的 UUIDv7 游标
     * @param limit         读取上限
     * @return 按 UUIDv7 升序排列的 Deployment
     */
    List<Deployment> listDeployments(
        ProjectId projectId, EnvironmentId environmentId, DeploymentId afterId, int limit);

    /**
     * @param deployment 部署聚合 @param history 首次历史 @param outbox 事件 @param actor 主体
     */
    void insertDeployment(
        Deployment deployment, DeploymentRevision history, OutboxEvent outbox, String actor);

    /**
     * @param projectId 项目标识 @param environmentId 环境标识 @param id Deployment 标识 @return 部署
     */
    Optional<Deployment> findDeployment(
        ProjectId projectId, EnvironmentId environmentId, DeploymentId id);

    /**
     * @param id Deployment 标识 @return Internal API 使用的部署
     */
    Optional<Deployment> findDeploymentInternal(DeploymentId id);

    /**
     * 原子更新 Deployment 指针或状态并追加历史与 Outbox。
     *
     * @param deployment      新聚合状态
     * @param expectedVersion 预期乐观锁版本
     * @param history         追加历史
     * @param outbox          变更事件
     * @param actor           操作主体
     * @return 更新行数
     */
    int updateDeployment(
        Deployment deployment,
        long expectedVersion,
        DeploymentRevision history,
        OutboxEvent outbox,
        String actor);
}
