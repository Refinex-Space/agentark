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

package space.refinex.agentark.control.secret.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 集中定义 Secret Public API 请求，只接受非敏感定位和绑定信息。
 *
 * @author refinex
 */
public final class SecretApiModels {

    /**
     * 禁止实例化 API 模型容器。
     */
    private SecretApiModels() {
    }

    /**
     * @param key             项目内稳定 Key
     * @param name            显示名称
     * @param provider        Provider 类型
     * @param externalPath    外部非敏感定位
     * @param externalVersion 可选外部版本
     * @param scope           PROJECT 或 ENVIRONMENT
     * @author refinex
     */
    public record CreateSecretMetadataRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @NotBlank String provider,
        @NotBlank @Size(max = 1024) String externalPath,
        @Size(max = 255) String externalVersion,
        @NotBlank String scope) {
    }

    /**
     * @param secretMetadataId Secret 元数据 UUIDv7
     * @param bindingKey       环境内稳定别名
     * @author refinex
     */
    public record CreateSecretBindingRequest(
        @NotBlank String secretMetadataId,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}") String bindingKey) {
    }
}
