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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 冻结 Go→Java 切换期间的 Internal Contract Hash、Java-only 默认部署与延后能力决策。
 *
 * @author refinex
 */
class AistioCutoverContractTest {

  /** AgentArk 仓库根目录。 */
  private static final Path ROOT =
      Path.of(System.getProperty("agentark.root")).toAbsolutePath().normalize();

  /**
   * 验证 Runtime、Scheduler 和 Control Internal v1 未因兼容代理改写。
   *
   * @throws IOException 契约或清单不可读取时抛出
   */
  @Test
  void internalContractsMatchFrozenCutoverHashes() throws IOException {
    Map<String, Object> manifest = manifest();

    for (Map<String, Object> contract : listOfMaps(manifest.get("javaInternalContracts"))) {
      Path path = ROOT.resolve(String.valueOf(contract.get("path"))).normalize();
      assertThat(path).startsWith(ROOT).isRegularFile();
      assertThat(sha256(Files.readAllBytes(path)))
          .isEqualTo(String.valueOf(contract.get("sha256")));
    }
  }

  /**
   * 验证默认 Cutover 已关闭 Go 路由、写入和 Fallback，Team/CRD 有明确 DEFER/REJECT。
   *
   * @throws IOException 清单不可读取时抛出
   */
  @Test
  void defaultCutoverIsJavaOnlyAndNonCoreCapabilitiesAreClassified() throws IOException {
    Map<String, Object> manifest = manifest();
    Map<String, Object> cutover = map(manifest.get("defaultCutover"));

    assertThat(cutover.get("mode")).isEqualTo("JAVA_ONLY");
    assertThat(cutover.get("goWrites")).isEqualTo("DISABLED");
    assertThat(cutover.get("goFallback")).isEqualTo("DISABLED");
    assertThat(cutover.get("runtimeCatalogReads"))
        .isEqualTo("JAVA_CONTROL_INTERNAL_V1_ONLY");
    assertThat(String.valueOf(manifest.get("goApiFamilies")).toUpperCase(java.util.Locale.ROOT))
        .contains("DEFER", "REJECT", "TEAM", "CRD");
  }

  /**
   * 验证默认 Compose 不构建、启动或依赖 Go Aistio。
   *
   * @throws IOException Compose 不可读取时抛出
   */
  @Test
  void defaultComposeContainsOnlyJavaControl() throws IOException {
    String compose = Files.readString(ROOT.resolve("deploy/compose/docker-compose.yml"));

    assertThat(compose.toLowerCase(java.util.Locale.ROOT))
        .doesNotContain("aistio", "golang", "aistiod", "postgres:17")
        .contains("agentark-control-server", "control:", "mysql:8.4.11");
  }

  /**
   * @param content 待校验文件字节
   * @return 小写 SHA-256 十六进制文本
   */
  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK does not provide SHA-256", exception);
    }
  }

  /**
   * 以 SnakeYAML 安全模式解析 JSON 子集，避免 Kernel 引入 Jackson。
   *
   * @return Cutover Manifest 顶层对象
   * @throws IOException Manifest 不可读取时抛出
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> manifest() throws IOException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(0);
    try (InputStream input = Files.newInputStream(
        ROOT.resolve("contracts/migration/aistio-cutover-v1.json"))) {
      Object value = new Yaml(new SafeConstructor(options)).load(input);
      return (Map<String, Object>) value;
    }
  }

  /**
   * @param value JSON Object 候选值
   * @return 字符串键对象
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  /**
   * @param value JSON Object 数组候选值
   * @return 字符串键对象列表
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    return (List<Map<String, Object>>) value;
  }
}
