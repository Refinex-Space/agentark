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

package space.refinex.agentark.control.catalog.application;

import space.refinex.agentark.control.catalog.CatalogProperties;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.foundation.storage.ObjectMetadata;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * 验证 Skill Artifact 的来源、Hash、Ed25519 签名、CycloneDX SBOM、扫描证明和许可证。
 *
 * <p>验证只使用 ObjectRef 和部署信任根，不执行或解压 Skill。签名覆盖 Artifact、来源、许可证、SBOM
 * 与扫描摘要，任何字段替换都会导致验证失败。
 *
 * @author refinex
 */
public final class SkillSupplyChainVerifier {

    /**
     * Catalog 安全配置。
     */
    private final CatalogProperties properties;

    /**
     * 只用于验证 CycloneDX JSON 结构的映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * @param properties Catalog 安全配置
     * @param jsonMapper JSON 映射器
     */
    public SkillSupplyChainVerifier(CatalogProperties properties, JsonMapper jsonMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 验证 Skill 供应链证明；本地开发显式关闭时只保留既有 ObjectRef 完整性检查。
     *
     * @param payload     Skill Version 载荷
     * @param objectStore Object Store
     */
    public void verify(Map<String, Object> payload, ObjectStore objectStore) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(objectStore, "objectStore must not be null");
        if (!properties.isSkillSupplyChainRequired()) {
            return;
        }

        String license = text(payload, "license");
        if (!properties.getAllowedSkillLicenses().contains(license)) {
            throw rejected("skill license is not allowlisted");
        }

        ObjectRef artifact = objectRef(object(payload, "artifact"));
        ObjectRef sbom = objectRef(object(payload, "sbom"));
        Map<String, Object> scan = object(payload, "scanAttestation");
        String scanner = text(scan, "scanner");
        String scanStatus = text(scan, "status");
        String scanChecksum = text(scan, "artifactChecksum");
        Instant scannedAt = instant(scan, "scannedAt");
        if (!"PASSED".equals(scanStatus) || !artifact.checksum().toString().equals(scanChecksum)) {
            throw rejected("skill scan attestation does not match the artifact");
        }
        verifyObject(sbom, objectStore);
        verifyCycloneDx(sbom, objectStore);
        Map<String, Object> signature = object(payload, "signature");
        if (!"ED25519".equals(text(signature, "algorithm"))) {
            throw rejected("skill signature algorithm must be ED25519");
        }
        String keyId = text(signature, "keyId");
        String encodedKey = properties.getTrustedSkillSigningKeys().get(keyId);
        if (encodedKey == null) {
            throw rejected("skill signing key is not trusted");
        }
        String manifest = manifest(artifact, text(payload, "sourceUri"), license, sbom, scanner, scannedAt, scanStatus);
        verifySignature(encodedKey, text(signature, "value"), manifest);
    }

    /**
     * @param ref         SBOM 对象引用
     * @param objectStore Object Store
     */
    private void verifyObject(ObjectRef ref, ObjectStore objectStore) {
        try {
            ObjectMetadata metadata = objectStore.head(ref);
            if (!metadata.checksum().equals(ref.checksum())
                || metadata.size() != ref.size()
                || !metadata.contentType().equals(ref.mediaType())) {
                throw rejected("skill SBOM metadata does not match object store");
            }
        } catch (IOException exception) {
            throw new IamConflictException("skill SBOM is missing or inaccessible");
        }
    }

    /**
     * @param ref         SBOM 对象引用
     * @param objectStore Object Store
     */
    @SuppressWarnings("unchecked")
    private void verifyCycloneDx(ObjectRef ref, ObjectStore objectStore) {
        long maximum = properties.getMaxSkillSbomSize().toBytes();
        if (ref.size() < 1 || ref.size() > maximum
            || !"application/vnd.cyclonedx+json".equals(ref.mediaType())) {
            throw rejected("skill SBOM size or media type is invalid");
        }

        try (InputStream input = objectStore.get(ref)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
            if (bytes.length != ref.size() || bytes.length > maximum || !Checksum.sha256(bytes).equals(ref.checksum())) {
                throw rejected("skill SBOM content does not match ObjectRef");
            }

            Map<String, Object> document = jsonMapper.readValue(bytes, Map.class);
            if (!"CycloneDX".equals(document.get("bomFormat"))
                || !(document.get("specVersion") instanceof String version)
                || version.isBlank() || !(document.get("components") instanceof java.util.List<?>)) {
                throw rejected("skill SBOM is not a supported CycloneDX document");
            }
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            throw new IamConflictException("skill SBOM cannot be verified");
        }
    }

    /**
     * @param encodedKey       Base64 X.509 Ed25519 公钥
     * @param encodedSignature Base64 签名
     * @param manifest         被签名稳定清单
     */
    private void verifySignature(String encodedKey, String encodedSignature, String manifest) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(manifest.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(encodedSignature))) {
                throw rejected("skill signature verification failed");
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IamConflictException("skill signature cannot be verified");
        }
    }

    /**
     * @param artifact  Skill Artifact 引用
     * @param sourceUri 来源 URI
     * @param license   SPDX 许可证
     * @param sbom      CycloneDX SBOM 引用
     * @param scanner   扫描器名称与版本
     * @param scannedAt 扫描时刻
     * @param status    扫描结果
     * @return 用换行分隔且版本化的稳定签名清单
     */
    public static String manifest(ObjectRef artifact, String sourceUri, String license, ObjectRef sbom, String scanner,
                                  Instant scannedAt, String status) {

        return String.join("\n", "agentark-skill-v1", artifact.checksum().toString(), sourceUri,
            license, sbom.checksum().toString(), scanner, scannedAt.toString(), status);
    }

    /**
     * @param values JSON 对象
     * @param key    字段名
     * @return 必需嵌套对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Map<?, ?>)) {
            throw rejected("skill " + key + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    /**
     * @param values JSON 对象
     * @param key    字段名
     * @return 必需非空文本
     */
    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw rejected("skill " + key + " must not be blank");
        }
        return text;
    }

    /**
     * @param values JSON 对象
     * @param key    字段名
     * @return ISO-8601 时刻
     */
    private Instant instant(Map<String, Object> values, String key) {
        try {
            return Instant.parse(text(values, key));
        } catch (java.time.format.DateTimeParseException exception) {
            throw rejected("skill " + key + " must be an ISO-8601 instant");
        }
    }

    /**
     * @param value 载荷中的 ObjectRef 对象
     * @return 强校验 ObjectRef
     */
    private ObjectRef objectRef(Map<String, Object> value) {
        Object size = value.get("size");
        if (!(size instanceof Number number)) {
            throw rejected("skill ObjectRef size must be a number");
        }

        return ObjectRef.of(
            text(value, "uri"),
            new Checksum(text(value, "checksum")),
            number.longValue(),
            text(value, "mediaType")
        );
    }

    /**
     * @param message 不包含制品内容、路径或签名值的拒绝原因
     * @return 稳定冲突异常
     */
    private IamConflictException rejected(String message) {
        return new IamConflictException(message);
    }
}
