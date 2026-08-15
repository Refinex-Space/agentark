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

import java.util.regex.Pattern;

/**
 * 表示 Agent 修订版本发布时固定的稳定 Runtime Provider 标识。
 *
 * @param value 小写稳定 Provider 名称
 * @author refinex
 */
public record RuntimeProviderId(String value) {

    /**
     * Runtime Provider 名称的小写稳定标识格式。
     */
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    /**
     * 校验并创建 Runtime Provider 标识。
     *
     * @param value Provider 名称，最长 64 字符
     * @throws IllegalArgumentException 当名称为空、超长或格式不合法时抛出
     */
    public RuntimeProviderId {
        SnapshotRequirements.matching(value, "RuntimeProviderId", FORMAT, 64);
    }

    /**
     * 返回稳定 Provider 名称。
     *
     * @return Provider 名称原值
     */
    @Override
    public String toString() {
        return value;
    }
}
