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

package space.refinex.agentark.kernel.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 验证 OpenAPI 与 AsyncAPI 骨架的版本、引用和安全解析约束。
 *
 * @author refinex
 */
class ContractDocumentLintTest {

  /** 仓库根目录下的契约目录绝对路径。 */
  private static final Path CONTRACTS =
      Path.of(System.getProperty("agentark.root"), "contracts").toAbsolutePath().normalize();

  /** 本阶段需要逐一检查的 OpenAPI 骨架文件。 */
  private static final List<String> OPEN_API_DOCUMENTS =
      List.of(
          "public-control-v1.yaml",
          "public-runtime-v1.yaml",
          "internal-control-v1.yaml",
          "internal-runtime-v1.yaml",
          "internal-scheduler-v1.yaml");

  /** 匹配 YAML 注释中至少一个汉字。 */
  private static final Pattern CHINESE_TEXT = Pattern.compile("\\p{IsHan}");

  /** 验证 OpenAPI 已版本化，且 Public/Internal Control 只声明已经实现的端点。 */
  @Test
  void openApiContractsAreVersionedAndOnlyExposeImplementedEndpoints() throws IOException {
    for (String fileName : OPEN_API_DOCUMENTS) {
      Path documentPath = CONTRACTS.resolve("openapi").resolve(fileName);
      Map<String, Object> document = load(documentPath);

      assertThat(document.get("openapi")).isEqualTo("3.1.0");
      if (fileName.equals("public-control-v1.yaml")) {
        Set<String> paths =
            ((Map<?, ?>) document.get("paths"))
                .keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        assertThat(paths)
            .containsExactlyInAnyOrderElementsOf(
                Set.of(
                    "/api/v1/organizations",
                    "/api/v1/organizations/{organizationId}/projects",
                    "/api/v1/projects/{projectId}/environments",
                    "/api/v1/projects/{projectId}/memberships",
                    "/api/v1/projects/{projectId}/roles",
                    "/api/v1/projects/{projectId}/role-bindings",
                    "/api/v1/projects/{projectId}/service-accounts",
                    "/api/v1/projects/{projectId}/permissions",
                    "/api/v1/projects/{projectId}/api-keys",
                    "/api/v1/projects/{projectId}/api-keys/{apiKeyId}/revoke",
                    "/api/v1/projects/{projectId}/catalog/{assetKind}",
                    "/api/v1/projects/{projectId}/catalog/{assetKind}/{assetId}/versions",
                    "/api/v1/projects/{projectId}/catalog/{assetKind}/{assetId}/versions:diff",
                    "/api/v1/projects/{projectId}/catalog/{assetKind}/{assetId}/archive",
                    "/api/v1/projects/{projectId}/skill-artifacts",
                    "/api/v1/projects/{projectId}/secrets",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/secret-bindings",
                    "/api/v1/projects/{projectId}/knowledge-bases",
                    "/api/v1/projects/{projectId}/knowledge-bases/{knowledgeBaseId}/data-sources",
                    "/api/v1/projects/{projectId}/knowledge-bases/{knowledgeBaseId}/documents",
                    "/api/v1/projects/{projectId}/knowledge-profiles/{profileKind}",
                    "/api/v1/projects/{projectId}/knowledge-bases/{knowledgeBaseId}/revisions",
                    "/api/v1/projects/{projectId}/knowledge-revisions/{revisionId}/ingestion-requests",
                    "/api/v1/projects/{projectId}/knowledge-revisions/{revisionId}/deprecate",
                    "/api/v1/projects/{projectId}/knowledge-revisions/{revisionId}/deletion",
                    "/api/v1/projects/{projectId}/agents",
                    "/api/v1/projects/{projectId}/agents/{agentId}",
                    "/api/v1/projects/{projectId}/agents/{agentId}/draft",
                    "/api/v1/projects/{projectId}/agents/{agentId}/draft/validate",
                    "/api/v1/projects/{projectId}/agents/{agentId}/publish",
                    "/api/v1/projects/{projectId}/agents/{agentId}/revisions",
                    "/api/v1/projects/{projectId}/agents/{agentId}/revisions/{revisionId}",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/promote",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/rollback",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/enable",
                    "/api/v1/projects/{projectId}/environments/{environmentId}/deployments/{deploymentId}/disable"));
      } else if (fileName.equals("internal-control-v1.yaml")) {
        Set<String> paths =
            ((Map<?, ?>) document.get("paths"))
                .keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        assertThat(paths)
            .containsExactlyInAnyOrder(
                "/internal/v1/agent-revisions/{revisionId}/snapshot",
                "/internal/v1/deployments/{deploymentId}");
      } else {
        assertThat(document.get("paths")).isEqualTo(Map.of());
      }
      assertThat(nested(document, "info", "version")).isEqualTo("1.0.0");
      assertThat(nested(document, "components", "schemas", "ProblemDetail", "$ref"))
          .isEqualTo("../schemas/problem-detail/v1.json");
      assertThat(documentPath.getParent().resolve("../schemas/problem-detail/v1.json").normalize())
          .exists();
    }
  }

  /** 验证 IAM 公共 Schema 已版本化，API Key 安全视图不包含摘要字段。 */
  @Test
  void iamPublicSchemaIsVersionedAndDoesNotExposeApiKeyDigest() throws IOException {
    Path schema = CONTRACTS.resolve("schemas/iam-public/v1.json");
    String content = Files.readString(schema);

    assertThat(schema).exists();
    assertThat(content)
        .contains("https://agentark.refinex.space/contracts/iam-public/v1.json")
        .contains("\"ApiKeyView\"")
        .contains("\"readOnly\": true")
        .doesNotContain("\"writeOnly\": true")
        .doesNotContain("\"digest\"");
  }

