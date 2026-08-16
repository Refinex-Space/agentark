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
 * 定义 Secret 外部 Provider 分类；除 LOCAL_FILE 外仅作为生产 SPI 描述。
 *
 * @author refinex
 */
public enum SecretProviderType {
    /**
     * 仅 local Profile 使用的受控文件 Provider。
     */
    LOCAL_FILE,
    /**
     * HashiCorp Vault SPI 标识。
     */
    VAULT,
    /**
     * AWS Secrets Manager SPI 标识。
     */
    AWS_SECRETS_MANAGER,
    /**
     * Azure Key Vault SPI 标识。
     */
    AZURE_KEY_VAULT,
    /**
     * GCP Secret Manager SPI 标识。
     */
    GCP_SECRET_MANAGER,
    /**
     * 由部署方显式提供的自定义 SPI。
     */
    CUSTOM
}

