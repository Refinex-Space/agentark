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

package space.refinex.agentark.control.secret;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.refinex.agentark.control.secret.adapter.out.local.LocalFileSecretResolver;
import space.refinex.agentark.control.secret.domain.*;
import space.refinex.agentark.kernel.id.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/**
 * 验证开发 Local File Provider 的根目录约束和 ResolvedSecret 清零生命周期。
 *
 * @author refinex
 */
class LocalFileSecretResolverTest {

    /** JUnit 创建的独立临时根目录。 */
    @TempDir
    Path root;

    /** 创建 Local File Resolver 测试实例。 */
    LocalFileSecretResolverTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 验证读取结果不字符串化，关闭后不能再次复制。
     *
     * @throws Exception 临时文件或 Resolver 初始化失败时抛出
     */
    @Test
    void resolvesOnlyInsideRootAndClearsOnClose() throws Exception {
        Files.writeString(root.resolve("model-key"), "temporary-value", StandardCharsets.UTF_8);
        LocalFileSecretResolver resolver = new LocalFileSecretResolver(root);
        Instant now = Instant.now();
        SecretMetadata metadata = new SecretMetadata(
            SecretMetadataId.generate(), OrganizationId.generate(), ProjectId.generate(),
            "model-key", "本地模型凭据", SecretProviderType.LOCAL_FILE, "model-key", "",
            SecretScope.PROJECT, SecretMetadataStatus.ENABLED, 0, now, now);

        var resolved = resolver.resolve(metadata);
        char[] copy = resolved.copy();
        assertThat(copy).containsExactly("temporary-value".toCharArray());
        assertThat(resolved.toString()).isEqualTo("[REDACTED]");
        java.util.Arrays.fill(copy, '\0');
        resolved.close();

        assertThatThrownBy(resolved::copy).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 验证非法 UTF-8 字节不会被替换后当作 Secret 返回。
     *
     * @throws Exception 临时文件或 Resolver 初始化失败时抛出
     */
    @Test
    void rejectsMalformedUtf8WithoutReplacement() throws Exception {
        Files.write(root.resolve("malformed-key"), new byte[] {(byte) 0xC3, (byte) 0x28});
        LocalFileSecretResolver resolver = new LocalFileSecretResolver(root);
        Instant now = Instant.now();
        SecretMetadata metadata = new SecretMetadata(
            SecretMetadataId.generate(), OrganizationId.generate(), ProjectId.generate(),
            "malformed-key", "非法编码凭据", SecretProviderType.LOCAL_FILE, "malformed-key", "",
            SecretScope.PROJECT, SecretMetadataStatus.ENABLED, 0, now, now);

        assertThatThrownBy(() -> resolver.resolve(metadata)).isInstanceOf(IOException.class);
    }
}
