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

package space.refinex.agentark.knowledge.domain;

/**
 * 定义 Parser、Chunk、Embedding 与 Retrieval Profile 不可变版本的发布状态。
 *
 * @author refinex
 */
public enum KnowledgeProfileStatus {

    /**
     * 尚未允许 Knowledge Revision 使用的草稿。
     */
    DRAFT,

    /**
     * 可用于创建 Knowledge Revision 的已发布版本。
     */
    PUBLISHED,

    /**
     * 停止新增引用但保留历史引用的版本。
     */
    DEPRECATED
}
