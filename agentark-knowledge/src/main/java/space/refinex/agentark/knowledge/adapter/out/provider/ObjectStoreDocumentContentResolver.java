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

import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.port.DocumentContentResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 使用 Worker 自有 Object Store 凭据解析原文件引用，不生成或持久化签名 URL。
 *
 * @author refinex
 */
public final class ObjectStoreDocumentContentResolver implements DocumentContentResolver {

    /**
     * 工作进程对象存储。
     */
    private final ObjectStore objectStore;

    /**
     * 创建对象内容解析器。
     *
     * @param objectStore Worker Object Store
     */
    public ObjectStoreDocumentContentResolver(ObjectStore objectStore) {
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
    }

    /**
     * 打开当前 Store 拥有的不可变原文件。
     *
     * @param revision 文档修订
     * @return 调用方关闭的输入流
     * @throws IOException 对象不存在、越权或读取失败时抛出
     */
    @Override
    public InputStream open(DocumentRevision revision) throws IOException {
        Objects.requireNonNull(revision, "revision must not be null");
        return objectStore.get(revision.objectRef());
    }
}
