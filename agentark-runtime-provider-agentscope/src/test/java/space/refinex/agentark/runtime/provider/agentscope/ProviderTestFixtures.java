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

package space.refinex.agentark.runtime.provider.agentscope;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import space.refinex.agentark.kernel.id.DeploymentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.kernel.id.SnapshotId;
import space.refinex.agentark.kernel.id.TurnId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.domain.RuntimeModels.Run;
import space.refinex.agentark.runtime.domain.RuntimeModels.RunStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.Session;
import space.refinex.agentark.runtime.domain.RuntimeModels.SessionStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;

/**
 * 提供 Phase 12 Compiler、State 和 Engine 测试共用的固定 Snapshot 与 Runtime 对象。
 *
 * @author refinex
 */
public final class ProviderTestFixtures {

    /** 测试使用的固定 UTC 时刻。 */
    public static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    /** Golden Snapshot 固定组织标识。 */
    public static final OrganizationId ORGANIZATION_ID = OrganizationId.parse(
        "0198a4b0-0001-7001-8001-000000000001");

    /** Golden Snapshot 固定项目标识。 */
    public static final ProjectId PROJECT_ID = ProjectId.parse(
        "0198a4b0-0002-7002-8002-000000000002");

    /**
     * 禁止实例化测试夹具类。
     */
    private ProviderTestFixtures() {
    }

    /**
     * 读取 Golden Snapshot，允许局部变异后重算 contentHash 并构造 Descriptor。
     *
     * @param objectMapper Jackson 2 映射器
     * @param mutator      用于单个测试的顶层字段变异器
     * @return 已重算 Hash 的 Snapshot Descriptor
     */
    public static SnapshotDescriptor snapshot(
        ObjectMapper objectMapper, Consumer<Map<String, Object>> mutator) {
        try (InputStream input = ProviderTestFixtures.class.getResourceAsStream(
            "/golden/snapshot-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("golden snapshot resource is missing");
            }
            LinkedHashMap<String, Object> root = objectMapper.readValue(
                input, new TypeReference<LinkedHashMap<String, Object>>() { });
            mutator.accept(root);
            root.remove("contentHash");
            Checksum hash = Checksum.sha256(objectMapper.writeValueAsString(root));
            root.put("contentHash", hash.toString());
            String json = objectMapper.writeValueAsString(root);
            return new SnapshotDescriptor(
                RevisionId.parse((String) root.get("revisionId")),
                SnapshotId.parse((String) root.get("snapshotId")),
                hash,
                ((Number) root.get("schemaVersion")).intValue(),
                (String) root.get("runtimeProvider"),
                json);
        } catch (IOException exception) {
            throw new IllegalStateException("golden snapshot cannot be loaded", exception);
        }
    }

    /**
     * 创建未变异的 Golden Snapshot Descriptor。
     *
     * @param objectMapper Jackson 2 映射器
     * @return Snapshot Descriptor
     */
    public static SnapshotDescriptor snapshot(ObjectMapper objectMapper) {
        return snapshot(objectMapper, ignored -> { });
    }

    /**
     * 创建固定 Revision/Snapshot 的活动 Session。
     *
     * @param descriptor Snapshot Descriptor
     * @return Session
     */
    public static Session session(SnapshotDescriptor descriptor) {
        return new Session(
            SessionId.generate(), ORGANIZATION_ID, PROJECT_ID,
            DeploymentId.generate(), descriptor.revisionId(), descriptor.snapshotId(),
            descriptor.contentHash(), Map.of(), Map.of(), SessionStatus.ACTIVE, 0, 0, NOW, NOW);
    }

    /**
     * 创建带有效 Fencing Token 的 RUNNING Run。
     *
     * @param session Session
     * @return Run
     */
    public static Run run(Session session) {
        return new Run(
            RunId.generate(), session.organizationId(), session.projectId(), session.id(),
            TurnId.generate(), 1, RuntimeProviderDescriptor.PROVIDER_ID,
            RuntimeProviderDescriptor.COMPILER_VERSION, RunStatus.RUNNING, 0,
            new FencingToken(1), Optional.of(NOW), Optional.empty(), Optional.empty(), NOW);
    }
}
