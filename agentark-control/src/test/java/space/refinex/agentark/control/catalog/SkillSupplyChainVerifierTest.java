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

package space.refinex.agentark.control.catalog;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.catalog.application.SkillSupplyChainVerifier;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.foundation.storage.ObjectMetadata;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证明 Skill 供应链验证器绑定 Artifact、来源、许可证、SBOM、扫描证明和信任根。
 *
 * @author refinex
 */
class SkillSupplyChainVerifierTest {

    /**
     * 证明可信 Ed25519 签名、CycloneDX SBOM 与通过扫描证明可以共同通过验证。
     *
     * @throws Exception 测试密钥、签名或对象流构造失败时抛出
     */
    @Test
    void shouldVerifySignedSkillSupplyChain() throws Exception {
        Fixture fixture = fixture();

        assertThatCode(() -> fixture.verifier().verify(fixture.payload(), fixture.store()))
            .doesNotThrowAnyException();
    }

    /**
     * 证明签名后替换许可证会使稳定清单签名失效。
     *
     * @throws Exception 测试密钥、签名或对象流构造失败时抛出
     */
    @Test
    void shouldRejectMetadataSubstitution() throws Exception {
        Fixture fixture = fixture();
        fixture.payload().put("license", "MIT");

        assertThatThrownBy(() -> fixture.verifier().verify(fixture.payload(), fixture.store()))
            .isInstanceOf(IamConflictException.class)
            .hasMessageContaining("signature");
    }

    /**
     * 证明 FAILED 扫描证明不能靠合法签名绕过。
     *
     * @throws Exception 测试密钥、签名或对象流构造失败时抛出
     */
    @Test
    void shouldRejectFailedScanAttestation() throws Exception {
        Fixture fixture = fixture();
        Map<String, Object> scan = new HashMap<>(cast(
            fixture.payload().get("scanAttestation")));
        scan.put("status", "FAILED");
        fixture.payload().put("scanAttestation", scan);

        assertThatThrownBy(() -> fixture.verifier().verify(fixture.payload(), fixture.store()))
            .isInstanceOf(IamConflictException.class)
            .hasMessageContaining("scan attestation");
    }

    /**
     * 创建带真实 Ed25519 签名和内存 SBOM 的验证夹具。
     *
     * @return 可独立篡改载荷的测试夹具
     * @throws Exception 密钥、签名或对象 Mock 构造失败时抛出
     */
    private Fixture fixture() throws Exception {
        byte[] sbomBytes = ("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\","
            + "\"components\":[]}").getBytes(StandardCharsets.UTF_8);
        ObjectRef artifact = ObjectRef.of(
            "object://skill/artifact", Checksum.sha256("artifact"), 8,
            "application/vnd.agentark.skill+zip");
        ObjectRef sbom = ObjectRef.of(
            "object://skill/sbom", Checksum.sha256(sbomBytes), sbomBytes.length,
            "application/vnd.cyclonedx+json");
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        CatalogProperties properties = new CatalogProperties();
        properties.setTrustedSkillSigningKeys(Map.of(
            "release-key", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())));
        Instant scannedAt = Instant.parse("2026-08-17T00:00:00Z");
        String manifest = SkillSupplyChainVerifier.manifest(
            artifact, "https://source.example.test/skill", "Apache-2.0", sbom,
            "trivy-0.72.0", scannedAt, "PASSED");
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(manifest.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new HashMap<>();
        payload.put("artifact", ref(artifact));
        payload.put("sourceUri", "https://source.example.test/skill");
        payload.put("license", "Apache-2.0");
        payload.put("compatibility", Map.of("runtimeProvider", "agentscope-java-2"));
        payload.put("sbom", ref(sbom));
        payload.put("scanAttestation", Map.of(
            "scanner", "trivy-0.72.0",
            "status", "PASSED",
            "artifactChecksum", artifact.checksum().toString(),
            "scannedAt", scannedAt.toString()));
        payload.put("signature", Map.of(
            "algorithm", "ED25519",
            "keyId", "release-key",
            "value", Base64.getEncoder().encodeToString(signer.sign())));

        ObjectStore store = mock(ObjectStore.class);
        when(store.head(sbom)).thenReturn(new ObjectMetadata(
            sbom.checksum(), sbom.size(), sbom.mediaType(), scannedAt));
        when(store.get(sbom)).thenAnswer(ignored -> new ByteArrayInputStream(sbomBytes));
        return new Fixture(
            new SkillSupplyChainVerifier(properties, JsonMapper.builder().build()), payload, store);
    }

    /**
     * @param ref ObjectRef
     * @return 与 Public API 相同的语言中立对象
     */
    private Map<String, Object> ref(ObjectRef ref) {
        return Map.of(
            "uri", ref.uri().toString(),
            "checksum", ref.checksum().toString(),
            "size", ref.size(),
            "mediaType", ref.mediaType());
    }

    /**
     * @param value 已知为字符串键对象的测试值
     * @return 对象视图
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    /**
     * 表示一次完整的 Skill 供应链验证夹具。
     *
     * @param verifier 验证器
     * @param payload 可变测试载荷
     * @param store 内存对象存储 Mock
     * @author refinex
     */
    private record Fixture(
        SkillSupplyChainVerifier verifier, Map<String, Object> payload, ObjectStore store) {
    }
}
