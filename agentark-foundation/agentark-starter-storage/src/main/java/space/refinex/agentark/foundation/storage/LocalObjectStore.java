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

package space.refinex.agentark.foundation.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 将对象安全存放在专用本地根目录，使用原子移动、SHA-256 校验和严格引用归属检查。
 *
 * @author refinex
 */
public final class LocalObjectStore implements ObjectStore {

  /** 生成 UTC 年月目录的固定格式。 */
  private static final DateTimeFormatter MONTH =
      DateTimeFormatter.ofPattern("uuuu/MM").withZone(ZoneOffset.UTC);

  /** 规范化后的专用根目录。 */
  private final Path root;

  /** 当前 Store 拥有的 ObjectRef Authority。 */
  private final String authority;

  /** 单对象最大字节数。 */
  private final long maxObjectSize;

  /** 临时签名最大 TTL。 */
  private final Duration maxSignTtl;

  /** 仅当前进程 Local Profile 使用的随机 HMAC 密钥，不写配置或磁盘。 */
  private final byte[] localSigningKey;

  /**
   * 创建 Local Object Store 并初始化专用根目录。
   *
   * @param properties 已绑定的 Local 存储属性
   * @throws IOException 根目录创建失败时抛出
   * @throws IllegalStateException 当启用后未配置 Authority 时抛出
   */
  public LocalObjectStore(AgentArkStorageProperties properties) throws IOException {
    java.util.Objects.requireNonNull(properties, "properties must not be null");
    if (properties.getAuthority() == null) {
      throw new IllegalStateException("storage authority must be configured");
    }
    this.root = requireSafeRoot(properties.getRoot());
    this.authority = properties.getAuthority();
    this.maxObjectSize = properties.getMaxObjectSize();
    this.maxSignTtl = properties.getMaxSignTtl();
    this.localSigningKey = new byte[32];
    new SecureRandom().nextBytes(localSigningKey);
    Files.createDirectories(root);
  }

