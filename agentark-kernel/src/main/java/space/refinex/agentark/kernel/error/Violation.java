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

package space.refinex.agentark.kernel.error;

import java.util.regex.Pattern;

/**
 * 表示可映射到任意传输协议的结构化字段校验失败。
 *
 * @param path    失败字段或集合元素的稳定路径
 * @param code    机器可读的稳定大写蛇形码
 * @param message 不含敏感信息的诊断消息
 * @author refinex
 */
public record Violation(String path, String code, String message) {

    /**
     * 校验明细代码的大写蛇形格式。
     */
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * 校验并创建结构化校验明细。
     *
     * @param path    字段路径，最长 512 字符
     * @param code    稳定校验码，最长 128 字符
     * @param message 诊断消息，最长 2048 字符
     * @throws IllegalArgumentException 当任一字段为空、超长或格式不合法时抛出
     */
    public Violation {
        if (path == null || path.isBlank() || path.length() > 512) {
            throw new IllegalArgumentException("Violation path must contain 1 to 512 characters");
        }
        if (code == null || code.length() > 128 || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Violation code must be stable upper snake case");
        }
        if (message == null || message.isBlank() || message.length() > 2048) {
            throw new IllegalArgumentException("Violation message must contain 1 to 2048 characters");
        }
    }
}
