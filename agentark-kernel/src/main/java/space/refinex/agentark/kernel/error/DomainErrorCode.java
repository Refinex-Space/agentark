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
 * 表示与 HTTP、持久化和 Provider 异常解耦的稳定平台错误码。
 *
 * @param value 形如 {@code ARK-<AREA>-<CATEGORY>-<5 位编号>} 的稳定错误码
 * @author refinex
 */
public record DomainErrorCode(String value) {

    /**
     * 平台错误码的规范格式。
     */
    private static final Pattern FORMAT =
        Pattern.compile("ARK-[A-Z][A-Z0-9_]*-[A-Z][A-Z0-9_]*-[0-9]{5}");

    /**
     * 校验并创建领域错误码。
     *
     * @param value 稳定错误码
     * @throws IllegalArgumentException 当值不符合平台错误码格式时抛出
     */
    public DomainErrorCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "DomainErrorCode must match ARK-<AREA>-<CATEGORY>-<5 digits>");
        }
    }

    /**
     * 返回稳定错误码原值。
     *
     * @return 稳定错误码
     */
    @Override
    public String toString() {
        return value;
    }
}
