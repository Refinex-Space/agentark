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

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 在 Java UUIDv7 与 MySQL BINARY(16) 之间执行无损转换，并拒绝其他 UUID 版本。
 *
 * @author refinex
 */
public final class UuidV7BinaryTypeHandler extends BaseTypeHandler<UUID> {

    /**
     * 创建无状态 UUIDv7 二进制类型处理器。
     */
    public UuidV7BinaryTypeHandler() {
        // MyBatis 通过公开无参构造器创建类型处理器。
    }

    /**
     * 将 UUIDv7 写为固定 16 字节二进制值。
     *
     * @param statement JDBC 预编译语句
     * @param index     参数下标，从 1 开始
     * @param parameter UUIDv7 参数
     * @param jdbcType  调用方声明的 JDBC 类型
     * @throws SQLException             JDBC 写入失败时抛出
     * @throws IllegalArgumentException 当 UUID 不是 RFC 9562 UUIDv7 时抛出
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, UUID parameter, JdbcType jdbcType)
        throws SQLException {
        requireUuidV7(parameter);
        statement.setBytes(index, toBytes(parameter));
    }

    /**
     * 按列名读取 BINARY(16) UUIDv7。
     *
     * @param resultSet  JDBC 结果集
     * @param columnName 列名
     * @return 列为空时返回 {@code null}
     * @throws SQLException 二进制长度或 UUID 版本非法、JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromBytes(resultSet.getBytes(columnName));
    }

    /**
     * 按列下标读取 BINARY(16) UUIDv7。
     *
     * @param resultSet   JDBC 结果集
     * @param columnIndex 列下标，从 1 开始
     * @return 列为空时返回 {@code null}
     * @throws SQLException 二进制长度或 UUID 版本非法、JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromBytes(resultSet.getBytes(columnIndex));
    }

    /**
     * 从存储过程输出参数读取 BINARY(16) UUIDv7。
     *
     * @param statement   JDBC 存储过程语句
     * @param columnIndex 输出参数下标，从 1 开始
     * @return 参数为空时返回 {@code null}
     * @throws SQLException 二进制长度或 UUID 版本非法、JDBC 读取失败时抛出
     */
    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromBytes(statement.getBytes(columnIndex));
    }

    /**
     * 将 UUID 转换为网络字节序的 16 字节表示。
     *
     * @param value UUIDv7
     * @return 16 字节数组
     */
    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
            .putLong(value.getMostSignificantBits())
            .putLong(value.getLeastSignificantBits())
            .array();
    }

    /**
     * 将数据库二进制值还原并校验为 UUIDv7。
     *
     * @param bytes 可为空的数据库值
     * @return UUIDv7 或 {@code null}
     * @throws SQLException 当字节长度或 UUID 版本非法时抛出
     */
    private UUID fromBytes(byte[] bytes) throws SQLException {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != 16) {
            throw new SQLException("UUIDv7 BINARY value must contain exactly 16 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID value = new UUID(buffer.getLong(), buffer.getLong());
        try {
            requireUuidV7(value);
            return value;
        } catch (IllegalArgumentException invalid) {
            throw new SQLException("database value is not an RFC 9562 UUIDv7", invalid);
        }
    }

    /**
     * 校验 UUID 版本和 RFC Variant。
     *
     * @param value 待校验 UUID
     * @throws IllegalArgumentException 当不是 UUIDv7 或 RFC Variant 时抛出
     */
    private void requireUuidV7(UUID value) {
        if (value == null || value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException("value must be an RFC 9562 UUIDv7");
        }
    }
}