  /** 验证资产公共 Schema 已版本化，Secret 契约只出现引用和外部定位。 */
  @Test
  void catalogPublicSchemaIsVersionedAndDoesNotExposeSecretValues() throws IOException {
    Path schema = CONTRACTS.resolve("schemas/catalog-public/v1.json");
    String content = Files.readString(schema);

    assertThat(schema).exists();
    assertThat(content)
        .contains("https://agentark.refinex.space/contracts/catalog-public/v1.json")
        .contains("\"CatalogVersion\"")
        .contains("\"SecretMetadata\"")
        .contains("\"ObjectRef\"")
        .doesNotContain("secretValue")
        .doesNotContain("apiKey")
        .doesNotContain("plaintext");
  }

  /** 验证 Knowledge 公共 Schema 已版本化且只使用 SecretRef 和中立 Provider 契约。 */
  @Test
  void knowledgePublicSchemaIsVersionedAndProviderNeutral() throws IOException {
    Path schema = CONTRACTS.resolve("schemas/knowledge-public/v1.json");
    String content = Files.readString(schema);

    assertThat(schema).exists();
    assertThat(content)
        .contains("https://agentark.refinex.space/contracts/knowledge-public/v1.json")
        .contains("\"KnowledgeRevision\"")
        .contains("\"DocumentAcl\"")
        .contains("\"SecretRef\"")
        .doesNotContain("Qdrant")
        .doesNotContain("AgentScope")
        .doesNotContain("collectionName")
        .doesNotContain("apiKey");
  }

  /** 验证 Release 公共 Schema 已版本化且不暴露明文 Secret 或 Control 持久化实体。 */
  @Test
  void releasePublicSchemaIsVersionedAndSecretSafe() throws IOException {
    Path schema = CONTRACTS.resolve("schemas/release-public/v1.json");
    String content = Files.readString(schema);

    assertThat(schema).exists();
    assertThat(content)
        .contains("https://agentark.refinex.space/contracts/release-public/v1.json")
        .contains("\"AgentDraft\"")
        .contains("\"AgentRevision\"")
        .contains("\"Deployment\"")
        .doesNotContain("secretValue")
        .doesNotContain("plaintext")
        .doesNotContain("credentialValue");
  }

  /** 验证 AsyncAPI 骨架已版本化且引用统一的运行时事件 Schema。 */
  @Test
  void asyncApiSkeletonIsVersionedAndReferencesRuntimeEventSchema() throws IOException {
    Path documentPath = CONTRACTS.resolve("asyncapi/runtime-events-v1.yaml");
    Map<String, Object> document = load(documentPath);

    assertThat(document.get("asyncapi")).isEqualTo("3.0.0");
    assertThat(document.get("channels")).isEqualTo(Map.of());
    assertThat(document.get("operations")).isEqualTo(Map.of());
    assertThat(nested(document, "components", "messages", "RuntimeEvent", "payload", "$ref"))
        .isEqualTo("../schemas/runtime-event/v1.json");
    assertThat(documentPath.getParent().resolve("../schemas/runtime-event/v1.json").normalize())
        .exists();
  }

  /**
   * 先验证每个 YAML 属性具有相邻中文注释，再使用禁止重复键的安全模式加载文档。
   *
   * @param path 待加载的 YAML 文件路径
   * @return YAML 根对象映射
   * @throws IOException 文件读取失败时抛出
   */
  private static Map<String, Object> load(Path path) throws IOException {
    assertChinesePropertyComments(path);
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(10);
    options.setNestingDepthLimit(50);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    try (InputStream input = Files.newInputStream(path)) {
      Object value = yaml.load(input);
      assertThat(value).isInstanceOf(Map.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> document = (Map<String, Object>) value;
      return document;
    }
  }

  /**
   * 断言每个 YAML 配置属性前的最近有效行是中文注释。
   *
   * @param path 待检查的 YAML 文件路径
   * @throws IOException 文件读取失败时抛出
   */
  private static void assertChinesePropertyComments(Path path) throws IOException {
    List<String> violations = new ArrayList<>();
    String previousSignificant = "";
    List<String> lines = Files.readAllLines(path);
    for (int index = 0; index < lines.size(); index++) {
      String current = lines.get(index).trim();
      if (current.isEmpty()) {
        continue;
      }
      if (!current.startsWith("#")
          && (!previousSignificant.startsWith("#")
              || !CHINESE_TEXT.matcher(previousSignificant).find())) {
        violations.add(path.getFileName() + ":" + (index + 1));
      }
      previousSignificant = current;
    }
    assertThat(violations).as("以下 YAML 属性缺少相邻中文注释：%s", String.join(", ", violations)).isEmpty();
  }

  /**
   * 沿给定键路径读取嵌套映射中的值，并断言中间节点均为映射。
   *
   * @param root YAML 根对象映射
   * @param path 依次访问的键路径
   * @return 目标键对应的值
   */
  private static Object nested(Map<String, Object> root, String... path) {
    Object current = root;
    for (String segment : path) {
      assertThat(current).isInstanceOf(Map.class);
      current = ((Map<?, ?>) current).get(segment);
    }
    return current;
  }
}
