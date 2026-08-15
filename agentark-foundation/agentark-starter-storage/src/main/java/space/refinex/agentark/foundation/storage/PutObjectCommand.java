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

import java.io.InputStream;
import java.util.Optional;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 表示对象写入命令；对象路径由 ObjectStore 生成，输入流的所有权在调用后转移给实现。
 *
 * @param namespace 服务端选择的业务命名空间
 * @param content 单次消费并由实现关闭的输入流
 * @param size 调用方声明的非负字节数
 * @param contentType 具体媒体类型
 * @param expectedChecksum 可选预期 SHA-256，用于写入完整性校验
 * @author refinex
 */
public record PutObjectCommand(
    ObjectNamespace namespace,
    InputStream content,
    long size,
    String contentType,
    Optional<Checksum> expectedChecksum) {

  /**
   * 校验并创建对象写入命令。
   *
   * @param namespace 业务命名空间
   * @param content 输入流
   * @param size 字节数
   * @param contentType 具体媒体类型
   * @param expectedChecksum 可选预期校验和
   * @throws IllegalArgumentException 当大小或媒体类型不合法时抛出
   * @throws NullPointerException 当必需参数或 Optional 容器为 {@code null} 时抛出
   */
  public PutObjectCommand {
    java.util.Objects.requireNonNull(namespace, "namespace must not be null");
    java.util.Objects.requireNonNull(content, "content must not be null");
    expectedChecksum =
        java.util.Objects.requireNonNull(expectedChecksum, "expectedChecksum must not be null");
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative");
    }
    if (contentType == null
        || !contentType.matches(
            "[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*")) {
      throw new IllegalArgumentException("contentType must be a concrete media type");
    }
  }
}
