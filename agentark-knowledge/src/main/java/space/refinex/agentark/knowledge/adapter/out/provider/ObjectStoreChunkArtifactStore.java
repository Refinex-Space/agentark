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

package space.refinex.agentark.knowledge.adapter.out.provider;

import space.refinex.agentark.foundation.storage.*;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * 把有序 Chunk 写为 UTF-8 NDJSON 不可变制品，并由 Object Store 复核大小和 SHA-256。
 *
 * @author refinex
 */
public final class ObjectStoreChunkArtifactStore implements ChunkArtifactStore {

    /**
     * Chunk 制品专用命名空间。
     */
    private static final ObjectNamespace NAMESPACE = new ObjectNamespace("knowledge-chunks");

    /**
     * 工作进程对象存储。
     */
    private final ObjectStore objectStore;

    /**
     * 统一 JSON Mapper。
     */
    private final JsonMapper jsonMapper;

    /**
     * 阻塞对象存储操作专用执行器。
     */
    private final Executor executor;

    /**
     * 创建 Chunk 制品存储。
     *
     * @param objectStore Worker Object Store
     * @param jsonMapper  JSON Mapper
     * @param executor    阻塞 I/O 执行器
     */
    public ObjectStoreChunkArtifactStore(
        ObjectStore objectStore, JsonMapper jsonMapper, Executor executor) {
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * 序列化有序 Chunk 并写入不可变对象。
     *
     * @param revisionId 固定 Knowledge Revision 标识
     * @param chunks     有序 Chunk
     * @return 异步对象引用
     */
    @Override
    public CompletionStage<ObjectRef> put(
        KnowledgeRevisionId revisionId, List<KnowledgeChunk> chunks) {
        Objects.requireNonNull(revisionId, "revisionId must not be null");
        List<KnowledgeChunk> values = List.copyOf(chunks);
        if (values.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("chunks must not be empty"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                for (KnowledgeChunk chunk : values) {
                    ArtifactLine line = new ArtifactLine(
                        revisionId.asString(), chunk.key(),
                        chunk.documentRevisionId().asString(), chunk.text(), chunk.metadata());
                    output.write(jsonMapper.writeValueAsBytes(line));
                    output.write('\n');
                }
                byte[] bytes = output.toByteArray();
                return objectStore.put(new PutObjectCommand(
                    NAMESPACE, new ByteArrayInputStream(bytes), bytes.length,
                    "application/x-ndjson", Optional.of(Checksum.sha256(
                        new String(bytes, StandardCharsets.UTF_8)))));
            } catch (IOException exception) {
                throw new CompletionException("chunk artifact write failed", exception);
            }
        }, executor);
    }

    /**
     * 删除当前 Store 拥有的派生制品。
     *
     * @param ref 制品引用
     * @return 异步完成信号
     */
    @Override
    public CompletionStage<Void> delete(ObjectRef ref) {
        Objects.requireNonNull(ref, "ref must not be null");
        return CompletableFuture.runAsync(() -> {
            try {
                objectStore.delete(ref);
            } catch (IOException exception) {
                throw new CompletionException("chunk artifact delete failed", exception);
            }
        }, executor);
    }

    /**
     * @param knowledgeRevisionId Knowledge Revision 标识
     * @param chunkKey            文本块稳定键
     * @param documentRevisionId  文档修订标识
     * @param text                Chunk 原文
     * @param metadata            非敏感元数据
     * @author refinex
     */
    private record ArtifactLine(
        String knowledgeRevisionId,
        String chunkKey,
        String documentRevisionId,
        String text,
        Map<String, String> metadata) {

        /**
         * 防御性复制 Chunk 元数据。
         *
         * @param knowledgeRevisionId Knowledge Revision 标识
         * @param chunkKey            Chunk Key
         * @param documentRevisionId  文档修订标识
         * @param text                Chunk 文本
         * @param metadata            Chunk 元数据
         */
        private ArtifactLine {
            metadata = Map.copyOf(metadata);
        }
    }
}
