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

package space.refinex.agentark.foundation.security;

/**
 * 区分交互式用户和非交互式服务身份，避免把两类授权语义混合。
 *
 * @author refinex
 */
public enum PrincipalType {

    /**
     * 由外部 OIDC 身份提供方认证的交互式用户。
     */
    USER,

    /**
     * 通过短期 Audience-bound 凭据认证的内部服务。
     */
    SERVICE,

    /**
     * 通过摘要校验和可轮换策略认证的 API Key 调用方。
     */
    API_KEY
}
