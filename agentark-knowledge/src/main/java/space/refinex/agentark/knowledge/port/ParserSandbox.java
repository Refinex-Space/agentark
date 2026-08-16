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
import space.refinex.agentark.knowledge.domain.ParserProfile;

import java.util.concurrent.CompletionStage;

/**
 * 定义不受信文档解析的隔离执行边界，生产实现不得在 HTTP 或 Worker 主进程内加载解析器插件。
 *
 * @author refinex
 */
@FunctionalInterface
public interface ParserSandbox {

    /**
     * 在受限子进程或等价 Sandbox 中解析文档并返回规范化文本。
     *
     * @param revision 文档修订
     * @param profile  固定 Parser Profile
     * @param resolver 受控对象内容解析器
     * @return 异步解析结果
     */
    CompletionStage<ParsedDocument> parse(
        DocumentRevision revision, ParserProfile profile, DocumentContentResolver resolver);
}
