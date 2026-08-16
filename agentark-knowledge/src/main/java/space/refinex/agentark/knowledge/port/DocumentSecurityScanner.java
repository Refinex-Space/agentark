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

import java.util.concurrent.CompletionStage;

/**
 * 定义文件类型、大小、恶意内容和压缩炸弹检查 Port。
 *
 * @author refinex
 */
@FunctionalInterface
public interface DocumentSecurityScanner {

    /**
     * 在解析子进程启动前完成全部安全检查，失败必须以异常终止当前 Attempt。
     *
     * @param revision 文档修订
     * @param resolver 受控对象内容解析器
     * @return 异步完成信号
     */
    CompletionStage<Void> scan(DocumentRevision revision, DocumentContentResolver resolver);
}
