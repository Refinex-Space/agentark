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

package space.refinex.agentark.control.iam.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.iam.application.port.ApiKeyRepository;
import space.refinex.agentark.control.iam.application.port.IdentityRepository;
import space.refinex.agentark.control.iam.domain.ApiKey;
import space.refinex.agentark.control.iam.domain.ServiceAccount;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.ApiKeyAuthenticator;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.ApiKeyId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 负责 API Key 高熵生成、单次展示、摘要认证、到期和吊销。
 *
 * @author refinex
 */
public class IamApiKeyService implements ApiKeyAuthenticator {

    /**
     * 只记录认证阶段分类而不记录前缀、摘要或凭据的安全诊断日志。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IamApiKeyService.class);

    /**
     * API Key 认证仅允许访问 Control Audience。
     */
    private static final String CONTROL_AUDIENCE = "agentark-control";

    /**
     * API Key 列表硬上限。
     */
    private static final int LIST_LIMIT = 100;

    /**
     * 密码学安全随机源。
     */
    private final SecureRandom secureRandom;

    /**
     * API Key 摘要端口。
     */
    private final ApiKeyRepository apiKeyRepository;

    /**
     * 服务账号端口。
     */
    private final IdentityRepository identityRepository;

    /**
     * 应用授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 租户目录应用服务。
     */
    private final IamApplicationService iamApplicationService;

