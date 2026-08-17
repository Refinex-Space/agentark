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

package space.refinex.agentark.control.secret;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.secret.adapter.out.vault.AuditedSecretResolver;
import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.control.secret.domain.SecretMetadataStatus;
import space.refinex.agentark.control.secret.domain.SecretProviderType;
import space.refinex.agentark.control.secret.domain.SecretScope;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretMetadataId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 证明 Secret Access Audit 失败时解析结果失败关闭并主动清零。
 *
 * @author refinex
 */
class AuditedSecretResolverTest {

    /**
     * 证明成功解析后若 Audit 无法持久化，调用方拿不到值且临时对象已关闭。
     */
    @Test
    void shouldCloseResolvedSecretWhenSuccessAuditFails() {
        ResolvedSecret resolved = new ResolvedSecret(UUID.randomUUID().toString().toCharArray());
        IamAuditPublisher publisher = mock(IamAuditPublisher.class);
        doThrow(new IllegalStateException("audit unavailable")).when(publisher).append(any());
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        AuditedSecretResolver resolver = new AuditedSecretResolver(
            metadata -> resolved, publisher, Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> resolver.resolve(metadata(now)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("audit unavailable");
        assertThatThrownBy(resolved::copy)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already closed");
    }

    /**
     * @param now 创建和更新时间
     * @return 不包含 Secret 值的 Vault 元数据
     */
    private SecretMetadata metadata(Instant now) {
        return new SecretMetadata(
            SecretMetadataId.generate(), OrganizationId.generate(), ProjectId.generate(),
            "audit-target", "审计目标", SecretProviderType.VAULT, "projects/audit-target", "1",
            SecretScope.PROJECT, SecretMetadataStatus.ENABLED, 0, now, now);
    }
}
