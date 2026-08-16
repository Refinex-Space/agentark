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

package space.refinex.agentark.control.secret.adapter.out.local;

import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.application.port.SecretResolver;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.control.secret.domain.SecretMetadataStatus;
import space.refinex.agentark.control.secret.domain.SecretProviderType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 仅在 local Profile 显式启用的文件 Secret Resolver，拒绝符号链接和根目录逃逸。
 *
 * @author refinex
 */
public final class LocalFileSecretResolver implements SecretResolver {

    /**
     * 单个本地 Secret 最大字节数。
     */
    private static final long MAX_SECRET_BYTES = 64 * 1024;

    /**
     * 规范化绝对根目录。
     */
    private final Path root;

    /**
     * @param root 本地 Secret 专用根目录
     * @throws IOException 根目录创建或规范化失败时抛出
     */
    public LocalFileSecretResolver(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        this.root = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * @param metadata 已授权且启用的 Local File 元数据
     * @return 使用后必须关闭的字符数组
     * @throws IOException 文件缺失、越界、符号链接或读取失败时抛出
     */
    @Override
    public ResolvedSecret resolve(SecretMetadata metadata) throws IOException {
        if (metadata.provider() != SecretProviderType.LOCAL_FILE
            || metadata.status() != SecretMetadataStatus.ENABLED) {
            throw new IOException("secret metadata is not enabled for local provider");
        }
        Path candidate = root.resolve(metadata.externalPath()).normalize();
        if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)
            || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("local secret path is outside the configured root");
        }
        long size = Files.size(candidate);
        if (size < 1 || size > MAX_SECRET_BYTES) {
            throw new IOException("local secret size is outside the allowed range");
        }
        byte[] encoded = Files.readAllBytes(candidate);
        CharBuffer decoded = CharBuffer.allocate(encoded.length);
        char[] value = null;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            CoderResult decodeResult = decoder.decode(ByteBuffer.wrap(encoded), decoded, true);
            if (decodeResult.isError()) {
                decodeResult.throwException();
            }
            CoderResult flushResult = decoder.flush(decoded);
            if (flushResult.isError()) {
                flushResult.throwException();
            }
            decoded.flip();
            value = new char[decoded.remaining()];
            decoded.get(value);
            return new ResolvedSecret(value);
        } finally {
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(decoded.array(), '\0');
            if (value != null) {
                Arrays.fill(value, '\0');
            }
        }
    }
}
