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

package space.refinex.agentark.knowledge.adapter.out.provider;

import java.nio.ByteBuffer;
import java.nio.charset.*;

/**
 * 只从标准输入读取 UTF-8 文本并向标准输出写规范化 Section 的受限解析子进程入口。
 *
 * @author refinex
 */
public final class RestrictedTextParserProcessMain {

    /**
     * 禁止由应用代码实例化子进程入口。
     */
    private RestrictedTextParserProcessMain() {
    }

    /**
     * 在独立 JVM 内校验字节上限、严格 UTF-8 和控制字符，然后规范化段落边界。
     *
     * @param arguments 唯一参数为最大输入字节数
     * @throws Exception 标准流读写、参数或编码校验失败时让子进程非零退出
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("exactly one size limit argument is required");
        }
        int maximum = Integer.parseInt(arguments[0]);
        if (maximum < 1 || maximum > 64 * 1024 * 1024) {
            throw new IllegalArgumentException("parser size limit is invalid");
        }
        byte[] input = System.in.readNBytes(maximum + 1);
        if (input.length > maximum) {
            throw new IllegalArgumentException("parser input exceeds size limit");
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text = decoder.decode(ByteBuffer.wrap(input)).toString();
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("parser input contains a null character");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] sections = normalized.split("\\n[ \\t]*\\n+");
        StringBuilder output = new StringBuilder(normalized.length());
        for (String section : sections) {
            String value = section.strip();
            if (!value.isEmpty()) {
                if (!output.isEmpty()) {
                    output.append("\n\n");
                }
                output.append(value);
            }
        }
        if (output.isEmpty()) {
            throw new IllegalArgumentException("parser input has no normalized text");
        }
        System.out.write(output.toString().getBytes(StandardCharsets.UTF_8));
    }
}
