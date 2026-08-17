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

import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 使用 JSON Schema Draft 2020-12 验证契约示例及关键拒绝场景。
 *
 * @author refinex
 */
class ContractSchemaTest {

  /** 仓库根目录下的契约目录绝对路径。 */
  private static final Path CONTRACTS =
      Path.of(System.getProperty("agentark.root"), "contracts").toAbsolutePath().normalize();

  /** 验证 Agent Revision Snapshot Golden File 符合其版本化 Schema。 */
  @Test
  void snapshotGoldenFileConformsToDraft202012Schema() throws IOException {
    assertValid(
        "schemas/agent-revision-snapshot/v1.json",
        "schemas/agent-revision-snapshot/examples/valid.json");
  }

  /** 验证 Runtime Event Golden File 符合其版本化 Schema。 */
  @Test
  void runtimeEventGoldenFileConformsToDraft202012Schema() throws IOException {
    assertValid("schemas/runtime-event/v1.json", "schemas/runtime-event/examples/valid.json");
  }

  /** 验证 Problem Detail Golden File 符合公共错误 Schema。 */
  @Test
  void problemDetailGoldenFileConformsToCommonErrorSchema() throws IOException {
    assertValid("schemas/problem-detail/v1.json", "schemas/problem-detail/examples/valid.json");
  }

  /** 验证 AI 资产目录不可变版本 Golden File 符合公共 Schema。 */
  @Test
  void catalogVersionGoldenFileConformsToPublicSchema() throws IOException {
    assertValid(
        "schemas/catalog-public/v1.json",
        "schemas/catalog-public/examples/valid-catalog-version.json");
  }

  /** 验证 Knowledge Revision Golden File 符合版本化公共 Schema。 */
  @Test
  void knowledgeRevisionGoldenFileConformsToPublicSchema() throws IOException {
    assertValid(
        "schemas/knowledge-public/v1.json",
        "schemas/knowledge-public/examples/valid-knowledge-revision.json");
  }

  /** 验证 Knowledge 摄取 Internal Command Golden File 符合语言中立 Schema。 */
  @Test
  void knowledgeIngestionResultGoldenFileConformsToInternalSchema() throws IOException {
    assertValid(
        "schemas/knowledge-ingestion-internal/v1.json",
        "schemas/knowledge-ingestion-internal/examples/valid-result.json");
  }

  /** 验证 Knowledge Retrieval Citation 与 Trace Golden File 符合版本化 Schema。 */
  @Test
  void knowledgeRetrievalGoldenFileConformsToRetrievalSchema() throws IOException {
    assertValid(
        "schemas/knowledge-retrieval/v1.json",
        "schemas/knowledge-retrieval/examples/valid.json");
  }

  /** 验证 Scheduler Durable Job 接单 Golden File 符合版本化语言中立 Schema。 */
  @Test
  void schedulerJobGoldenFileConformsToInternalSchema() throws IOException {
    assertValid(
        "schemas/scheduler-job/v1.json",
        "schemas/scheduler-job/examples/valid-enqueue-job.json");
  }

  /** 验证 Aistio 只读迁移 Fixture 符合固定来源、备份、主键和引用契约。 */
  @Test
  void aistioMigrationExportFixtureConformsToMigrationSchema() throws IOException {
    assertValid(
        "schemas/migration/aistio-export/v1.json",
        "../tools/migration/fixtures/aistio-export-v1.json");
  }

  /** 验证 Aistio 数据迁移校验报告符合无正文、无 Secret 的版本化格式。 */
  @Test
  void aistioMigrationReportConformsToValidationSchema() throws IOException {
    assertValid(
        "schemas/migration/aistio-validation/v1.json",
        "schemas/migration/aistio-validation/examples/valid-migration-report.json");
  }

  /** 验证 Aistio Shadow Compare 报告符合 Hash、状态、延迟和字段路径格式。 */
  @Test
  void aistioShadowReportConformsToValidationSchema() throws IOException {
    assertValid(
        "schemas/migration/aistio-validation/v1.json",
        "schemas/migration/aistio-validation/examples/valid-shadow-report.json");
  }

  /** 验证 Agent Revision Golden File 符合 Phase 10 Release 公共 Schema。 */
  @Test
  void releaseRevisionGoldenFileConformsToPublicSchema() throws IOException {
    assertValid(
        "schemas/release-public/v1.json",
        "schemas/release-public/examples/valid-agent-revision.json");
  }

  /** 验证 Snapshot Schema 拒绝明文凭证字段、值和带用户信息的端点。 */
  @Test
  void snapshotSchemaRejectsPlaintextCredentials() throws IOException {
    String golden = read("schemas/agent-revision-snapshot/examples/valid.json");
    String plaintextValue =
        golden.replace("secret://project/model-dashscope-prod", "plaintext-model-key");
    String plaintextField =
        golden.replace(
            "\"resolutionPolicy\": \"LATEST_ENABLED\"",
            "\"resolutionPolicy\": \"LATEST_ENABLED\", \"password\": \"plaintext\"");
    String endpointCredential =
        golden.replace("https://mcp.example.com", "https://user:plaintext@mcp.example.com");

    assertThat(validate("schemas/agent-revision-snapshot/v1.json", plaintextValue)).isNotNull();
    assertThat(validate("schemas/agent-revision-snapshot/v1.json", plaintextField)).isNotNull();
    assertThat(validate("schemas/agent-revision-snapshot/v1.json", endpointCredential)).isNotNull();
  }

  /** 验证 Runtime Event 必须携带稳定的运行关联标识。 */
  @Test
  void runtimeEventRequiresStableCorrelationIdentifiers() throws IOException {
    String golden = read("schemas/runtime-event/examples/valid.json");
    String withoutRunId =
        golden.replace("  \"runId\": \"0198a4b0-1004-7104-8104-000000000004\",\n", "");

    assertThat(validate("schemas/runtime-event/v1.json", withoutRunId)).isNotNull();
  }

  /**
   * 断言指定实例能够通过目标 Schema 校验。
   *
   * @param schema Schema 相对于契约目录的路径
   * @param instance 实例文件相对于契约目录的路径
   * @throws IOException 文件读取失败时抛出
   */
  private static void assertValid(String schema, String instance) throws IOException {
    ValidationFailure failure = validate(schema, read(instance));
    assertThat(failure).as("contract validation failure: %s", failure).isNull();
  }

  /**
   * 根据目标 Schema 校验给定 JSON 文本。
   *
   * @param schema Schema 相对于契约目录的路径
   * @param instance 待校验的 JSON 文本
   * @return 校验成功时为 {@code null}，否则为失败详情
   * @throws IOException Schema 读取失败时抛出
   */
  private static ValidationFailure validate(String schema, String instance) throws IOException {
    Path schemaPath = CONTRACTS.resolve(schema);
    Validator validator =
        Validator.forSchema(
            new SchemaLoader(Files.readString(schemaPath), schemaPath.toUri()).load());
    return validator.validate(new JsonParser(instance).parse());
  }

  /**
   * 读取契约目录下的 UTF-8 文本文件。
   *
   * @param relativePath 文件相对于契约目录的路径
   * @return 文件文本
   * @throws IOException 文件读取失败时抛出
   */
  private static String read(String relativePath) throws IOException {
    return Files.readString(CONTRACTS.resolve(relativePath));
  }
}
