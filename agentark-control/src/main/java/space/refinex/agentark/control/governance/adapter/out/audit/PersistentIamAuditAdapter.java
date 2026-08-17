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

package space.refinex.agentark.control.governance.adapter.out.audit;

import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.control.iam.application.IamAuditRecord;
import space.refinex.agentark.control.iam.application.port.IamAuditPort;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import space.refinex.agentark.kernel.id.EventId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 Control 本地 IAM、发布和 Secret 操作与业务事务一起写入不可变 Audit Event 表。
 *
 * @author refinex
 */
public final class PersistentIamAuditAdapter implements IamAuditPort {

    /** 治理持久化仓储。 */
    private final GovernanceRepository repository;

    /** 当前 Servlet 请求上下文。 */
    private final RequestContextAccessor requestContextAccessor;

    /** UTC 时钟。 */
    private final Clock clock;

    /**
     * 创建持久 IAM Audit Adapter。
     *
     * @param repository             Governance Repository
     * @param requestContextAccessor 请求上下文访问器
     * @param clock                  UTC 时钟
     */
    public PersistentIamAuditAdapter(
        GovernanceRepository repository,
        RequestContextAccessor requestContextAccessor,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.requestContextAccessor = Objects.requireNonNull(
            requestContextAccessor, "requestContextAccessor must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 追加不含正文和凭据的 IAM Audit Event；持久失败向业务事务抛出。
     *
     * @param record IAM 审计记录
     */
    @Override
    public void append(IamAuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        var context = requestContextAccessor.current();
        Instant ingestedAt = Instant.now(clock);
        repository.appendAudit(new AuditEvent(
            EventId.generate(), EventId.generate().asString(), AuditPlane.CONTROL,
            record.organizationId(), record.projectId(), principalType(record.principal()),
            record.principal(), record.projectId().isPresent()
                ? AuditScopeType.PROJECT
                : record.organizationId().isPresent()
                    ? AuditScopeType.ORGANIZATION : AuditScopeType.PLATFORM,
            record.projectId().map(value -> value.asString())
                .or(() -> record.organizationId().map(value -> value.asString()))
                .orElse("platform"),
            record.action(), AuditResult.valueOf(record.outcome()), record.resourceType(),
            record.resourceId(), Map.of(), Optional.empty(), Optional.empty(),
            context.map(value -> value.traceId()), context.map(value -> value.requestId()),
            record.occurredAt(), ingestedAt));
    }

    /**
     * 根据受控 Issuer 前缀推导主体类型，不记录 API Key 明文。
     *
     * @param principal 主体稳定引用
     * @return Audit 主体类型
     */
    private AuditPrincipalType principalType(String principal) {
        if (principal.startsWith("urn:agentark:api-key")) {
            return AuditPrincipalType.API_KEY;
        }
        if (principal.startsWith("urn:agentark:service")) {
            return AuditPrincipalType.SERVICE;
        }
        return AuditPrincipalType.USER;
    }
}
