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

package space.refinex.agentark.knowledge.adapter.out.persistence;

/**
 * 以受控枚举限定四张 Profile 表，禁止调用方注入动态表名。
 *
 * @author refinex
 */
enum ProfileTable {

    /**
     * Parser Profile 表。
     */
    PARSER("parser_profile", false),

    /**
     * Chunk Profile 表。
     */
    CHUNK("chunk_profile", false),

    /**
     * Embedding Profile 表，包含 SecretRef 列。
     */
    EMBEDDING("embedding_profile", true),

    /**
     * Retrieval Profile 表。
     */
    RETRIEVAL("retrieval_profile", false);

    /**
     * 受控数据库表名。
     */
    private final String tableName;

    /**
     * 是否包含凭据引用列。
     */
    private final boolean credentialColumn;

    /**
     * 创建受控 Profile 表描述。
     *
     * @param tableName        固定表名
     * @param credentialColumn 是否包含凭据引用列
     */
    ProfileTable(String tableName, boolean credentialColumn) {
        this.tableName = tableName;
        this.credentialColumn = credentialColumn;
    }

    /**
     * @return 固定表名
     */
    String tableName() {
        return tableName;
    }

    /**
     * @return 包含凭据引用列时返回 {@code true}
     */
    boolean credentialColumn() {
        return credentialColumn;
    }
}
