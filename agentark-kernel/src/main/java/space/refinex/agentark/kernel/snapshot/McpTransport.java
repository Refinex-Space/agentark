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

package space.refinex.agentark.kernel.snapshot;

/**
 * 定义版本化 MCP 服务条目允许使用的传输方式。
 *
 * @author refinex
 */
public enum McpTransport {
    /**
     * 基于 HTTP 的 MCP Streamable HTTP 传输。
     */
    STREAMABLE_HTTP,

    /**
     * 仅引用平台注册命令、由沙箱进程承载的标准输入输出传输。
     */
    STDIO
}
