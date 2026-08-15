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
 * 定义 Runtime 在完成授权后解析 Secret 引用的版本策略。
 *
 * @author refinex
 */
public enum SecretResolutionPolicy {
    /**
     * 每次解析目标环境中最新的启用版本。
     */
    LATEST_ENABLED,

    /**
     * 仅解析 SecretRef 已明确固定的版本。
     */
    PINNED_VERSION
}
