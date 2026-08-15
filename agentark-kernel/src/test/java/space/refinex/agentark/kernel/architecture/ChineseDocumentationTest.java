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

package space.refinex.agentark.kernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * 使用 JDK 编译器语法树验证仓库手工维护 Java 声明的中文 Javadoc 规范。
 *
 * @author refinex
 */
class ChineseDocumentationTest {

  /** 匹配 Javadoc 语法树中经过转义的 Unicode 码点。 */
  private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

  /** AgentArk Git 仓库根目录。 */
  private static final Path ROOT =
      Path.of(System.getProperty("agentark.root")).toAbsolutePath().normalize();

  /** 验证所有手工维护的 Java 声明均具有包含中文内容的 Javadoc。 */
  @Test
  void everyMaintainedJavaDeclarationHasChineseJavadoc() throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("中文注释审计必须运行在完整 JDK 上").isNotNull();

    List<Path> sources = javaSources();
    assertThat(sources).as("中文注释审计必须找到 Java 源文件").isNotEmpty();

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      JavacTask task =
          (JavacTask)
              compiler.getTask(
                  null,
                  fileManager,
                  null,
                  List.of("-proc:none"),
                  null,
                  fileManager.getJavaFileObjectsFromPaths(sources));
      DocTrees docTrees = DocTrees.instance(task);
      List<String> violations = new ArrayList<>();

      task.parse()
          .forEach(unit -> new ChineseDocumentationScanner(docTrees, violations).scan(unit, null));

