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
 * 定义文档原始内容的来源类型，本阶段只执行受控上传。
 *
 * @author refinex
 */
public enum DataSourceType {

    /**
     * 由 Public API 上传并写入 Object Store。
     */
    UPLOAD,

    /**
     * 由后续连接器从受控 URI 拉取，本阶段仅保存元数据。
     */
    URI,

    /**
     * 由后续 Provider 连接器同步，本阶段仅保存元数据。
     */
    CONNECTOR
}
