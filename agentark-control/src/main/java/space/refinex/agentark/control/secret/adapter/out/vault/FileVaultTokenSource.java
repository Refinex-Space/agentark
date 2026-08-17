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

package space.refinex.agentark.control.secret.adapter.out.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 从工作负载只读挂载文件按请求读取 Vault Token，拒绝符号链接和超大内容。
 *
 * @author refinex
 */
public final class FileVaultTokenSource implements VaultTokenSource {

    /** 允许的令牌文件最大字节数。 */
    private static final long MAX_TOKEN_BYTES = 8192;

    /** 规范化绝对令牌文件。 */
    private final Path tokenFile;

    /** @param tokenFile 专用绝对令牌文件 */
    public FileVaultTokenSource(Path tokenFile) {
        if (tokenFile == null || !tokenFile.isAbsolute()) {
            throw new IllegalArgumentException("tokenFile must be absolute");
        }
        this.tokenFile = tokenFile.normalize();
    }

    /**
     * 读取并裁剪令牌；每次请求重新读取以支持无重启轮换。
     *
     * @return 当前令牌字符
     * @throws IOException 文件不存在、是符号链接、不是普通文件或大小异常时抛出
     */
    @Override
    public char[] load() throws IOException {
        if (Files.isSymbolicLink(tokenFile)
            || !Files.isRegularFile(tokenFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("vault token file is not a safe regular file");
        }
        long size = Files.size(tokenFile);
        if (size < 1 || size > MAX_TOKEN_BYTES) {
            throw new IOException("vault token file size is invalid");
        }
        String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        if (token.isEmpty() || token.length() > MAX_TOKEN_BYTES
            || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IOException("vault token file content is invalid");
        }
        return token.toCharArray();
    }
}
