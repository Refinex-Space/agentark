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

package space.refinex.agentark.foundation.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 以合法 JSON 文本读写 Jackson JsonNode，避免使用不受约束的 Map 作为稳定持久化模型。
 *
 * @author refinex
 */
public final class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    /**
     * 应用统一配置的 Jackson 3 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 JSON 节点类型处理器。
     *
     * @param jsonMapper 应用统一 JSON 映射器
     */
    public JsonNodeTypeHandler(JsonMapper jsonMapper) {
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 将 JsonNode 写为合法 JSON 文本。
     *
     * @param statement JDBC 预编译语句
     * @param index     参数下标，从 1 开始
     * @param parameter JSON 节点
     * @param jdbcType  调用方声明的 JDBC 类型
     * @throws SQLException JSON 序列化或 JDBC 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, JsonNode parameter, JdbcType jdbcType)
        throws SQLException {
        try {
            statement.setString(index, jsonMapper.writeValueAsString(parameter));
        } catch (JacksonException error) {
            throw new SQLException("failed to serialize JSON column", error);
        }
    }

    /**
     * 按列名读取 JSON 节点。
     *
     * @param resultSet  JDBC 结果集
     * @param columnName 列名
     * @return 列为空时返回 {@code null}
     * @throws SQLException JSON 解析或 JDBC 读取失败时抛出
     */
    @Override
    public JsonNode getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    /**
     * 按列下标读取 JSON 节点。
     *
     * @param resultSet   JDBC 结果集
     * @param columnIndex 列下标，从 1 开始
     * @return 列为空时返回 {@code null}
     * @throws SQLException JSON 解析或 JDBC 读取失败时抛出
     */
    @Override
    public JsonNode getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    /**
     * 从存储过程输出参数读取 JSON 节点。
     *
     * @param statement   JDBC 存储过程语句
     * @param columnIndex 输出参数下标，从 1 开始
     * @return 参数为空时返回 {@code null}
     * @throws SQLException JSON 解析或 JDBC 读取失败时抛出
     */
    @Override
    public JsonNode getNullableResult(CallableStatement statement, int columnIndex)
        throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    /**
     * 解析可空 JSON 文本。
     *
     * @param value 数据库 JSON 文本
     * @return JSON 节点或 {@code null}
     * @throws SQLException JSON 不合法时抛出
     */
    private JsonNode parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return jsonMapper.readTree(value);
        } catch (JacksonException error) {
            throw new SQLException("failed to parse JSON column", error);
        }
    }
}
