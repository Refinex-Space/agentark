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

package space.refinex.agentark.runtime.adapter.out.storage;

import space.refinex.agentark.foundation.storage.ObjectNamespace;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.storage.PutObjectCommand;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;
import space.refinex.agentark.runtime.port.RuntimePayloadExternalizer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * 将超过 64 KiB 的 Runtime Event JSON 写入服务端固定命名空间 Object Store。
 *
 * @author refinex
 */
public final class ObjectStoreRuntimePayloadExternalizer
    implements RuntimePayloadExternalizer {

    /**
     * Event 内联存储最大 UTF-8 字节数。
     */
    private static final int INLINE_LIMIT = 65_536;

    /**
     * Runtime Event 固定对象命名空间。
     */
    private static final ObjectNamespace NAMESPACE = new ObjectNamespace("runtime-event");

    /**
     * 受控对象存储。
     */
    private final ObjectStore objectStore;

    /**
     * 创建 Event 载荷外置器。
     *
     * @param objectStore Object Store
     */
    public ObjectStoreRuntimePayloadExternalizer(ObjectStore objectStore) {
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
    }

    /**
     * 小载荷保持内联，大载荷按 Hash 校验写入 Object Store。
     *
     * @param json JSON 文本
     * @return Runtime 载荷
     */
    @Override
    public RuntimePayload externalize(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("runtime event payload must not be blank");
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= INLINE_LIMIT) {
            return RuntimePayload.inline(json);
        }
        try {
            return RuntimePayload.external(objectStore.put(new PutObjectCommand(
                NAMESPACE, new ByteArrayInputStream(bytes), bytes.length,
                "application/json", Optional.of(Checksum.sha256(bytes)))));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("runtime event payload cannot be externalized", exception);
        }
    }
}
