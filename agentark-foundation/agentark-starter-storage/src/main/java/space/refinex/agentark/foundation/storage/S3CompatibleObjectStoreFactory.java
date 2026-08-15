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

/**
 * 定义 S3-compatible Adapter 工厂扩展点，具体 SDK 依赖由独立适配模块拥有。
 *
 * @author refinex
 */
@FunctionalInterface
public interface S3CompatibleObjectStoreFactory {

  /**
   * 使用非秘密配置和 SecretRef 创建对象存储适配器。
   *
   * @param configuration S3-compatible 配置
   * @return 已完成受控路径与签名策略装配的对象存储
   */
  ObjectStore create(S3CompatibleConfiguration configuration);
}
