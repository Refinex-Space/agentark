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

package space.refinex.agentark.control.iam.adapter.out.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.refinex.agentark.control.iam.application.IamAuditRecord;
import space.refinex.agentark.control.iam.application.port.IamAuditPort;

/**
 * 将非秘密 IAM 审计事实写入独立结构化日志类别，Phase 19 可替换为持久审计适配器。
 *
 * @author refinex
 */
public final class StructuredLogIamAuditAdapter implements IamAuditPort {

    /**
     * IAM 审计专用日志器。
     */
    private static final Logger AUDIT = LoggerFactory.getLogger("agentark.audit.iam");

    /**
     * 创建无可变状态的审计适配器。
     */
    public StructuredLogIamAuditAdapter() {
    }

    /**
     * 写入字段受控的单行审计记录，不包含请求正文、Token 或 API Key。
     *
     * @param record 审计记录
     */
    @Override
    public void append(IamAuditRecord record) {
        java.util.Objects.requireNonNull(record, "record must not be null");
        AUDIT.info(
            "iam_audit action={} principal={} resource_type={} resource_id={} organization_id={} project_id={} outcome={} occurred_at={}",
            record.action(),
            record.principal(),
            record.resourceType(),
            record.resourceId(),
            record.organizationId().map(value -> value.asString()).orElse("-"),
            record.projectId().map(value -> value.asString()).orElse("-"),
            record.outcome(),
            record.occurredAt());
    }
}
