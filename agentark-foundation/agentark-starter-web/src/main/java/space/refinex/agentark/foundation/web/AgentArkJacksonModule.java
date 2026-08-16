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

package space.refinex.agentark.foundation.web;

import space.refinex.agentark.kernel.id.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.function.Function;

/**
 * 将所有 AgentArk 强类型 UUIDv7 标识稳定序列化为规范字符串并执行严格反序列化。
 *
 * @author refinex
 */
public final class AgentArkJacksonModule extends SimpleModule {

    /**
     * Jackson 模块序列化兼容标识。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建并注册当前 Kernel 中全部公开强类型标识映射。
     */
    public AgentArkJacksonModule() {
        super("agentark-strong-ids");
        addSerializer(StrongId.class, new StrongIdSerializer());
        addId(OrganizationId.class, OrganizationId::parse);
        addId(ProjectId.class, ProjectId::parse);
        addId(EnvironmentId.class, EnvironmentId::parse);
        addId(AgentId.class, AgentId::parse);
        addId(RevisionId.class, RevisionId::parse);
        addId(SnapshotId.class, SnapshotId::parse);
        addId(DeploymentId.class, DeploymentId::parse);
        addId(KnowledgeRevisionId.class, KnowledgeRevisionId::parse);
        addId(SessionId.class, SessionId::parse);
        addId(TurnId.class, TurnId::parse);
        addId(RunId.class, RunId::parse);
        addId(ApprovalId.class, ApprovalId::parse);
        addId(JobId.class, JobId::parse);
        addId(EventId.class, EventId::parse);
        addId(PromptVersionId.class, PromptVersionId::parse);
        addId(McpServerVersionId.class, McpServerVersionId::parse);
        addId(SkillVersionId.class, SkillVersionId::parse);
        addId(MemoryProfileVersionId.class, MemoryProfileVersionId::parse);
        addId(WorkspaceProfileVersionId.class, WorkspaceProfileVersionId::parse);
        addId(SandboxProfileVersionId.class, SandboxProfileVersionId::parse);
        addId(UserIdentityId.class, UserIdentityId::parse);
        addId(ServiceAccountId.class, ServiceAccountId::parse);
        addId(MembershipId.class, MembershipId::parse);
        addId(RoleId.class, RoleId::parse);
        addId(PermissionId.class, PermissionId::parse);
        addId(RoleBindingId.class, RoleBindingId::parse);
        addId(ApiKeyId.class, ApiKeyId::parse);
        addId(PromptId.class, PromptId::parse);
        addId(ModelProviderId.class, ModelProviderId::parse);
        addId(ModelProfileId.class, ModelProfileId::parse);
        addId(McpServerId.class, McpServerId::parse);
        addId(McpToolDescriptorId.class, McpToolDescriptorId::parse);
        addId(SkillId.class, SkillId::parse);
        addId(MemoryProfileId.class, MemoryProfileId::parse);
        addId(WorkspaceProfileId.class, WorkspaceProfileId::parse);
        addId(SandboxProfileId.class, SandboxProfileId::parse);
        addId(PermissionPolicyId.class, PermissionPolicyId::parse);
        addId(PermissionPolicyVersionId.class, PermissionPolicyVersionId::parse);
        addId(SecretMetadataId.class, SecretMetadataId::parse);
        addId(SecretBindingId.class, SecretBindingId::parse);
        addId(KnowledgeBaseId.class, KnowledgeBaseId::parse);
        addId(DataSourceId.class, DataSourceId::parse);
        addId(DocumentId.class, DocumentId::parse);
        addId(DocumentRevisionId.class, DocumentRevisionId::parse);
        addId(ParserProfileId.class, ParserProfileId::parse);
        addId(ChunkProfileId.class, ChunkProfileId::parse);
        addId(EmbeddingProfileId.class, EmbeddingProfileId::parse);
        addId(RetrievalProfileId.class, RetrievalProfileId::parse);
        addId(IngestionRequestId.class, IngestionRequestId::parse);
    }

    /**
     * 为具体强类型标识注册严格字符串解析器。
     *
     * @param type   标识类型
     * @param parser 规范字符串解析函数
     * @param <T>    强类型标识类型
     */
    private <T extends StrongId> void addId(Class<T> type, Function<String, T> parser) {
        addDeserializer(type, new StrongIdDeserializer<>(type, parser));
    }

    /**
     * 将强类型标识输出为小写连字符形式字符串。
     *
     * @author refinex
     */
    private static final class StrongIdSerializer extends StdSerializer<StrongId> {

        /**
         * Jackson 序列化兼容标识。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 创建强类型标识序列化器。
         */
        private StrongIdSerializer() {
            super(StrongId.class);
        }

        /**
         * 将强类型标识写为规范字符串，不暴露内部对象结构。
         *
         * @param value     强类型标识
         * @param generator JSON 输出器
         * @param context   Jackson 序列化上下文
         * @throws JacksonException 输出失败时抛出
         */
        @Override
        public void serialize(StrongId value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
            generator.writeString(value.asString());
        }
    }

    /**
     * 使用具体标识的严格 parse 方法读取规范 UUIDv7 字符串。
     *
     * @param <T> 强类型标识类型
     * @author refinex
     */
    private static final class StrongIdDeserializer<T extends StrongId> extends StdDeserializer<T> {

        /**
         * Jackson 反序列化兼容标识。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 具体标识的规范字符串解析函数。
         */
        private final Function<String, T> parser;

        /**
         * 创建具体强类型标识反序列化器。
         *
         * @param type   具体标识类型
         * @param parser 规范字符串解析函数
         */
        private StrongIdDeserializer(Class<T> type, Function<String, T> parser) {
            super(type);
            this.parser = parser;
        }

        /**
         * 读取字符串并调用 Kernel 的严格 UUIDv7 解析逻辑。
         *
         * @param jsonParser JSON 输入器
         * @param context    Jackson 反序列化上下文
         * @return 通过版本和格式校验的强类型标识
         * @throws JacksonException         JSON Token 读取失败时抛出
         * @throws IllegalArgumentException 字符串不是规范 UUIDv7 时抛出
         */
        @Override
        public T deserialize(JsonParser jsonParser, DeserializationContext context)
            throws JacksonException {
            return parser.apply(jsonParser.getString());
        }
    }
}
