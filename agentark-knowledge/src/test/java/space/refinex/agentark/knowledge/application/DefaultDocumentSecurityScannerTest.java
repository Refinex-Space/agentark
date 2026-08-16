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

package space.refinex.agentark.knowledge.application;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.adapter.out.provider.DefaultDocumentSecurityScanner;
import space.refinex.agentark.knowledge.domain.DocumentRevision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证安全扫描在恶意内容 Provider 前拒绝媒体魔数不匹配和压缩炸弹。
 *
 * @author refinex
 */
class DefaultDocumentSecurityScannerTest {

    /** 验证伪造 PDF 魔数会终止 Attempt 且不会调用恶意内容 Provider。 */
    @Test
    void rejectsMismatchedMagicBeforeMalwareScan() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus.INGESTING);
        byte[] content = "not-a-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        DocumentRevision revision = revision(
            fixture, content, "application/pdf", "forged.pdf");
        AtomicBoolean malwareCalled = new AtomicBoolean();
        DefaultDocumentSecurityScanner scanner = scanner(
            Set.of("application/pdf"), (ignored, resolver) -> {
                malwareCalled.set(true);
                return CompletableFuture.completedFuture(null);
            });

        assertThatThrownBy(() -> scanner.scan(
            revision, ignored -> new ByteArrayInputStream(content)).toCompletableFuture().join())
            .hasRootCauseInstanceOf(SecurityException.class);
        assertThat(malwareCalled).isFalse();
    }

    /** 验证超过解压字节上限的 ZIP 在 Parser 前被拒绝。 */
    @Test
    void rejectsArchiveExpansionBomb() throws Exception {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus.INGESTING);
        byte[] content = compressedZeros(4096);
        DocumentRevision revision = revision(
            fixture, content, "application/zip", "bomb.zip");
        DefaultDocumentSecurityScanner scanner = scanner(
            Set.of("application/zip"), (ignored, resolver) ->
                CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> scanner.scan(
            revision, ignored -> new ByteArrayInputStream(content)).toCompletableFuture().join())
            .hasRootCauseInstanceOf(SecurityException.class);
    }

    /**
     * 创建固定安全边界的扫描器。
     *
     * @param mediaTypes    允许媒体类型
     * @param malwareScanner 恶意内容扫描替身
     * @return 文档安全扫描器
     */
    private DefaultDocumentSecurityScanner scanner(
        Set<String> mediaTypes,
        space.refinex.agentark.knowledge.port.MalwareScanner malwareScanner) {
        return new DefaultDocumentSecurityScanner(
            mediaTypes, 1024, 8, 1024, 4, malwareScanner, Runnable::run);
    }

    /**
     * 使用夹具租户创建指定内容类型的不可变文档修订。
     *
     * @param fixture  测试夹具
     * @param content  原始内容
     * @param mediaType 声明媒体类型
     * @param fileName 原文件名
     * @return 文档修订
     */
    private DocumentRevision revision(
        Phase14Fixtures.Fixture fixture, byte[] content, String mediaType, String fileName) {
        return new DocumentRevision(
            fixture.documentRevision().id(), fixture.organizationId(), fixture.projectId(),
            fixture.documentRevision().knowledgeBaseId(), fixture.document().id(), 1, fileName,
            ObjectRef.of("object://knowledge/" + fileName, Checksum.sha256(content),
                content.length, mediaType), fixture.now());
    }

    /**
     * 创建小体积但解压后超过限制的 ZIP 内容。
     *
     * @param size 解压字节数
     * @return ZIP 字节
     * @throws Exception ZIP 构造失败时抛出
     */
    private byte[] compressedZeros(int size) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("content.txt"));
            zip.write(new byte[size]);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
