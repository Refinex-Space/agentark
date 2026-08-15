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

package space.refinex.agentark.foundation.persistence.contract;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.databind.JsonNode;

/**
 * 表达契约测试临时表的数据对象，用于验证 UUID、JSON、Instant 与乐观锁映射。
 *
 * @author refinex
 */
@TableName(value = "persistence_contract_record", autoResultMap = true)
public class PersistenceContractRecordDO {

    /**
     * UUIDv7 二进制主键。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /**
     * 用于验证租户唯一约束的 Organization UUIDv7。
     */
    @TableField(value = "organization_id", jdbcType = JdbcType.BINARY)
    private UUID organizationId;

    /**
     * Scope 内稳定且唯一的测试键。
     */
    @TableField("record_key")
    private String recordKey;

    /**
     * 用于验证受控 JSON TypeHandler 的低频 Payload。
     */
    @TableField(value = "payload", jdbcType = JdbcType.VARCHAR)
    private JsonNode payload;

    /**
     * 用于验证 TIMESTAMP(6)/UTC 的观测时刻。
     */
    @TableField(value = "observed_at", jdbcType = JdbcType.TIMESTAMP)
    private Instant observedAt;

    /**
     * MyBatis-Plus 乐观锁版本号。
     */
    @Version
    @TableField("version")
    private Long version;

    /**
     * 创建 MyBatis 结果映射所需的空数据对象。
     */
    public PersistenceContractRecordDO() {
        // MyBatis 先创建实例，再按列填充字段。
    }

    /**
     * 返回记录主键。
     *
     * @return UUIDv7 主键
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置记录主键。
     *
     * @param id UUIDv7 主键
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 返回 Organization 标识。
     *
     * @return Organization UUIDv7
     */
    public UUID getOrganizationId() {
        return organizationId;
    }

    /**
     * 设置 Organization 标识。
     *
     * @param organizationId Organization UUIDv7
     */
    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * 返回 Scope 内记录键。
     *
     * @return 非空记录键
     */
    public String getRecordKey() {
        return recordKey;
    }

    /**
     * 设置 Scope 内记录键。
     *
     * @param recordKey 非空记录键
     */
    public void setRecordKey(String recordKey) {
        this.recordKey = recordKey;
    }

    /**
     * 返回 JSON Payload。
     *
     * @return 非空 JSON 节点
     */
    public JsonNode getPayload() {
        return payload;
    }

    /**
     * 设置 JSON Payload。
     *
     * @param payload 非空 JSON 节点
     */
    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    /**
     * 返回 UTC 观测时刻。
     *
     * @return 微秒精度 Instant
     */
    public Instant getObservedAt() {
        return observedAt;
    }

    /**
     * 设置 UTC 观测时刻。
     *
     * @param observedAt 微秒精度 Instant
     */
    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    /**
     * 返回乐观锁版本。
     *
     * @return 非负版本号
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本。
     *
     * @param version 非负版本号
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
