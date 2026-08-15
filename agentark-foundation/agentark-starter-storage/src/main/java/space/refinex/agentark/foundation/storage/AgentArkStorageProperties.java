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

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定义 Local Object Store 的显式开关、根目录、Authority、大小和签名时长上限。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.storage")
public class AgentArkStorageProperties {

  /** 是否启用 Local Object Store；默认关闭以避免 Library 隐式写入磁盘。 */
  private boolean enabled;

  /** Local Object Store 的专用根目录。 */
  private Path root = Path.of(".agentark", "data", "objects");

  /** ObjectRef URI 的稳定 Authority，用于拒绝其他 Store 的引用。 */
  private String authority;

  /** 单个对象允许的最大字节数。 */
  private long maxObjectSize = 64L * 1024 * 1024;

  /** Local 临时签名允许的最大存活时间。 */
  private Duration maxSignTtl = Duration.ofMinutes(15);

  /**
   * 返回 Local Object Store 是否启用。
   *
   * @return 启用时为 {@code true}
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 设置 Local Object Store 启用状态。
   *
   * @param enabled 是否启用
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 返回 Local Object Store 根目录。
   *
   * @return 根目录路径
   */
  public Path getRoot() {
    return root;
  }

  /**
   * 设置 Local Object Store 根目录。
   *
   * @param root 专用且非空目录
   */
  public void setRoot(Path root) {
    this.root = java.util.Objects.requireNonNull(root, "root must not be null");
  }

  /**
   * 返回 ObjectRef Authority。
   *
   * @return 可为空的稳定 Authority
   */
  public String getAuthority() {
    return authority;
  }

  /**
   * 设置 ObjectRef Authority。
   *
   * @param authority 稳定小写 Authority
   * @throws IllegalArgumentException 当格式不合法时抛出
   */
  public void setAuthority(String authority) {
    if (authority == null || !authority.matches("[a-z][a-z0-9-]{0,62}")) {
      throw new IllegalArgumentException("authority must be a stable lowercase segment");
    }
    this.authority = authority;
  }

  /**
   * 返回单对象最大字节数。
   *
   * @return 正数大小上限
   */
  public long getMaxObjectSize() {
    return maxObjectSize;
  }

  /**
   * 设置单对象最大字节数。
   *
   * @param maxObjectSize 正数大小上限
   * @throws IllegalArgumentException 当值非正数时抛出
   */
  public void setMaxObjectSize(long maxObjectSize) {
    if (maxObjectSize < 1) {
      throw new IllegalArgumentException("maxObjectSize must be positive");
    }
    this.maxObjectSize = maxObjectSize;
  }

  /**
   * 返回临时签名最大 TTL。
   *
   * @return 正数有限时长
   */
  public Duration getMaxSignTtl() {
    return maxSignTtl;
  }

  /**
   * 设置临时签名最大 TTL。
   *
   * @param maxSignTtl 正数且不超过 24 小时的时长
   * @throws IllegalArgumentException 当时长越界时抛出
   */
  public void setMaxSignTtl(Duration maxSignTtl) {
    if (maxSignTtl == null
        || maxSignTtl.isZero()
        || maxSignTtl.isNegative()
        || maxSignTtl.compareTo(Duration.ofHours(24)) > 0) {
      throw new IllegalArgumentException("maxSignTtl must be positive and at most 24 hours");
    }
    this.maxSignTtl = maxSignTtl;
  }
}
