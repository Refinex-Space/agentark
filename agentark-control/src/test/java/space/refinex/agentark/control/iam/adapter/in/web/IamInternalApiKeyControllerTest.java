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

package space.refinex.agentark.control.iam.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 验证 Control 内部 API Key 自省只返回已独立认证的非秘密主体信息。
 *
 * @author refinex
 */
class IamInternalApiKeyControllerTest {

    /**
     * 验证 API Key 主体返回租户和权限，但不包含任何凭据字段。
     */
    @Test
    void returnsSafeAuthenticatedApiKeyIdentity() {
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "agentark-iam",
            "service-account-1",
            PrincipalType.API_KEY,
            Set.of("agent:read"),
            Optional.of(new TenantSelection(
                organizationId, Optional.of(projectId), Optional.empty())),
            Optional.empty());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of());

        var response = new IamInternalApiKeyController().verify(authentication);

        assertThat(response.principalType()).isEqualTo("API_KEY");
        assertThat(response.organizationId()).isEqualTo(organizationId.asString());
        assertThat(response.projectId()).isEqualTo(projectId.asString());
        assertThat(response.authorities()).containsExactly("agent:read");
        assertThat(response.toString()).doesNotContain("credential", "secret", "digest");
    }

    /**
     * 验证普通用户 JWT 不能调用 API Key 自省端点。
     */
    @Test
    void rejectsNonApiKeyPrincipal() {
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "https://issuer.example.test",
            "user-1",
            PrincipalType.USER,
            Set.of(),
            Optional.empty(),
            Optional.empty());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of());

        assertThatThrownBy(() -> new IamInternalApiKeyController().verify(authentication))
            .isInstanceOf(IamAccessDeniedException.class);
    }
}
