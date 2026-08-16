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

package space.refinex.agentark.server.control;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.knowledge.KnowledgeConfiguration;
import space.refinex.agentark.knowledge.application.KnowledgePermissions;
import space.refinex.agentark.knowledge.application.KnowledgeProjectContext;
import space.refinex.agentark.knowledge.application.port.KnowledgeAccessPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeAuditPort;

import java.util.Optional;

/**
 * 在 Control Server 组合根把 Knowledge 的语言中立授权与审计 Port 接到真实 IAM 能力。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "agentark.control.knowledge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(KnowledgeConfiguration.class)
public class KnowledgeControlBridgeConfiguration {

    /**
     * 创建 Knowledge Control 组合根桥接配置。
     */
    public KnowledgeControlBridgeConfiguration() {
        // Spring 通过公开无参构造器创建配置实例。
    }

    /**
     * @param tenantRepository     IAM 租户目录
     * @param authorizationService IAM 授权服务
     * @return Knowledge 项目授权 Port
     */
    @Bean
    public KnowledgeAccessPort knowledgeAccessPort(
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService) {
        return (principal, projectId, permission) -> {
            requirePermissionCode(permission);
            Project project = tenantRepository.findProject(projectId)
                .orElseThrow(() -> new AccessDeniedException("project permission is required"));
            try {
                ResolvedPrincipal resolved = authorizationService.requirePermission(
                    principal, project.organizationId(), Optional.of(projectId), Optional.empty(),
                    permission);
                return new KnowledgeProjectContext(
                    project.organizationId(), projectId, resolved.kind().name(), resolved.id());
            } catch (IamAccessDeniedException exception) {
                throw new AccessDeniedException("project permission is required", exception);
            }
        };
    }

    /**
     * @param auditPublisher IAM 事务感知审计发布器
     * @return Knowledge 审计 Port
     */
    @Bean
    public KnowledgeAuditPort knowledgeAuditPort(IamAuditPublisher auditPublisher) {
        return record -> auditPublisher.afterCommit(new IamAuditRecord(
            record.action(), record.actor(), record.resourceType(), record.resourceId(),
            Optional.of(record.organizationId()), Optional.of(record.projectId()), "SUCCEEDED",
            record.occurredAt()));
    }

    /**
     * 验证 Knowledge 与 IAM 权限注册表没有字符串漂移。
     *
     * @param permission Knowledge 请求的权限代码
     */
    private static void requirePermissionCode(String permission) {
        if (!KnowledgePermissions.ALL.contains(permission)
            || !PermissionRegistry.definitionsView().containsKey(permission)) {
            throw new IllegalArgumentException("knowledge permission is not registered");
        }
    }
}
