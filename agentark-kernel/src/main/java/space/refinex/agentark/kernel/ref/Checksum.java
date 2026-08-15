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

package space.refinex.agentark.kernel.ref;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 表示不可变快照与对象引用使用的规范 SHA-256 校验和。
 *
 * @param value 形如 {@code sha256:<64 位小写十六进制>} 的规范值
 * @author refinex
 */
public record Checksum(String value) {

    /**
     * 规范 SHA-256 字符串的完整格式约束。
     */
    private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * 校验并创建校验和。
     *
     * @param value 规范 SHA-256 字符串
     * @throws IllegalArgumentException 当值不是规范 SHA-256 字符串时抛出
     */
    public Checksum {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Checksum must use canonical lowercase sha256:<64-hex> form");
        }
    }

    /**
     * 对字节内容计算 SHA-256；计算前复制输入，避免调用期间被并发修改。
     *
     * @param content 待计算的原始字节
     * @return 规范 SHA-256 校验和
     * @throws IllegalArgumentException 当内容为 {@code null} 时抛出
     * @throws IllegalStateException    当当前 JDK 不提供 SHA-256 算法时抛出
     */
    public static Checksum sha256(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.clone());
            return new Checksum("sha256:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    /**
     * 使用 UTF-8 编码对文本计算 SHA-256。
     *
     * @param content 待计算文本
     * @return 规范 SHA-256 校验和
     * @throws IllegalArgumentException 当内容为 {@code null} 时抛出
     */
    public static Checksum sha256(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 返回不含算法前缀的 64 位小写十六进制摘要。
     *
     * @return SHA-256 十六进制摘要
     */
    public String hex() {
        return value.substring("sha256:".length());
    }

    /**
     * 返回包含算法前缀的规范值。
     *
     * @return 规范 SHA-256 字符串
     */
    @Override
    public String toString() {
        return value;
    }
}
