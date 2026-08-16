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

package space.refinex.agentark.control.catalog.application;

import java.util.List;
import java.util.Map;

/**
 * 表示完成分类约束和敏感字段检查后的规范 JSON 及 MCP Tool 快照载荷。
 *
 * @param canonicalJson 键顺序稳定的规范 JSON
 * @param toolPayloads  MCP Tool Descriptor 载荷；其他分类为空
 * @author refinex
 */
record CatalogValidatedPayload(
    String canonicalJson, List<Map<String, Object>> toolPayloads) {

    /**
     * 防御性复制 MCP Tool 载荷列表。
     */
    CatalogValidatedPayload {
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new IllegalArgumentException("canonicalJson must not be blank");
        }
        toolPayloads = List.copyOf(toolPayloads);
    }
}

