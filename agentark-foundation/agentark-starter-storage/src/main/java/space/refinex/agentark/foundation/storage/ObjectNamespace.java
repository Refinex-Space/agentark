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
 * 表示由服务端代码选择的对象业务命名空间，调用方不能传入路径分隔符或相对路径。
 *
 * @param value 稳定小写命名空间段
 * @author refinex
 */
public record ObjectNamespace(String value) {

  /**
   * 校验并创建对象业务命名空间。
   *
   * @param value 稳定小写命名空间段
   * @throws IllegalArgumentException 当值含路径分隔符或格式不合法时抛出
   */
  public ObjectNamespace {
    if (value == null || !value.matches("[a-z][a-z0-9-]{0,62}")) {
      throw new IllegalArgumentException("object namespace must be a lowercase segment");
    }
  }
}
