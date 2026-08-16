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

package space.refinex.agentark.knowledge.port;

import space.refinex.agentark.knowledge.domain.DocumentRevision;

import java.io.IOException;
import java.io.InputStream;

/**
 * 定义按不可变 ObjectRef 重复打开文档流的 Worker Port，不暴露签名 URL。
 *
 * @author refinex
 */
@FunctionalInterface
public interface DocumentContentResolver {

    /**
     * 打开由当前 Worker Object Store 拥有的一次性只读流。
     *
     * @param revision 文档修订
     * @return 调用方必须关闭的流
     * @throws IOException 对象不存在、越权或完整性校验失败时抛出
     */
    InputStream open(DocumentRevision revision) throws IOException;
}
