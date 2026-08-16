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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.net.URI;

/**
 * 按租户和固定 Endpoint Identity 解析经过 Control SSRF 审核的 HTTPS 地址。
 *
 * @author refinex
 */
@FunctionalInterface
public interface OutboundEndpointResolver {

    /**
     * 解析固定端点；实现必须拒绝私网、回环、链路本地、UserInfo 和非 HTTPS 地址。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param endpointId     固定 Endpoint Identity
     * @return 已验证 HTTPS URI
     */
    URI resolve(OrganizationId organizationId, ProjectId projectId, String endpointId);
}
