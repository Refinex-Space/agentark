import { resolve } from "node:path";
import { defineConfig } from "orval";

const outputRoot = resolve(process.env.AGENTARK_OPENAPI_OUTPUT_ROOT ?? "src/shared/api/generated");

/**
 * 创建单个 Public OpenAPI 的 Fetch Client 与类型生成配置。
 *
 * @param name 输出客户端名称。
 * @param specification 相对当前 Web 工程的规范文件路径。
 * @param externalRefs 允许解析的仓库内外部 Schema 文档。
 */
function publicApi(name: string, specification: string, externalRefs: string[]) {
  return {
    input: {
      target: specification,
      parserOptions: {
        // 只放行契约显式拥有的仓库内 Schema，不允许远程引用。
        externalRefs: { allow: externalRefs },
      },
    },
    output: {
      target: resolve(outputRoot, name, "client.ts"),
      schemas: resolve(outputRoot, name, "models"),
      client: "fetch" as const,
      mode: "single" as const,
      tsconfig: resolve("tsconfig.app.json"),
      baseUrl: "",
      headers: true,
    },
  };
}

/**
 * 为 Control、Runtime 和 Scheduler 三套公共契约生成互不混合的客户端。
 */
export default defineConfig({
  control: publicApi("control", "../contracts/openapi/public-control-v1.yaml", [
    "../schemas/problem-detail/v1.json",
    "../schemas/iam-public/v1.json",
    "../schemas/catalog-public/v1.json",
    "../schemas/knowledge-public/v1.json",
    "../schemas/release-public/v1.json",
  ]),
  runtime: publicApi("runtime", "../contracts/openapi/public-runtime-v1.yaml", [
    "../schemas/problem-detail/v1.json",
    "../schemas/runtime-event/v1.json",
  ]),
  scheduler: publicApi("scheduler", "../contracts/openapi/public-scheduler-v1.yaml", [
    "../schemas/problem-detail/v1.json",
    "../schemas/scheduler-job/v1.json",
  ]),
});
