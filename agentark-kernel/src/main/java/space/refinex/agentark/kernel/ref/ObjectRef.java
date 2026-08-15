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

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表示带完整性和媒体元数据的不可变对象存储引用；URI 不得携带任何授权材料。
 *
 * @param uri       仅允许 object、s3、oss 或 cos Scheme 的对象 URI
 * @param checksum  对象内容的 SHA-256 校验和
 * @param size      对象字节数，必须大于等于零
 * @param mediaType 具体媒体类型
 * @author refinex
 */
public record ObjectRef(URI uri, Checksum checksum, long size, String mediaType) {

    /**
     * 平台允许持久化到 Snapshot 的对象存储 Scheme 白名单。
     */
    private static final Set<String> SCHEMES = Set.of("object", "s3", "oss", "cos");

    /**
     * 具体媒体类型的语法约束，不接受通配类型。
     */
    private static final Pattern MEDIA_TYPE =
        Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*");

    /**
     * 校验并创建对象引用，拒绝 User Info、Query 和 Fragment，防止签名或凭据入库。
     *
     * @param uri       对象 URI
     * @param checksum  内容校验和
     * @param size      对象字节数
     * @param mediaType 具体媒体类型
     * @throws NullPointerException     当 URI 或校验和为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 URI、大小或媒体类型不满足约束时抛出
     */
    public ObjectRef {
        Objects.requireNonNull(uri, "ObjectRef uri must not be null");
        Objects.requireNonNull(checksum, "ObjectRef checksum must not be null");
        if (!SCHEMES.contains(uri.getScheme())
            || uri.getRawAuthority() == null
            || uri.getRawAuthority().isBlank()
            || uri.getRawPath() == null
            || uri.getRawPath().length() < 2
            || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                "ObjectRef uri must be object/s3/oss/cos without credentials, query, or fragment");
        }
        if (size < 0) {
            throw new IllegalArgumentException("ObjectRef size must not be negative");
        }
        if (mediaType == null || !MEDIA_TYPE.matcher(mediaType).matches()) {
            throw new IllegalArgumentException("ObjectRef mediaType must be a concrete media type");
        }
    }

    /**
     * 从字符串 URI 构造对象引用，并保留其余字段的强校验错误语义。
     *
     * @param uri       对象 URI 字符串
     * @param checksum  内容校验和
     * @param size      对象字节数
     * @param mediaType 具体媒体类型
     * @return 通过校验的对象引用
     * @throws IllegalArgumentException 当 URI 无法解析或任一字段不合法时抛出
     */
    public static ObjectRef of(String uri, Checksum checksum, long size, String mediaType) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("ObjectRef uri must not be blank");
        }
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("ObjectRef uri is invalid", exception);
        }
        return new ObjectRef(parsed, checksum, size, mediaType);
    }
}
