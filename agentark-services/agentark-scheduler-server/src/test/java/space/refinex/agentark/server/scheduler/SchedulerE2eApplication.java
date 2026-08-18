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

package space.refinex.agentark.server.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import space.refinex.agentark.foundation.security.AudienceValidator;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.adapter.out.control.ControlKnowledgeIngestionClient;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionWorker;
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.EmbeddedChunk;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.ParsedDocument;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;
import space.refinex.agentark.scheduling.application.KnowledgeIngestionJobHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 使用真实 Scheduler 和临时 RSA JWT Decoder 启动浏览器 E2E，配置不进入生产 JAR。
 *
 * @author refinex
 */
public final class SchedulerE2eApplication {

    /** 禁止实例化测试启动器。 */
    private SchedulerE2eApplication() {
    }

    /**
     * 启动带 Test Classpath 安全配置的 Scheduler。
     *
     * @param args Spring Boot 参数
     */
    public static void main(String[] args) {
        SpringApplication.from(AgentArkSchedulerApplication::main)
            .with(E2eSecurityConfiguration.class)
            .run(args);
    }

    /**
     * 定义 Scheduler E2E JWT Decoder。
     *
     * @author refinex
     */
    @TestConfiguration(proxyBeanMethods = false)
    public static class E2eSecurityConfiguration {

        /**
         * 创建面向 Scheduler Audience 的真实 RS256 Decoder。
         *
         * @return E2E JWT Decoder
         */
        @Bean
        @Primary
        public JwtDecoder e2eJwtDecoder() {
            return decoder("agentark-scheduler");
        }

        /**
         * 创建跨真实 Scheduler/Control Contract 的确定性 Knowledge 摄取 Worker。
         *
         * <p>测试 Provider 不连接付费 Embedding 或外部 Qdrant，但执行真实摄取编排、Job、
         * Internal API、Control 状态机和结果幂等逻辑，且只存在于 Test Classpath。
         *
         * @param properties Scheduler 内部服务配置
         * @param objectMapper Scheduler Job Payload 解析器
         * @return E2E Knowledge 摄取 Handler
         */
        @Bean
        public KnowledgeIngestionJobHandler e2eKnowledgeIngestionJobHandler(
            SchedulerServerProperties properties, ObjectMapper objectMapper) {
            ControlKnowledgeIngestionClient controlClient =
                new ControlKnowledgeIngestionClient(
                    RestClient.builder().baseUrl(properties.controlBaseUrl().toString()).build(),
                    properties::internalServiceToken);
            KnowledgeIngestionWorker worker = new KnowledgeIngestionWorker(
                controlClient,
                controlClient,
                revision -> new ByteArrayInputStream(
                    "AgentArk release readiness knowledge".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)),
                (revision, resolver) -> CompletableFuture.completedFuture(null),
                (revision, profile, resolver) -> CompletableFuture.completedFuture(
                    new ParsedDocument(
                        revision.id(), "AgentArk release readiness knowledge",
                        Map.of("source_trust", "UNTRUSTED_EXTERNAL"))),
                (document, profile) -> CompletableFuture.completedFuture(List.of(
                    new KnowledgeChunk(
                        document.documentRevisionId().asString() + ":c000000",
                        document.documentRevisionId(), document.text(), document.metadata()))),
                new E2eChunkArtifactStore(),
                (chunks, profile) -> CompletableFuture.completedFuture(chunks.stream()
                    .map(chunk -> new EmbeddedChunk(chunk, new float[]{1.0F, 0.0F, 0.0F}))
                    .toList()),
                new E2eVectorStore(),
                Runnable::run,
                Clock.systemUTC(),
                32,
                1,
                Duration.ZERO);
            return new KnowledgeIngestionJobHandler(worker, objectMapper);
        }
    }

    /**
     * 在 E2E 内生成不可变 Chunk 引用，不写入生产或用户文件系统。
     *
     * @author refinex
     */
    private static final class E2eChunkArtifactStore implements ChunkArtifactStore {

        /** 创建无状态 E2E Chunk Artifact Store。 */
        private E2eChunkArtifactStore() {
        }

        /**
         * 返回与当前 Revision 和 Chunk 数绑定的内容寻址引用。
         *
         * @param revisionId 固定 Knowledge Revision 标识
         * @param chunks     有序 Chunk
         * @return 已完成的 ObjectRef
         */
        @Override
        public CompletionStage<ObjectRef> put(
            space.refinex.agentark.kernel.id.KnowledgeRevisionId revisionId,
            List<KnowledgeChunk> chunks) {
            String payload = revisionId.asString() + ":" + chunks.size();
            return CompletableFuture.completedFuture(ObjectRef.of(
                "object://e2e-knowledge/chunks.ndjson", Checksum.sha256(payload),
                payload.length(), "application/x-ndjson"));
        }

        /**
         * E2E Artifact 不写物理对象，删除只返回完成信号。
         *
         * @param ref 待删除引用
         * @return 已完成信号
         */
        @Override
        public CompletionStage<Void> delete(ObjectRef ref) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 记录一次 E2E 向量写入并校验 Count/Checksum，不承担检索功能。
     *
     * @author refinex
     */
    private static final class E2eVectorStore implements KnowledgeVectorStore {

        /** 最近一次写入的 Point 数。 */
        private int count;

        /** 最近一次写入的 Revision 摘要。 */
        private Checksum checksum;

        /** 创建初始为空的 E2E 向量存储。 */
        private E2eVectorStore() {
        }

        /**
         * 记录租户固定写入的数量和摘要。
         *
         * @param request 向量写入请求
         * @return 已完成信号
         */
        @Override
        public CompletionStage<Void> upsert(VectorWriteRequest request) {
            count = request.chunks().size();
            checksum = request.revisionChecksum();
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 校验 Control Worker 期望值与最近一次写入完全一致。
         *
         * @param request 数量与摘要校验请求
         * @return 匹配结果
         */
        @Override
        public CompletionStage<Boolean> verify(VectorVerificationRequest request) {
            return CompletableFuture.completedFuture(
                request.expectedCount() == count && request.expectedChecksum().equals(checksum));
        }

        /**
         * Phase 23 摄取 E2E 不执行检索，返回空结果。
         *
         * @param request 固定租户检索请求
         * @return 空命中列表
         */
        @Override
        public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        /**
         * E2E 向量数据仅驻留当前对象，删除后清空记录。
         *
         * @param scope 固定租户与 Revision 范围
         * @return 已完成信号
         */
        @Override
        public CompletionStage<Void> delete(VectorScope scope) {
            count = 0;
            checksum = null;
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 从临时 X.509 RSA 公钥创建 Decoder。
     *
     * @param audience 当前服务 Audience
     * @return 带 Issuer 与 Audience Validator 的 Decoder
     */
    private static JwtDecoder decoder(String audience) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required("AGENTARK_E2E_PUBLIC_KEY"));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                JwtValidators.createDefaultWithIssuer(required("AGENTARK_E2E_ISSUER")),
                new AudienceValidator(java.util.Set.of(audience))));
            return decoder;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("E2E RSA public key is invalid", exception);
        }
    }

    /**
     * 读取必需且非空的 E2E 环境变量，不输出其内容。
     *
     * @param name 环境变量名称
     * @return 非空值
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for E2E");
        }
        return value;
    }
}
