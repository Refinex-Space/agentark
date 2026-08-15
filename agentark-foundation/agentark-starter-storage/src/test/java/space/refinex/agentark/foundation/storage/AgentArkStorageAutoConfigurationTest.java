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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 验证 Storage Starter 默认不写磁盘、显式启用以及 Local 完整性和归属边界。
 *
 * @author refinex
 */
class AgentArkStorageAutoConfigurationTest {

  /** 每个测试独占且由 JUnit 清理的本地对象根目录。 */
  @TempDir Path temporaryRoot;

  /** 验证未显式启用时不创建 ObjectStore。 */
  @Test
  void remainsDisabledByDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentArkStorageAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ObjectStore.class));
  }

  /** 验证显式根目录和 Authority 配置后创建 Local Object Store。 */
  @Test
  void configuresLocalStoreWhenEnabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentArkStorageAutoConfiguration.class))
        .withPropertyValues(
            "agentark.foundation.storage.enabled=true",
            "agentark.foundation.storage.authority=control",
            "agentark.foundation.storage.root=" + temporaryRoot)
        .run(context -> assertThat(context).hasSingleBean(ObjectStore.class));
  }

  /** 验证 Local Put/Get/Head/Sign/Delete 闭环并核对大小和 SHA-256。 */
  @Test
  void storesAndValidatesLocalObject() throws Exception {
    byte[] content = "agentark-object".getBytes(StandardCharsets.UTF_8);
    AgentArkStorageProperties properties = new AgentArkStorageProperties();
    properties.setAuthority("runtime");
    properties.setRoot(temporaryRoot);
    ObjectStore store = new LocalObjectStore(properties);
    ObjectRef ref =
        store.put(
            new PutObjectCommand(
                new ObjectNamespace("events"),
                new ByteArrayInputStream(content),
                content.length,
                "application/octet-stream",
                Optional.of(Checksum.sha256(content))));

    try (InputStream storedContent = store.get(ref)) {
      assertThat(storedContent.readAllBytes()).isEqualTo(content);
    }
    assertThat(store.head(ref).size()).isEqualTo(content.length);
    assertThat(store.sign(ref, Duration.ofMinutes(1)).uri().getRawQuery())
        .contains("expires=")
        .contains("signature=");
    store.delete(ref);
    assertThatThrownBy(() -> store.get(ref)).isInstanceOf(java.io.IOException.class);
  }

  /** 验证其他 Authority 的 ObjectRef 不能访问当前根目录。 */
  @Test
  void rejectsReferencesOwnedByAnotherStore() throws Exception {
    AgentArkStorageProperties properties = new AgentArkStorageProperties();
    properties.setAuthority("runtime");
    properties.setRoot(temporaryRoot);
    ObjectStore store = new LocalObjectStore(properties);
    ObjectRef forged =
        ObjectRef.of(
            "object://control/events/forged",
            Checksum.sha256(new byte[0]),
            0,
            "application/octet-stream");

    assertThatThrownBy(() -> store.get(forged)).isInstanceOf(java.io.IOException.class);
  }

  /** 验证危险根目录在创建 Store 前被拒绝，避免 Local 删除能力越过专用数据目录。 */
  @Test
  void rejectsProtectedStorageRoot() {
    AgentArkStorageProperties properties = new AgentArkStorageProperties();
    properties.setAuthority("runtime");
    properties.setRoot(Path.of(System.getProperty("user.home")));

    assertThatThrownBy(() -> new LocalObjectStore(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dedicated subdirectory");
  }

  /** 验证声明大小超限时仍关闭输入流，并把关闭失败保留为受抑制异常。 */
  @Test
  void preservesInputCloseFailureWhenRejectingOversizedObject() throws Exception {
    InputStream content = mock(InputStream.class);
    doThrow(new IOException("close failed")).when(content).close();
    AgentArkStorageProperties properties = new AgentArkStorageProperties();
    properties.setAuthority("runtime");
    properties.setRoot(temporaryRoot);
    properties.setMaxObjectSize(1);
    ObjectStore store = new LocalObjectStore(properties);
    PutObjectCommand command =
        new PutObjectCommand(
            new ObjectNamespace("events"),
            content,
            2,
            "application/octet-stream",
            Optional.empty());

    assertThatThrownBy(() -> store.put(command))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("size limit")
        .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
  }
}
