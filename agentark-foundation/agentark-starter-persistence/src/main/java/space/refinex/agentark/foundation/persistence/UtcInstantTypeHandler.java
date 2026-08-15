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

import java.sql.*;
import java.time.Instant;

/**
 * 在 UTC 时间线的 Instant 与 JDBC Timestamp 之间转换，不使用 JVM 默认时区。
 *
 * @author refinex
 */
public final class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {

    /**
     * 创建无状态 UTC Instant 类型处理器。
     */
    public UtcInstantTypeHandler() {
        // MyBatis 通过公开无参构造器创建类型处理器。
    }

    /**
     * 将 Instant 写为 JDBC Timestamp。
     *
     * @param statement JDBC 预编译语句
     * @param index     参数下标，从 1 开始
     * @param parameter UTC 时间线时刻
     * @param jdbcType  调用方声明的 JDBC 类型
     * @throws SQLException JDBC 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, Instant parameter, JdbcType jdbcType)
        throws SQLException {
        statement.setTimestamp(index, Timestamp.from(parameter));
    }

    /**
     * 按列名读取 UTC Instant。
     *
     * @param resultSet  JDBC 结果集
     * @param columnName 列名
     * @return 列为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toInstant(resultSet.getTimestamp(columnName));
    }

    /**
     * 按列下标读取 UTC Instant。
     *
     * @param resultSet   JDBC 结果集
     * @param columnIndex 列下标，从 1 开始
     * @return 列为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toInstant(resultSet.getTimestamp(columnIndex));
    }

    /**
     * 从存储过程输出参数读取 UTC Instant。
     *
     * @param statement   JDBC 存储过程语句
     * @param columnIndex 输出参数下标，从 1 开始
     * @return 参数为空时返回 {@code null}
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex)
        throws SQLException {
        return toInstant(statement.getTimestamp(columnIndex));
    }

    /**
     * 将可空 Timestamp 转换为 Instant。
     *
     * @param timestamp 数据库时间值
     * @return UTC Instant 或 {@code null}
     */
    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
