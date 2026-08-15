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

package space.refinex.agentark.kernel.ref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * 验证版本、校验和、Secret 引用与对象引用值对象的边界。
 *
 * @author refinex
 */
class ReferenceValueTest {

  /** 验证 Schema 版本必须为正整数且初始版本为 1。 */
  @Test
  void schemaVersionMustBePositive() {
    assertThat(SchemaVersion.initial().value()).isEqualTo(1);
    assertThatThrownBy(() -> new SchemaVersion(0)).isInstanceOf(IllegalArgumentException.class);
  }

  /** 验证 SHA-256 校验和采用固定前缀和小写十六进制规范形式。 */
  @Test
  void checksumUsesCanonicalSha256() {
    Checksum checksum = Checksum.sha256("abc");

    assertThat(checksum.value())
        .isEqualTo("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(checksum.hex()).hasSize(64);
    assertThatThrownBy(
            () ->
                new Checksum(
                    "SHA256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 验证 Secret 引用不能嵌入用户凭证、查询参数或非 secret 协议。 */
  @Test
  void secretReferenceCannotEmbedCredentialsOrQueryValues() {
    SecretRef reference = SecretRef.parse("secret://project/model-production");

    assertThat(reference.asString()).isEqualTo("secret://project/model-production");
    assertThatThrownBy(() -> SecretRef.parse("secret://user:password@project/model"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SecretRef.parse("secret://project/model?token=plaintext"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SecretRef.parse("https://vault.example.com/secret"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 验证对象引用携带完整性元数据，并拒绝授权信息与非法大小。 */
  @Test
  void objectReferenceCarriesIntegrityMetadataWithoutAuthorizationData() {
    Checksum checksum = Checksum.sha256("skill");
    ObjectRef reference =
        ObjectRef.of("s3://agentark-skills/review.tgz", checksum, 512, "application/gzip");

    assertThat(reference.checksum()).isEqualTo(checksum);
    assertThat(reference.size()).isEqualTo(512);
    assertThatThrownBy(
            () ->
                new ObjectRef(
                    URI.create("s3://user:password@bucket/review.tgz"),
                    checksum,
                    512,
                    "application/gzip"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ObjectRef(
                    URI.create("s3://bucket/review.tgz?signature=secret"),
                    checksum,
                    512,
                    "application/gzip"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ObjectRef(
                    URI.create("s3://bucket/review.tgz"), checksum, -1, "application/gzip"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
