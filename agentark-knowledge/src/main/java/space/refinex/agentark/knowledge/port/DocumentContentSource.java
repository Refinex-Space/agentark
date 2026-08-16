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

import java.io.IOException;
import java.io.InputStream;

/**
 * 延迟打开文档内容流，避免 Port 强迫应用把大文档完整载入内存。
 *
 * @author refinex
 */
@FunctionalInterface
public interface DocumentContentSource {

    /**
     * 打开一次性读取流，调用方必须关闭。
     *
     * @return 新打开的文档流
     * @throws IOException 对象存储读取失败时抛出
     */
    InputStream open() throws IOException;
}
