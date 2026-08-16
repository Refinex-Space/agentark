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

package space.refinex.agentark.knowledge.domain;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.ObjectRef;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示一次不可变原文件提交，ObjectRef 同时携带 SHA-256、大小和媒体类型。
 *
 * @param id               文档修订标识
 * @param organizationId   组织标识
 * @param projectId        项目标识
 * @param knowledgeBaseId  Knowledge Base 标识
 * @param documentId       文档稳定标识
 * @param revisionNumber   文档内单调递增版本号
 * @param originalFileName 原始文件名，不作为对象路径
 * @param objectRef        不含授权信息的原文件引用
 * @param createdAt        创建时间
 * @author refinex
 */
public record DocumentRevision(
    DocumentRevisionId id,
    OrganizationId organizationId,
    ProjectId projectId,
    KnowledgeBaseId knowledgeBaseId,
    DocumentId documentId,
    long revisionNumber,
    String originalFileName,
    ObjectRef objectRef,
    Instant createdAt) {

    /**
     * 校验租户链、不可变版本号、文件名和 ObjectRef。
     *
     * @param id               文档修订标识
     * @param organizationId   组织标识
     * @param projectId        项目标识
     * @param knowledgeBaseId  Knowledge Base 标识
     * @param documentId       文档稳定标识
     * @param revisionNumber   文档内版本号
     * @param originalFileName 原始文件名
     * @param objectRef        原文件引用
     * @param createdAt        创建时间
     */
    public DocumentRevision {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(objectRef, "objectRef must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (revisionNumber <= 0
            || originalFileName == null
            || originalFileName.isBlank()
            || originalFileName.length() > 255
            || originalFileName.contains("/")
            || originalFileName.contains("\\")) {
            throw new IllegalArgumentException("document revision number or file name is invalid");
        }
    }
}
