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

package space.refinex.agentark.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.web.RequestContextAccessor;
import space.refinex.agentark.knowledge.adapter.in.web.KnowledgeController;
import space.refinex.agentark.knowledge.adapter.in.web.KnowledgeIngestionInternalController;
import space.refinex.agentark.knowledge.adapter.in.web.KnowledgeProblemDetailAdvice;
import space.refinex.agentark.knowledge.adapter.out.persistence.KnowledgeIngestionResultMapper;
import space.refinex.agentark.knowledge.adapter.out.persistence.KnowledgeMapper;
import space.refinex.agentark.knowledge.adapter.out.persistence.MybatisKnowledgeIngestionResultRepository;
import space.refinex.agentark.knowledge.adapter.out.persistence.MybatisKnowledgeRepository;
import space.refinex.agentark.knowledge.application.KnowledgeApplicationService;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionControlService;
import space.refinex.agentark.knowledge.application.KnowledgeRevisionResolver;
import space.refinex.agentark.knowledge.application.port.KnowledgeAccessPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeAuditPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeIngestionResultRepository;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;

/**
 * 装配 Knowledge MyBatis 适配器、应用服务、READY Resolver 与 Public API。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.knowledge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@MapperScan(basePackageClasses = KnowledgeMapper.class)
public class KnowledgeConfiguration {

    /**
     * 创建 Knowledge 模块装配。
     */
    public KnowledgeConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * @param mapper     Knowledge Mapper
     * @param jsonMapper 应用统一 JSON Mapper
     * @return MyBatis Knowledge Repository
     */
    @Bean
    public KnowledgeRepository knowledgeRepository(
        KnowledgeMapper mapper, JsonMapper jsonMapper) {
        return new MybatisKnowledgeRepository(mapper, jsonMapper);
    }

    /**
     * @param mapper     摄取结果 Mapper
     * @param jsonMapper JSON Mapper
     * @return MyBatis 摄取结果仓储
     */
    @Bean
    public KnowledgeIngestionResultRepository knowledgeIngestionResultRepository(
        KnowledgeIngestionResultMapper mapper, JsonMapper jsonMapper) {
        return new MybatisKnowledgeIngestionResultRepository(mapper, jsonMapper);
    }

    /**
     * @param repository          Knowledge Repository
     * @param accessPort          组合根授权 Port
     * @param auditPort           组合根审计 Port
     * @param objectStoreProvider 可选 Object Store
     * @param clock               UTC 时钟
     * @return Knowledge 应用服务
     */
    @Bean
    public KnowledgeApplicationService knowledgeApplicationService(
        KnowledgeRepository repository,
        KnowledgeAccessPort accessPort,
        KnowledgeAuditPort auditPort,
        ObjectProvider<ObjectStore> objectStoreProvider,
        Clock clock) {
        return new KnowledgeApplicationService(
            repository, accessPort, auditPort,
            Optional.ofNullable(objectStoreProvider.getIfAvailable()), clock);
    }

    /**
     * @param repository Knowledge Repository
     * @return 仅解析 READY Revision 的 Resolver
     */
    @Bean
    public KnowledgeRevisionResolver knowledgeRevisionResolver(KnowledgeRepository repository) {
        return new KnowledgeRevisionResolver(repository);
    }

    /**
     * @param repository       Knowledge 元数据仓储
     * @param resultRepository 摄取结果与 Outbox 仓储
     * @param jsonMapper       JSON Mapper
     * @param clock            UTC 时钟
     * @return Internal 摄取 Control 服务
     */
    @Bean
    public KnowledgeIngestionControlService knowledgeIngestionControlService(
        KnowledgeRepository repository,
        KnowledgeIngestionResultRepository resultRepository,
        JsonMapper jsonMapper,
        Clock clock) {
        return new KnowledgeIngestionControlService(
            repository, resultRepository, jsonMapper, clock);
    }

    /**
     * @param service Internal 摄取 Control 服务
     * @return Scheduler Worker Internal API
     */
    @Bean
    public KnowledgeIngestionInternalController knowledgeIngestionInternalController(
        KnowledgeIngestionControlService service) {
        return new KnowledgeIngestionInternalController(service);
    }

    /**
     * @param service    Knowledge 应用服务
     * @param jsonMapper 应用统一 JSON Mapper
     * @return Knowledge Public API
     */
    @Bean
    public KnowledgeController knowledgeController(
        KnowledgeApplicationService service, JsonMapper jsonMapper) {
        return new KnowledgeController(service, jsonMapper);
    }

    /**
     * @param accessor 请求上下文访问器
     * @return Knowledge ProblemDetail 映射器
     */
    @Bean
    public KnowledgeProblemDetailAdvice knowledgeProblemDetailAdvice(
        RequestContextAccessor accessor) {
        return new KnowledgeProblemDetailAdvice(accessor);
    }
}
