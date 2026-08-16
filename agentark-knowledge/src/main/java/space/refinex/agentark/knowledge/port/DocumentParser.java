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
 * 定义把原文件异步解析为平台中立文本的 Provider Port。
 *
 * @author refinex
 */
public interface DocumentParser {

    /**
     * 异步解析文档，Provider 必须关闭通过内容源打开的流。
     *
     * @param revision      不可变文档修订
     * @param profile       已发布 Parser Profile
     * @param contentSource 延迟内容源
     * @return 异步解析结果
     */
    CompletionStage<ParsedDocument> parse(
        DocumentRevision revision, ParserProfile profile, DocumentContentSource contentSource);
}
