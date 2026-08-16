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

package space.refinex.agentark.control.catalog.domain;

/**
 * 定义不可变版本在创建时携带的发布语义；状态不授权修改版本内容。
 *
 * @author refinex
 */
public enum CatalogVersionStatus {
    /**
     * 尚未作为稳定引用公开的草稿版本。
     */
    DRAFT,
    /**
     * 可被 Agent Draft 和 Snapshot 引用的已发布版本。
     */
    PUBLISHED,
    /**
     * 只供历史引用读取的归档版本。
     */
    ARCHIVED
}

