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

package space.refinex.agentark.control.secret.domain;

/**
 * 定义 Secret Metadata 可被引用的租户范围。
 *
 * @author refinex
 */
public enum SecretScope {
    /**
     * 项目内资产可直接引用。
     */
    PROJECT,
    /**
     * 必须通过 Environment Binding 引用。
     */
    ENVIRONMENT
}