      assertThat(violations)
          .as("以下 Java 声明缺少中文 Javadoc：%n%s", String.join("%n", violations))
          .isEmpty();
    }
  }

  /**
   * 收集仓库模块中手工维护的生产和测试 Java 源文件。
   *
   * @return 按路径排序的 Java 源文件列表
   * @throws IOException 遍历仓库文件失败时抛出
   */
  private static List<Path> javaSources() throws IOException {
    try (Stream<Path> paths = Files.walk(ROOT)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(ChineseDocumentationTest::isMaintainedJavaSource)
          .sorted()
          .toList();
    }
  }

  /**
   * 判断路径是否属于模块的手工维护 Java 源码目录。
   *
   * @param path 待判断的绝对或相对路径
   * @return 位于 main/test Java 源码目录且不是生成物时返回 {@code true}
   */
  private static boolean isMaintainedJavaSource(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.endsWith(".java")
        && (normalized.contains("/src/main/java/") || normalized.contains("/src/test/java/"))
        && !normalized.contains("/target/")
        && !normalized.contains("/.agentark/");
  }

  /**
   * 判断 Javadoc 是否包含至少一个汉字。
   *
   * @param comment 编译器解析得到的 Javadoc
   * @return Javadoc 存在且包含汉字时返回 {@code true}
   */
  private static boolean containsChinese(DocCommentTree comment) {
    return comment != null && containsChinese(comment.toString());
  }

  /**
   * 判断 Javadoc 语法树渲染文本是否包含汉字 Unicode 码点。
   *
   * @param rendered 编译器渲染并转义后的 Javadoc 文本
   * @return 文本包含汉字时返回 {@code true}
   */
  private static boolean containsChinese(String rendered) {
    var matcher = UNICODE_ESCAPE.matcher(rendered);
    while (matcher.find()) {
      int codePoint = Integer.parseInt(matcher.group(1), 16);
      if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断类型 Javadoc 是否声明唯一标准作者 {@code refinex}。
   *
   * @param comment 编译器解析得到的类型 Javadoc
   * @return 仅声明 {@code @author refinex} 时返回 {@code true}
   */
  private static boolean hasRefinexAuthor(DocCommentTree comment) {
    if (comment == null) {
      return false;
    }
    List<String> authors =
        comment.getBlockTags().stream()
            .filter(AuthorTree.class::isInstance)
            .map(AuthorTree.class::cast)
            .map(
                author ->
                    String.join("", author.getName().stream().map(Object::toString).toList())
                        .trim())
            .toList();
    return authors.equals(List.of("refinex"));
  }

  /**
   * 遍历声明节点并收集缺少中文 Javadoc 的位置。
   *
   * @author refinex
   */
  private static final class ChineseDocumentationScanner extends TreePathScanner<Void, Void> {

    /** 用于读取 Javadoc 和源码位置的编译器视图。 */
    private final DocTrees docTrees;

    /** 当前审计累计发现的违规详情。 */
    private final List<String> violations;

    /**
     * 创建单个编译单元使用的中文注释扫描器。
     *
     * @param docTrees 编译器 Javadoc 视图
     * @param violations 共享的违规详情集合
     */
    private ChineseDocumentationScanner(DocTrees docTrees, List<String> violations) {
      this.docTrees = docTrees;
      this.violations = violations;
    }

    /**
     * 检查具名类、接口、记录和枚举的中文 Javadoc。
     *
     * @param node 当前类型声明
     * @param unused 扫描器未使用的上下文
     * @return 扫描器不产生返回值，固定为 {@code null}
     */
    @Override
    public Void visitClass(ClassTree node, Void unused) {
      if (!node.getSimpleName().isEmpty()) {
        requireChinese("类型", node.getSimpleName().toString(), node);
        requireRefinexAuthor(node);
      }
      return super.visitClass(node, unused);
    }

    /**
     * 检查显式构造器和方法的中文 Javadoc。
     *
     * @param node 当前构造器或方法声明
     * @param unused 扫描器未使用的上下文
     * @return 扫描器不产生返回值，固定为 {@code null}
     */
    @Override
    public Void visitMethod(MethodTree node, Void unused) {
      requireChinese("方法", node.getName().toString(), node);
      return super.visitMethod(node, unused);
    }

    /**
     * 检查字段、常量和枚举值的中文 Javadoc，并允许 Record 组件使用类型级 {@code @param}。
     *
     * @param node 当前变量声明
     * @param unused 扫描器未使用的上下文
     * @return 扫描器不产生返回值，固定为 {@code null}
     */
    @Override
    public Void visitVariable(VariableTree node, Void unused) {
      TreePath parent = getCurrentPath().getParentPath();
      if (parent != null
          && parent.getLeaf() instanceof ClassTree parentClass
          && !containsChinese(docTrees.getDocCommentTree(getCurrentPath()))
          && !recordComponentCovered(parent, parentClass, node)) {
        violations.add(location(node) + " 字段 " + node.getName());
      }
      return super.visitVariable(node, unused);
    }

    /**
     * 检查 Record 类型注释是否以中文 {@code @param} 覆盖指定组件。
     *
     * @param parent Record 类型的语法树路径
     * @param parentClass Record 类型声明
     * @param component 待检查的 Record 组件
     * @return 类型注释覆盖当前组件时返回 {@code true}
     */
    private boolean recordComponentCovered(
        TreePath parent, ClassTree parentClass, VariableTree component) {
      if (parentClass.getKind() != Tree.Kind.RECORD) {
        return false;
      }
      DocCommentTree comment = docTrees.getDocCommentTree(parent);
      return comment != null
          && comment.getBlockTags().stream()
              .filter(ParamTree.class::isInstance)
              .map(ParamTree.class::cast)
              .anyMatch(
                  parameter ->
                      parameter.getName().getName().contentEquals(component.getName())
                          && containsChinese(parameter.getDescription().toString()));
    }

    /**
     * 要求当前声明具有中文 Javadoc，否则记录可定位的违规详情。
     *
     * @param kind 声明种类中文名称
     * @param name 声明标识符
     * @param declaration 声明语法树节点
     */
    private void requireChinese(String kind, String name, Tree declaration) {
      if (!containsChinese(docTrees.getDocCommentTree(getCurrentPath()))) {
        violations.add(location(declaration) + " " + kind + " " + name);
      }
    }

    /**
     * 要求当前具名类型唯一声明 {@code @author refinex}。
     *
     * @param declaration 待检查的类型声明
     */
    private void requireRefinexAuthor(ClassTree declaration) {
      if (!hasRefinexAuthor(docTrees.getDocCommentTree(getCurrentPath()))) {
        violations.add(location(declaration) + " 类型作者 " + declaration.getSimpleName());
      }
    }

    /**
     * 将语法树节点转换为仓库相对路径和行号。
     *
     * @param declaration 待定位的声明节点
     * @return 便于开发者定位的路径与行号
     */
    private String location(Tree declaration) {
      long offset =
          docTrees
              .getSourcePositions()
              .getStartPosition(getCurrentPath().getCompilationUnit(), declaration);
      long line = getCurrentPath().getCompilationUnit().getLineMap().getLineNumber(offset);
      Path path = Path.of(getCurrentPath().getCompilationUnit().getSourceFile().toUri());
      return ROOT.relativize(path).toString().replace('\\', '/') + ":" + line;
    }
  }
}
