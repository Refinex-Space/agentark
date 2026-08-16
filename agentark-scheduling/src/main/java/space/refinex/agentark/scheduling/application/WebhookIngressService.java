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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.port.TriggerRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 把经过 HMAC、Nonce 和 Replay Protection 验证的 Webhook 原子转换为 Durable Job。
 *
 * @author refinex
 */
public final class WebhookIngressService {

    /**
     * Trigger 与 Nonce 权威仓储。
     */
    private final TriggerRepository repository;

    /**
     * HMAC 验证器。
     */
    private final WebhookSignatureVerifier verifier;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * Nonce 保留时间。
     */
    private final Duration nonceTtl;

    /**
     * 创建 Webhook 接入服务。
     *
     * @param repository Trigger 仓储
     * @param verifier   签名验证器
     * @param clock      UTC 时钟
     * @param nonceTtl   Replay Protection 保留时间
     */
    public WebhookIngressService(
        TriggerRepository repository,
        WebhookSignatureVerifier verifier,
        Clock clock,
        Duration nonceTtl) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nonceTtl = Objects.requireNonNull(nonceTtl, "nonceTtl must not be null");
        if (nonceTtl.compareTo(Duration.ofMinutes(5)) < 0
            || nonceTtl.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("nonceTtl must be between 5 minutes and 7 days");
        }
    }

    /**
     * 验证并接收 Webhook；重复 Nonce 返回同一业务效果而不再次创建 Job。
     *
     * @param triggerId Trigger UUIDv7
     * @param timestamp Unix 秒请求头
     * @param nonce     唯一 Nonce
     * @param signature HMAC 签名
     * @param body      原始 JSON 正文
     * @return 首次创建的 Job
     */
    public Job accept(
        UUID triggerId, String timestamp, String nonce, String signature, byte[] body) {
        TriggerDefinition trigger = repository.find(triggerId)
            .filter(candidate -> candidate.type() == TriggerType.WEBHOOK
                && candidate.status() == TriggerStatus.ENABLED)
            .orElseThrow(() -> new SchedulerException(
                "WEBHOOK_TRIGGER_NOT_FOUND", "webhook trigger is not available"));
        verifier.verify(
            trigger.secretRef().orElseThrow(), timestamp, nonce, signature, body);
        String payload = new String(body, StandardCharsets.UTF_8);
        Checksum requestHash = Checksum.sha256(body);
        Instant now = clock.instant();
        RetryPolicy policy = new RetryPolicy(
            3, Duration.ofSeconds(5), Duration.ofMinutes(5), 2.0, 0.2,
            Duration.ofMinutes(2));
        Job job = new Job(
            JobId.generate(), trigger.organizationId(), trigger.projectId(),
            trigger.targetJobType(), "webhook:" + trigger.id() + ":" + nonce,
            payload, requestHash, JobStatus.READY, 0, now, policy,
            IdempotencyCapability.PROVIDER_KEY, 0, 0, now, now);
        if (!repository.acceptWebhook(
            trigger, nonce, requestHash.value(), now.plus(nonceTtl), job,
            SchedulerApplicationService.outbox(job.id(), "job.accepted", now))) {
            throw new SchedulerException("WEBHOOK_REPLAYED", "webhook nonce was already consumed");
        }
        return job;
    }
}