  /**
   * 流式写入对象，验证实际大小和可选 SHA-256 后原子发布。
   *
   * @param command 写入命令
   * @return 不含授权材料的对象引用
   * @throws IOException 读取、写入、大小或校验和验证失败时抛出
   */
  @Override
  public ObjectRef put(PutObjectCommand command) throws IOException {
    java.util.Objects.requireNonNull(command, "command must not be null");
    if (command.size() > maxObjectSize) {
      IOException sizeError = new IOException("object exceeds configured size limit");
      closeBeforeReject(command.content(), sizeError);
      throw sizeError;
    }
    String relative =
        command.namespace().value() + "/" + MONTH.format(Instant.now()) + "/" + UUID.randomUUID();
    Path target = resolveRelative(relative);
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
    try {
      WriteResult result = write(command.content(), temporary, maxObjectSize);
      if (result.size() != command.size()) {
        throw new IOException("object size does not match declared size");
      }
      Checksum checksum = new Checksum("sha256:" + result.sha256Hex());
      if (command.expectedChecksum().filter(expected -> !expected.equals(checksum)).isPresent()) {
        throw new IOException("object checksum does not match expected checksum");
      }
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return ObjectRef.of(
          "object://" + authority + "/" + relative, checksum, result.size(), command.contentType());
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  /**
   * 打开当前 Store 拥有对象的只读流。
   *
   * @param ref 对象引用
   * @return 需要调用方关闭的输入流
   * @throws IOException 引用越权或对象不存在时抛出
   */
  @Override
  public InputStream get(ObjectRef ref) throws IOException {
    return Files.newInputStream(pathFor(ref), StandardOpenOption.READ);
  }

  /**
   * 返回当前 Store 拥有对象的元数据并验证文件大小与引用一致。
   *
   * @param ref 对象引用
   * @return 对象元数据
   * @throws IOException 引用越权、对象不存在或大小不一致时抛出
   */
  @Override
  public ObjectMetadata head(ObjectRef ref) throws IOException {
    Path path = pathFor(ref);
    long actualSize = Files.size(path);
    if (actualSize != ref.size()) {
      throw new IOException("stored object size no longer matches ObjectRef");
    }
    return new ObjectMetadata(
        ref.checksum(), actualSize, ref.mediaType(), Files.getLastModifiedTime(path).toInstant());
  }

  /**
   * 删除当前 Store 拥有的对象，不允许跨根目录或跨 Authority 删除。
   *
   * @param ref 对象引用
   * @throws IOException 引用越权或删除失败时抛出
   */
  @Override
  public void delete(ObjectRef ref) throws IOException {
    Files.deleteIfExists(pathFor(ref));
  }

  /**
   * 为本地开发对象生成进程级短期 HMAC URI；服务重启后旧签名自然失效。
   *
   * @param ref 对象引用
   * @param ttl 正数且不超过配置上限的时长
   * @return 禁止持久化和日志记录的短期 URI
   * @throws IOException 引用越权、对象不存在或签名失败时抛出
   */
  @Override
  public SignedUrl sign(ObjectRef ref, Duration ttl) throws IOException {
    pathFor(ref);
    requireSignTtl(ttl);
    Instant expiresAt = Instant.now().plus(ttl);
    String payload = ref.uri().toASCIIString() + "\n" + expiresAt.getEpochSecond();
    String signature = hmac(payload);
    URI signed =
        URI.create(
            ref.uri().toASCIIString()
                + "?expires="
                + expiresAt.getEpochSecond()
                + "&signature="
                + signature);
    return new SignedUrl(signed, expiresAt);
  }

  /**
   * 流式写入临时文件并同步计算 SHA-256 与字节数。
   *
   * @param input 输入流，方法始终关闭
   * @param target 临时文件
   * @param maximum 最大允许字节数
   * @return 实际大小和 SHA-256
   * @throws IOException 读取、写入或大小超限时抛出
   */
  private WriteResult write(InputStream input, Path target, long maximum) throws IOException {
    MessageDigest digest = sha256();
    long size = 0;
    byte[] buffer = new byte[8192];
    try (InputStream source = input;
        var output =
            Files.newOutputStream(
                target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      int count;
      while ((count = source.read(buffer)) != -1) {
        size += count;
        if (size > maximum) {
          throw new IOException("object exceeds configured size limit");
        }
        digest.update(buffer, 0, count);
        output.write(buffer, 0, count);
      }
    }
    return new WriteResult(size, HexFormat.of().formatHex(digest.digest()));
  }

  /**
   * 将 ObjectRef 转换为当前根目录内的安全路径。
   *
   * @param ref 对象引用
   * @return 当前 Store 根目录内的规范路径
   * @throws IOException 当 Scheme、Authority、路径或对象归属不合法时抛出
   */
  private Path pathFor(ObjectRef ref) throws IOException {
    java.util.Objects.requireNonNull(ref, "ref must not be null");
    if (!"object".equals(ref.uri().getScheme())
        || !authority.equals(ref.uri().getAuthority())
        || ref.uri().getRawQuery() != null
        || ref.uri().getRawFragment() != null) {
      throw new IOException("ObjectRef does not belong to this ObjectStore");
    }
    String rawPath = ref.uri().getPath();
    if (rawPath == null || rawPath.length() < 2 || rawPath.contains("..")) {
      throw new IOException("ObjectRef path is invalid");
    }
    Path path = resolveRelative(rawPath.substring(1));
    if (!Files.isRegularFile(path)) {
      throw new IOException("stored object does not exist");
    }
    return path;
  }

  /**
   * 将相对路径解析到根目录并执行目录穿越保护。
   *
   * @param relative 相对对象路径
   * @return 根目录内的规范路径
   * @throws IOException 当解析结果逃逸根目录时抛出
   */
  private Path resolveRelative(String relative) throws IOException {
    Path resolved = root.resolve(relative).normalize();
    if (!resolved.startsWith(root)) {
      throw new IOException("object path escapes configured root");
    }
    return resolved;
  }

  /**
   * 拒绝文件系统根目录、用户主目录和当前工作目录，防止误配置扩大删除边界。
   *
   * @param configuredRoot 待使用的专用存储根目录
   * @return 规范化后的安全根目录
   * @throws IllegalArgumentException 当根目录为空或指向受保护目录时抛出
   */
  private Path requireSafeRoot(Path configuredRoot) {
    Path candidate =
        java.util.Objects.requireNonNull(configuredRoot, "storage root must not be null")
            .toAbsolutePath()
            .normalize();
    Path fileSystemRoot = candidate.getRoot();
    Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    if (candidate.equals(fileSystemRoot)
        || candidate.equals(userHome)
        || candidate.equals(workingDirectory)) {
      throw new IllegalArgumentException("storage root must be a dedicated subdirectory");
    }
    return candidate;
  }

  /**
   * 校验签名 TTL。
   *
   * @param ttl 待校验时长
   * @throws IllegalArgumentException 当时长为零、负数或超过上限时抛出
   */
  private void requireSignTtl(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(maxSignTtl) > 0) {
      throw new IllegalArgumentException("sign ttl must be positive and within maximum");
    }
  }

  /**
   * 计算 Local Profile 临时授权签名。
   *
   * @param payload 待签名路径和失效时间
   * @return Base64 URL 安全签名
   * @throws IOException 当前 JDK 缺少 HmacSHA256 时抛出
   */
  private String hmac(String payload) throws IOException {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(localSigningKey, "HmacSHA256"));
      return java.util.Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException error) {
      throw new IOException("JDK does not provide HmacSHA256", error);
    }
  }

  /**
   * 创建 SHA-256 消息摘要器。
   *
   * @return SHA-256 摘要器
   * @throws IllegalStateException 当前 JDK 缺少 SHA-256 时抛出
   */
  private MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("JDK does not provide SHA-256", error);
    }
  }

  /**
   * 在写入前置校验失败时关闭已转移所有权的输入流，并保留关闭失败上下文。
   *
   * @param input 待关闭输入流
   * @param primaryError 即将抛出的主错误，关闭错误以 Suppressed 形式附加
   */
  private void closeBeforeReject(InputStream input, IOException primaryError) {
    try {
      input.close();
    } catch (IOException closeError) {
      primaryError.addSuppressed(closeError);
    }
  }

  /**
   * 表示一次临时文件写入的实际大小和 SHA-256 结果。
   *
   * @param size 实际字节数
   * @param sha256Hex 64 位小写 SHA-256 十六进制摘要
   * @author refinex
   */
  private record WriteResult(long size, String sha256Hex) {

    /**
     * 校验临时写入结果。
     *
     * @param size 实际字节数
     * @param sha256Hex SHA-256 十六进制摘要
     * @throws IllegalArgumentException 当字段不合法时抛出
     */
    private WriteResult {
      if (size < 0 || sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("write result is invalid");
      }
    }
  }
}
