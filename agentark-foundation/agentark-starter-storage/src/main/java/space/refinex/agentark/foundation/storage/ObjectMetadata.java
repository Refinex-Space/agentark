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

import java.time.Instant;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 表示对象存储返回的完整性、大小、媒体类型和最近修改元数据。
 *
 * @param checksum SHA-256 校验和
 * @param size 非负字节数
 * @param contentType 具体媒体类型
 * @param lastModified 最近修改的 UTC 时刻
 * @author refinex
 */
public record ObjectMetadata(
    Checksum checksum, long size, String contentType, Instant lastModified) {

  /**
   * 校验并创建对象元数据。
   *
   * @param checksum 校验和
   * @param size 字节数
   * @param contentType 媒体类型
   * @param lastModified 最近修改时刻
   * @throws IllegalArgumentException 当大小为负数或媒体类型为空时抛出
   * @throws NullPointerException 当校验和或时间为 {@code null} 时抛出
   */
  public ObjectMetadata {
    java.util.Objects.requireNonNull(checksum, "checksum must not be null");
    java.util.Objects.requireNonNull(lastModified, "lastModified must not be null");
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("contentType must not be blank");
    }
  }
}
