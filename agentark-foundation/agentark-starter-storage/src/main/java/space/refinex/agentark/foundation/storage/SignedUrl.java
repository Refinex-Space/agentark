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

import java.net.URI;
import java.time.Instant;

/**
 * 表示短期对象访问 URI；该值可能含授权签名，禁止持久化、日志记录或写入 Event。
 *
 * @param uri 短期授权 URI
 * @param expiresAt 明确失效的 UTC 时刻
 * @author refinex
 */
public record SignedUrl(URI uri, Instant expiresAt) {

  /**
   * 校验并创建短期授权 URI。
   *
   * @param uri 绝对 URI
   * @param expiresAt 失效时刻
   * @throws IllegalArgumentException 当 URI 不是绝对地址时抛出
   * @throws NullPointerException 当参数为 {@code null} 时抛出
   */
  public SignedUrl {
    java.util.Objects.requireNonNull(uri, "uri must not be null");
    java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("signed URI must be absolute");
    }
  }
}