    /**
     * 缓存失效事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 事务感知审计发布器。
     */
    private final IamAuditPublisher auditPublisher;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 API Key 服务。
     *
     * @param secureRandom          安全随机源
     * @param apiKeyRepository      API Key 摘要端口
     * @param identityRepository    服务账号端口
     * @param authorizationService  授权服务
     * @param iamApplicationService IAM 应用服务
     * @param eventPublisher        失效事件发布器
     * @param auditPublisher        审计发布器
     * @param clock                 UTC 时钟
     */
    public IamApiKeyService(SecureRandom secureRandom, ApiKeyRepository apiKeyRepository, IdentityRepository identityRepository,
                            IamAuthorizationService authorizationService, IamApplicationService iamApplicationService,
                            ApplicationEventPublisher eventPublisher, IamAuditPublisher auditPublisher, Clock clock) {

        this.secureRandom = java.util.Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        this.apiKeyRepository = java.util.Objects.requireNonNull(apiKeyRepository, "apiKeyRepository must not be null");
        this.identityRepository = java.util.Objects.requireNonNull(identityRepository, "identityRepository must not be null");
        this.authorizationService = java.util.Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.iamApplicationService = java.util.Objects.requireNonNull(iamApplicationService, "iamApplicationService must not be null");
        this.eventPublisher = java.util.Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.auditPublisher = java.util.Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建 API Key；数据库只接收摘要，明文只随当前返回值交付一次。
     *
     * @param principal        操作主体
     * @param projectId        项目标识
     * @param serviceAccountId 服务账号标识
     * @param name             展示名称
     * @param scopes           权限收窄集合
     * @param expiresAt        可选到期时刻
     * @return 单次明文交付对象
     */
    @Transactional
    public CreatedApiKey create(AgentArkPrincipal principal, ProjectId projectId, ServiceAccountId serviceAccountId,
                                String name, Set<String> scopes, Optional<Instant> expiresAt) {

        var project = iamApplicationService.requireProject(projectId);
        authorizationService.requirePermission(
            principal,
            project.organizationId(),
            Optional.of(project.id()),
            Optional.empty(),
            PermissionRegistry.API_KEY_MANAGE);
        ServiceAccount account = identityRepository.findServiceAccount(serviceAccountId)
            .filter(value -> value.organizationId().equals(project.organizationId()))
            .filter(value -> value.projectId().equals(project.id()))
            .orElseThrow(() -> new IamNotFoundException("service account was not found"));
        Set<String> checkedScopes = PermissionRegistry.requireRegistered(scopes);
        Set<String> servicePermissions = authorizationService.serviceAccountPermissions(
            project.organizationId(), project.id(), account.id());
        if (!servicePermissions.containsAll(checkedScopes)) {
            throw new IamAccessDeniedException("API key scopes must not exceed service account permissions");
        }

        String prefix = encode(randomBytes(9));
        String secret = encode(randomBytes(32));
        String plaintext = "ark_" + prefix + "_" + secret;
        ApiKey apiKey = ApiKey.create(
            project.organizationId(),
            project.id(),
            account.id(),
            name,
            prefix,
            digest(plaintext),
            checkedScopes,
            expiresAt,
            clock.instant());
        apiKeyRepository.insert(apiKey);
        eventPublisher.publishEvent(new IamAuthorizationChanged(project.organizationId(), Optional.of(project.id())));
        auditPublisher.append(new IamAuditRecord(
            "api_key.create",
            principal.subject(),
            "api-key",
            apiKey.id().asString(),
            Optional.of(project.organizationId()),
            Optional.of(project.id()),
            "SUCCEEDED",
            clock.instant()));
        return new CreatedApiKey(apiKey, plaintext);
    }

    /**
     * 列出项目 API Key 非秘密元数据。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百条摘要元数据
     */
    @Transactional(readOnly = true)
    public List<ApiKey> list(AgentArkPrincipal principal, ProjectId projectId) {
        var project = iamApplicationService.requireProject(projectId);
        authorizationService.requirePermission(
            principal,
            project.organizationId(),
            Optional.of(project.id()),
            Optional.empty(),
            PermissionRegistry.API_KEY_READ);
        return apiKeyRepository.list(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 吊销项目 API Key，已经吊销或版本冲突均返回稳定冲突。
     *
     * @param principal       操作主体
     * @param projectId       项目标识
     * @param apiKeyId        API Key 标识
     * @param expectedVersion 调用方读取的乐观锁版本
     */
    @Transactional
    public void revoke(AgentArkPrincipal principal, ProjectId projectId, ApiKeyId apiKeyId, long expectedVersion) {
        var project = iamApplicationService.requireProject(projectId);
        authorizationService.requirePermission(
            principal,
            project.organizationId(),
            Optional.of(project.id()),
            Optional.empty(),
            PermissionRegistry.API_KEY_MANAGE);
        if (!apiKeyRepository.revoke(
            project.organizationId(),
            project.id(),
            apiKeyId,
            clock.instant(),
            expectedVersion)) {
            throw new IamConflictException("API key was already changed or not found");
        }
        eventPublisher.publishEvent(new IamAuthorizationChanged(project.organizationId(), Optional.of(project.id())));
        auditPublisher.append(new IamAuditRecord(
            "api_key.revoke",
            principal.subject(),
            "api-key",
            apiKeyId.asString(),
            Optional.of(project.organizationId()),
            Optional.of(project.id()),
            "SUCCEEDED",
            clock.instant()));
    }

    /**
     * 校验 API Key 摘要、Audience、到期和吊销状态，并返回受 Scope 收窄的主体。
     *
     * @param keyId    公开前缀
     * @param secret   明文 Secret 字符数组；返回前始终清零
     * @param audience 当前服务 Audience
     * @return 认证成功主体或空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AgentArkPrincipal> authenticate(
        String keyId, char[] secret, String audience) {
        if (!CONTROL_AUDIENCE.equals(audience)
            || keyId == null
            || !keyId.matches("[A-Za-z0-9_-]{12}")
            || secret == null
            || secret.length != 43) {
            clear(secret);
            return Optional.empty();
        }

        try {
            byte[] candidateDigest = digest(keyId, secret);
            Optional<ApiKey> candidate = apiKeyRepository.findByPrefix(keyId);
            boolean digestMatches = candidate
                .map(apiKey -> MessageDigest.isEqual(apiKey.digest(), candidateDigest))
                .orElse(false);
            boolean usable = candidate
                .filter(apiKey -> MessageDigest.isEqual(apiKey.digest(), candidateDigest))
                .map(apiKey -> apiKey.isUsableAt(clock.instant()))
                .orElse(false);
            if (!digestMatches || !usable) {
                LOGGER.debug("API key authentication rejected: candidatePresent={}, digestMatches={}, usable={}",
                    candidate.isPresent(), digestMatches, usable);
                return Optional.empty();
            }
            LOGGER.debug("API key authentication passed digest and lifecycle checks");
            return candidate.map(apiKey -> new AgentArkPrincipal(
                "urn:agentark:api-key",
                apiKey.serviceAccountId().asString(),
                PrincipalType.API_KEY,
                apiKey.scopes(),
                Optional.of(new TenantSelection(
                    apiKey.organizationId(),
                    Optional.of(apiKey.projectId()),
                    Optional.empty())),
                Optional.empty()));
        } finally {
            clear(secret);
        }
    }

    /**
     * 生成指定长度的随机字节。
     *
     * @param length 正数字节数
     * @return 新随机数组
     */
    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    /**
     * 使用无填充 Base64 URL 编码随机值。
     *
     * @param value 随机字节
     * @return URL 安全文本
     */
    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /**
     * 计算完整 API Key 的 SHA-256 摘要。
     *
     * @param plaintext 完整 Key；禁止日志记录
     * @return 32 字节摘要
     */
    private byte[] digest(String plaintext) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 不创建额外明文 String，直接计算认证凭据的 SHA-256 摘要。
     *
     * @param keyId  公开前缀
     * @param secret URL 安全 ASCII Secret
     * @return 32 字节摘要
     */
    private byte[] digest(String keyId, char[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("ark_" + keyId + "_").getBytes(StandardCharsets.US_ASCII));
            for (char value : secret) {
                if (value > 0x7f) {
                    return new byte[32];
                }
                digest.update((byte) value);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 清零调用方提供的敏感字符数组。
     *
     * @param value 可为空的 Secret 数组
     */
    private void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
