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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 仅在显式启用时装配 Local Object Store，调用方提供其他 ObjectStore Bean 即可替换。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkStorageProperties.class)
@ConditionalOnProperty(
    prefix = "agentark.foundation.storage",
    name = "enabled",
    havingValue = "true")
public class AgentArkStorageAutoConfiguration {

  /** 创建对象存储自动配置。 */
  public AgentArkStorageAutoConfiguration() {
    // Spring Boot 通过公开构造器创建自动配置实例。
  }

  /**
   * 创建本地对象存储实现。
   *
   * @param properties 本地存储配置属性
   * @return Local Object Store
   * @throws IOException 专用根目录初始化失败时抛出
   */
  @Bean
  @ConditionalOnMissingBean
  public ObjectStore objectStore(AgentArkStorageProperties properties) throws IOException {
    return new LocalObjectStore(properties);
  }
}
