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
import space.refinex.agentark.knowledge.domain.ParserProfile;
import space.refinex.agentark.knowledge.port.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 在低堆上限、空环境变量、无文件路径输入的独立 JVM 中解析纯文本和 Markdown。
 *
 * <p>该适配器只执行仓库内固定解析入口，不加载文档携带的代码或第三方 Parser 插件。
 *
 * @author refinex
 */
public final class ProcessIsolatedTextParserSandbox implements ParserSandbox {

    /**
     * 允许当前固定解析器处理的媒体类型。
     */
    private static final Set<String> MEDIA_TYPES = Set.of("text/plain", "text/markdown");

    /**
     * 单文档最大输入字节数。
     */
    private final int maxBytes;

    /**
     * 子进程硬超时。
     */
    private final Duration timeout;

    /**
     * 子进程管理执行器。
     */
    private final Executor executor;

    /**
     * 创建受限文本解析 Sandbox。
     *
     * @param maxBytes 最大输入字节数，最多 64 MiB
     * @param timeout  正数且不超过五分钟的超时
     * @param executor 子进程管理执行器
     */
    public ProcessIsolatedTextParserSandbox(
        int maxBytes, Duration timeout, Executor executor) {
        if (maxBytes < 1 || maxBytes > 64 * 1024 * 1024) {
            throw new IllegalArgumentException("maxBytes must be between one byte and 64 MiB");
        }
        if (timeout == null
            || timeout.isZero()
            || timeout.isNegative()
            || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("timeout must be positive and at most five minutes");
        }
        this.maxBytes = maxBytes;
        this.timeout = timeout;
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * 在独立 JVM 解析固定文本格式，父进程只通过标准流传输不可变内容。
     *
     * @param revision 文档修订
     * @param profile  固定 Parser Profile
     * @param resolver 受控内容解析器
     * @return 异步规范化文档
     */
    @Override
    public CompletionStage<ParsedDocument> parse(
        DocumentRevision revision, ParserProfile profile, DocumentContentResolver resolver) {
        Objects.requireNonNull(revision, "revision must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        if (!MEDIA_TYPES.contains(revision.objectRef().mediaType())) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("isolated text parser does not support the media type"));
        }
        return CompletableFuture.supplyAsync(
            () -> invoke(revision, resolver), executor);
    }

    /**
     * 启动并监管受限解析子进程，超时或输出异常时强制终止当前进程。
     *
     * @param revision 文档修订
     * @param resolver 内容解析器
     * @return 规范化解析结果
     */
    private ParsedDocument invoke(
        DocumentRevision revision, DocumentContentResolver resolver) {
        Path workingDirectory = null;
        Process process = null;
        try {
            workingDirectory = Files.createTempDirectory("agentark-parser-");
            ProcessBuilder builder = new ProcessBuilder(
                javaExecutable(), "-Xmx96m", "-XX:+UseSerialGC", "-cp", absoluteClasspath(),
                RestrictedTextParserProcessMain.class.getName(), Integer.toString(maxBytes));
            builder.directory(workingDirectory.toFile());
            builder.environment().clear();
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            Process active = process;
            CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
                try {
                    return active.getInputStream().readNBytes(maxBytes + 1);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
            try (InputStream input = resolver.open(revision);
                 OutputStream target = process.getOutputStream()) {
                copyBounded(input, target);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("parser subprocess timed out");
            }
            byte[] parsed = output.get(10, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || parsed.length == 0 || parsed.length > maxBytes) {
                throw new IllegalStateException("parser subprocess returned an invalid result");
            }
            String text = new String(parsed, StandardCharsets.UTF_8);
            int sections = text.split("\\n\\n").length;
            return new ParsedDocument(
                revision.id(), text,
                Map.of("section_count", Integer.toString(sections),
                    "source_trust", "UNTRUSTED_EXTERNAL"));
        } catch (IOException exception) {
            throw new CompletionException("parser subprocess I/O failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException("parser subprocess was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new CompletionException("parser subprocess output failed", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (workingDirectory != null) {
                try {
                    Files.deleteIfExists(workingDirectory);
                } catch (IOException ignored) {
                    // 临时空目录删除失败不改变摄取事实，交由操作系统临时目录清理。
                }
            }
        }
    }

    /**
     * 流式复制文档并拒绝超过固定上限的输入。
     *
     * @param input  原文件流
     * @param output 子进程标准输入
     * @throws IOException 读写或大小超限时抛出
     */
    private void copyBounded(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("parser input exceeds configured size limit");
            }
            output.write(buffer, 0, read);
        }
    }

    /**
     * 返回当前 JDK 的绝对 Java 可执行文件路径。
     *
     * @return Java 可执行文件
     */
    private String javaExecutable() {
        String executable = System.getProperty("os.name", "").startsWith("Windows")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable)
            .toAbsolutePath().toString();
    }

    /**
     * 把当前类路径转换为绝对路径，避免子进程临时工作目录改变相对语义。
     *
     * @return 平台分隔的绝对类路径
     */
    private String absoluteClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(
                java.util.regex.Pattern.quote(File.pathSeparator)))
            .map(path -> Path.of(path).toAbsolutePath().normalize().toString())
            .collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }
}
