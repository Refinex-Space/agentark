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
import java.time.Duration;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 定义路径由服务端生成且完整性受校验的稳定对象存储能力。
 *
 * @author refinex
 */
public interface ObjectStore {

  /**
   * 写入对象并返回不含授权材料的持久引用。
   *
   * @param command 写入命令；调用后输入流由实现关闭
   * @return 包含校验和、大小和媒体类型的对象引用
   * @throws IOException 读取、写入或完整性校验失败时抛出
   */
  ObjectRef put(PutObjectCommand command) throws IOException;

  /**
   * 打开对象读取流，调用方必须关闭返回流。
   *
   * @param ref 由当前 Store 生成的对象引用
   * @return 对象读取流
   * @throws IOException 引用越权、对象不存在或读取失败时抛出
   */
  InputStream get(ObjectRef ref) throws IOException;

  /**
   * 读取对象元数据并复核引用范围。
   *
   * @param ref 由当前 Store 生成的对象引用
   * @return 对象元数据
   * @throws IOException 引用越权、对象不存在或读取失败时抛出
   */
  ObjectMetadata head(ObjectRef ref) throws IOException;

  /**
   * 删除当前 Store 拥有的对象；所属业务必须先完成授权和持久状态变更。
   *
   * @param ref 由当前 Store 生成的对象引用
   * @throws IOException 引用越权或删除失败时抛出
   */
  void delete(ObjectRef ref) throws IOException;

  /**
   * 为当前 Store 拥有的对象生成短期访问 URI。
   *
   * @param ref 由当前 Store 生成的对象引用
   * @param ttl 正数且不超过实现上限的授权时长
   * @return 禁止持久化或日志记录的短期授权 URI
   * @throws IOException 引用越权、对象不存在或签名失败时抛出
   */
  SignedUrl sign(ObjectRef ref, Duration ttl) throws IOException;
}
