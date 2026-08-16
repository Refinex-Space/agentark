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

package space.refinex.agentark.scheduling.adapter.out.persistence;

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.SchedulerAuditPort;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Scheduler 管理操作审计事实写入所属 Outbox，拒绝空实现静默丢弃。
 *
 * @author refinex
 */
public final class MybatisSchedulerAuditAdapter implements SchedulerAuditPort {

    /**
     * 调度数据库 Mapper。
     */
    private final SchedulerMapper mapper;

    /**
     * 审计载荷 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 Outbox 审计适配器。
     *
     * @param mapper     Scheduler Mapper
     * @param jsonMapper JSON Mapper
     */
    public MybatisSchedulerAuditAdapter(SchedulerMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 写入不含 Payload 和凭据的审计 Outbox。
     */
    @Override
    public void record(
        String action, String actor, OrganizationId organizationId, ProjectId projectId,
        JobId jobId, String reason, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("actor", actor);
        payload.put("organizationId", organizationId.asString());
        payload.put("projectId", projectId.asString());
        payload.put("jobId", jobId.asString());
        payload.put("reason", reason);
        payload.put("occurredAt", occurredAt.toString());
        mapper.insertOutbox(
            SchedulerUuidV7.generate(occurredAt), "audit", jobId.value(), action,
            jsonMapper.writeValueAsString(payload), "PENDING", occurredAt, 0, occurredAt);
    }
}
