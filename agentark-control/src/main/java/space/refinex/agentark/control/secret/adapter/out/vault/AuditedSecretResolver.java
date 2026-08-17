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

package space.refinex.agentark.control.secret.adapter.out.vault;

import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuditRecord;
import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.application.port.SecretResolver;
import space.refinex.agentark.control.secret.domain.SecretMetadata;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 为每次生产 Secret 解析记录成功或失败审计，审计内容只包含元数据引用。
 *
 * @author refinex
 */
public final class AuditedSecretResolver implements SecretResolver {

    /** 实际生产解析器。 */
    private final SecretResolver delegate;

    /** 事务感知审计发布器。 */
    private final IamAuditPublisher auditPublisher;

    /** UTC 时钟。 */
    private final Clock clock;

    /**
     * 创建审计包装器。
     *
     * @param delegate 实际解析器
     * @param auditPublisher 审计发布器
     * @param clock UTC 时钟
     */
    public AuditedSecretResolver(
        SecretResolver delegate, IamAuditPublisher auditPublisher, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.auditPublisher = Objects.requireNonNull(
            auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 解析 Secret，并以固定服务主体记录访问结果，禁止把值写入审计。
     *
     * @param metadata 已授权元数据
     * @return 可清零 Secret
     * @throws IOException 解析失败时原样抛出安全异常
     */
    @Override
    public ResolvedSecret resolve(SecretMetadata metadata) throws IOException {
        Instant now = Instant.now(clock);
        ResolvedSecret resolved = null;
        try {
            resolved = delegate.resolve(metadata);
            audit(metadata, "SUCCEEDED", now);
            return resolved;
        } catch (IOException | RuntimeException exception) {
            if (resolved != null) {
                resolved.close();
            }
            try {
                audit(metadata, "FAILED", now);
            } catch (RuntimeException auditFailure) {
                if (auditFailure != exception) {
                    exception.addSuppressed(auditFailure);
                }
            }
            throw exception;
        }
    }

    /**
     * 写入不含值、路径和版本的 Secret Access Audit。
     *
     * @param metadata Secret 元数据
     * @param outcome 访问结果
     * @param now 发生时间
     */
    private void audit(SecretMetadata metadata, String outcome, Instant now) {
        auditPublisher.append(new IamAuditRecord(
            "secret.value.resolve", "urn:agentark:service:secret-resolver",
            "secret-metadata", metadata.id().asString(),
            Optional.of(metadata.organizationId()), Optional.of(metadata.projectId()),
            outcome, now));
    }
}
