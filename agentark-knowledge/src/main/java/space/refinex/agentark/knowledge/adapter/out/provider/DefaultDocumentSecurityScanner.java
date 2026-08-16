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

package space.refinex.agentark.knowledge.adapter.out.provider;

import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.port.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipInputStream;

/**
 * 在 Parser 启动前校验媒体类型、大小、魔数、恶意内容和 ZIP 解压边界。
 *
 * @author refinex
 */
public final class DefaultDocumentSecurityScanner implements DocumentSecurityScanner {

    /**
     * 允许进入已配置 Parser 的媒体类型。
     */
    private final Set<String> allowedMediaTypes;

    /**
     * 单文件最大字节数。
     */
    private final long maxBytes;

    /**
     * 压缩包最大 Entry 数。
     */
    private final int maxArchiveEntries;

    /**
     * 压缩包最大解压字节数。
     */
    private final long maxExpandedBytes;

    /**
     * 最大解压倍率。
     */
    private final double maxCompressionRatio;

    /**
     * 真实恶意内容扫描器。
     */
    private final MalwareScanner malwareScanner;

    /**
     * 阻塞扫描执行器。
     */
    private final Executor executor;

    /**
     * 创建文档安全扫描器。
     *
     * @param allowedMediaTypes  允许媒体类型
     * @param maxBytes          最大文件字节数
     * @param maxArchiveEntries 最大压缩 Entry 数
     * @param maxExpandedBytes  最大解压字节数
     * @param maxCompressionRatio 最大解压倍率
     * @param malwareScanner    恶意内容扫描 Port
     * @param executor          阻塞扫描执行器
     */
    public DefaultDocumentSecurityScanner(
        Set<String> allowedMediaTypes,
        long maxBytes,
        int maxArchiveEntries,
        long maxExpandedBytes,
        double maxCompressionRatio,
        MalwareScanner malwareScanner,
        Executor executor) {
        this.allowedMediaTypes = Set.copyOf(Objects.requireNonNull(
            allowedMediaTypes, "allowedMediaTypes must not be null"));
        this.malwareScanner = Objects.requireNonNull(
            malwareScanner, "malwareScanner must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        if (this.allowedMediaTypes.isEmpty()
            || maxBytes < 1
            || maxArchiveEntries < 1
            || maxExpandedBytes < maxBytes
            || !Double.isFinite(maxCompressionRatio)
            || maxCompressionRatio < 1) {
            throw new IllegalArgumentException("document scanner limits are invalid");
        }
        this.maxBytes = maxBytes;
        this.maxArchiveEntries = maxArchiveEntries;
        this.maxExpandedBytes = maxExpandedBytes;
        this.maxCompressionRatio = maxCompressionRatio;
    }

    /**
     * 执行基础边界、魔数、压缩炸弹和恶意内容检查。
     *
     * @param revision 文档修订
     * @param resolver 受控内容解析器
     * @return 异步完成信号
     */
    @Override
    public CompletionStage<Void> scan(
        DocumentRevision revision, DocumentContentResolver resolver) {
        Objects.requireNonNull(revision, "revision must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        CompletionStage<Void> structural = CompletableFuture.runAsync(
            () -> scanStructure(revision, resolver), executor);
        return structural.thenCompose(ignored -> malwareScanner.scan(revision, resolver));
    }

    /**
     * 同步检查对象元数据、魔数与 ZIP 解压边界。
     *
     * @param revision 文档修订
     * @param resolver 内容解析器
     */
    private void scanStructure(DocumentRevision revision, DocumentContentResolver resolver) {
        String mediaType = revision.objectRef().mediaType();
        if (!allowedMediaTypes.contains(mediaType)
            || revision.objectRef().size() <= 0
            || revision.objectRef().size() > maxBytes) {
            throw new CompletionException(new SecurityException(
                "document media type or size is not allowed"));
        }
        try (InputStream input = resolver.open(revision)) {
            byte[] header = input.readNBytes(8);
            if (!matchesMagic(mediaType, header)) {
                throw new SecurityException("document magic does not match media type");
            }
        } catch (IOException exception) {
            throw new CompletionException("document signature scan failed", exception);
        }
        if (isZip(mediaType)) {
            scanArchive(revision, resolver);
        }
    }

    /**
     * 检查 ZIP Entry 数、路径、总解压大小和解压倍率。
     *
     * @param revision 文档修订
     * @param resolver 内容解析器
     */
    private void scanArchive(DocumentRevision revision, DocumentContentResolver resolver) {
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream archive = new ZipInputStream(resolver.open(revision))) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                entries++;
                String name = entry.getName();
                if (entries > maxArchiveEntries
                    || name == null
                    || name.startsWith("/")
                    || name.contains("..")) {
                    throw new SecurityException("archive entry boundary is invalid");
                }
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > maxExpandedBytes
                        || expanded > revision.objectRef().size() * maxCompressionRatio) {
                        throw new SecurityException("archive expansion limit exceeded");
                    }
                }
            }
        } catch (IOException exception) {
            throw new CompletionException("archive scan failed", exception);
        }
    }

    /**
     * 按声明媒体类型校验最小魔数。
     *
     * @param mediaType 媒体类型
     * @param header    文件前八字节
     * @return 匹配时为 {@code true}
     */
    private boolean matchesMagic(String mediaType, byte[] header) {
        if (isZip(mediaType)) {
            return header.length >= 4 && header[0] == 'P' && header[1] == 'K'
                && (header[2] == 3 || header[2] == 5 || header[2] == 7)
                && (header[3] == 4 || header[3] == 6 || header[3] == 8);
        }
        if ("application/pdf".equals(mediaType)) {
            return header.length >= 5 && header[0] == '%' && header[1] == 'P'
                && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        }
        return ("text/plain".equals(mediaType) || "text/markdown".equals(mediaType))
            && java.util.stream.IntStream.range(0, header.length).noneMatch(index -> header[index] == 0);
    }

    /**
     * 判断媒体类型是否基于 ZIP 容器。
     *
     * @param mediaType 媒体类型
     * @return ZIP 容器时为 {@code true}
     */
    private boolean isZip(String mediaType) {
        return "application/zip".equals(mediaType)
            || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            .equals(mediaType);
    }
}
