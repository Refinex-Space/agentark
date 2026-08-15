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
import space.refinex.agentark.kernel.ref.SecretRef;

/**
 * 表示 S3-compatible Adapter 的非秘密连接配置和凭据引用，不依赖任何厂商 SDK。
 *
 * @param endpoint HTTPS 服务端点
 * @param region 稳定区域名称
 * @param bucket 服务端拥有的 Bucket
 * @param credentialRef 解析访问凭据的 SecretRef
 * @param pathStyle 是否使用 Path-style 地址
 * @author refinex
 */
public record S3CompatibleConfiguration(
    URI endpoint, String region, String bucket, SecretRef credentialRef, boolean pathStyle) {

  /**
   * 校验并创建 S3-compatible 配置。
   *
   * @param endpoint HTTPS 端点
   * @param region 区域名称
   * @param bucket Bucket 名称
   * @param credentialRef 凭据引用
   * @param pathStyle 是否使用 Path-style 地址
   * @throws IllegalArgumentException 当端点或文本字段不合法时抛出
   * @throws NullPointerException 当必需对象为 {@code null} 时抛出
   */
  public S3CompatibleConfiguration {
    java.util.Objects.requireNonNull(endpoint, "endpoint must not be null");
    java.util.Objects.requireNonNull(credentialRef, "credentialRef must not be null");
    if (!endpoint.isAbsolute() || !"https".equalsIgnoreCase(endpoint.getScheme())) {
      throw new IllegalArgumentException("endpoint must be an absolute HTTPS URI");
    }
    if (region == null || region.isBlank() || bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("region and bucket must not be blank");
    }
  }
}
