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

package space.refinex.agentark.runtime.provider.agentscope.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonCodec;
import io.agentscope.core.util.JsonUtils;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.CheckpointStore;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将 AgentScope {@link io.agentscope.core.state.AgentStateStore} 定向适配到 AgentArk
 * 版本化 {@code runtime_agent_state}、Checkpoint 端口。
 *
 * <p>此适配器按单个 Run 构造，拒绝其他 Session 的读写，且不创建任何
 * AgentScope 表或本地 JSON 文件。
 *
 * @author refinex
 */
public final class AgentScopeStateStoreAdapter
    implements io.agentscope.core.state.AgentStateStore {

    /**
     * AgentScope ReActAgent 用于持久主状态的稳定键。
     */
    private static final String AGENT_STATE_KEY = "agent_state";

    /**
     * 列表长度辅助键后缀，用于避免列表缩短后读到旧元素。
     */
    private static final String LIST_LENGTH_SUFFIX = ".__length";

    /**
     * 与固定源码 CAS 接口一致的无版本哨兵值。
     */
    private static final long UNVERSIONED = -1L;

    /**
     * 当前固定 Snapshot 的 Session。
     */
    private final Session session;

    /**
     * 当前 Run Attempt。
     */
    private final Run run;

    /**
     * Snapshot 内 Agent 稳定键。
     */
    private final String agentKey;

    /**
     * AgentArk 权威 State Store 端口。
     */
    private final space.refinex.agentark.runtime.port.AgentStateStore stateStore;

    /**
     * AgentArk 可恢复 Checkpoint 端口。
     */
    private final CheckpointStore checkpointStore;

    /**
     * AgentScope 官方 JsonCodec，确保 State 多态语义与固定版本一致。
     */
    private final JsonCodec jsonCodec;

    /**
     * 产生可测试 UTC 时刻的时钟。
     */
    private final Clock clock;

    /**
     * 当前 Run 内的 Checkpoint 序号。
     */
    private final AtomicLong checkpointSequence;

    /**
     * @param session         当前 Session
     * @param run             当前 Run
     * @param agentKey        Snapshot 内 Agent 稳定键
     * @param stateStore      AgentArk State Store
     * @param checkpointStore AgentArk Checkpoint Store
     * @param clock           UTC 时钟
     */
    public AgentScopeStateStoreAdapter(
        Session session,
        Run run,
        String agentKey,
        space.refinex.agentark.runtime.port.AgentStateStore stateStore,
        CheckpointStore checkpointStore,
        Clock clock) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.run = Objects.requireNonNull(run, "run must not be null");
        if (agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("agentKey must not be blank");
        }
        this.agentKey = agentKey;
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.checkpointStore = Objects.requireNonNull(
            checkpointStore, "checkpointStore must not be null");
        this.jsonCodec = JsonUtils.getJsonCodec();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        long next = checkpointStore.findLatestRecoverable(run.id())
            .map(Checkpoint::sequence).orElse(0L);
        this.checkpointSequence = new AtomicLong(next);
    }

    /**
     * 全量替换标量 State，并在成功提交主 Agent State 后追加 Checkpoint。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @param key       State 稳定键
     * @param value     State 值
     */
    @Override
    public synchronized void save(String userId, String sessionId, String key, State value) {
        requireContext(userId, sessionId);
        Objects.requireNonNull(value, "value must not be null");
        persist(key, 0, value, UNVERSIONED);
    }

    /**
     * 声明当前适配器使用 runtime_agent_state 版本实现 CAS。
     *
     * @return 始终为 true
     */
    public boolean supportsVersioning() {
        return true;
    }

    /**
     * 执行基于 AgentArk State Version 的比较并交换。
     *
     * @param userId          AgentScope 用户标识
     * @param sessionId       AgentScope Session 标识
     * @param key             State 键
     * @param value           新 State
     * @param expectedVersion 调用方观察到的版本
     * @return 成功时的新版本，冲突时为 {@link #UNVERSIONED}
     */
    public synchronized long saveIfVersion(
        String userId, String sessionId, String key, State value, long expectedVersion) {
        requireContext(userId, sessionId);
        Objects.requireNonNull(value, "value must not be null");
        long currentVersion = latest(key, 0).map(AgentStateVersion::stateVersion).orElse(0L);
        if (expectedVersion != UNVERSIONED && expectedVersion != currentVersion) {
            return UNVERSIONED;
        }
        return persist(key, 0, value, expectedVersion).stateVersion();
    }

    /**
     * 全量替换列表 State，同时持久长度避免遗留尾元素。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @param key       列表 State 键
     * @param values    完整列表
     */
    @Override
    public synchronized void save(
        String userId, String sessionId, String key, List<? extends State> values) {
        requireContext(userId, sessionId);
        Objects.requireNonNull(values, "values must not be null");
        for (int index = 0; index < values.size(); index++) {
            persist(key, index, Objects.requireNonNull(values.get(index), "state item must not be null"),
                UNVERSIONED);
        }
        persist(key + LIST_LENGTH_SUFFIX, 0, new ListLengthState(values.size()), UNVERSIONED);
    }

    /**
     * 读取标量 State。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @param key       State 键
     * @param type      State 类型
     * @param <T>       State 类型
     * @return 最新已提交 State
     */
    @Override
    public synchronized <T extends State> Optional<T> get(
        String userId, String sessionId, String key, Class<T> type) {
        requireContext(userId, sessionId);
        return latest(key, 0).map(version -> decode(version, type));
    }

    /**
     * 按持久长度读取列表 State。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @param key       列表 State 键
     * @param itemType  列表元素类型
     * @param <T>       State 类型
     * @return 完整列表
     */
    @Override
    public synchronized <T extends State> List<T> getList(
        String userId, String sessionId, String key, Class<T> itemType) {
        requireContext(userId, sessionId);
        int size = latest(key + LIST_LENGTH_SUFFIX, 0)
            .map(version -> decode(version, ListLengthState.class).size()).orElse(0);
        List<T> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            int itemIndex = index;
            AgentStateVersion version = latest(key, itemIndex).orElseThrow(() ->
                persistenceFailure("list state contains a missing item"));
            values.add(decode(version, itemType));
        }
        return List.copyOf(values);
    }

    /**
     * 判断当前 Session 是否已持久主 Agent State。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @return 存在已提交主状态时为 true
     */
    @Override
    public synchronized boolean exists(String userId, String sessionId) {
        requireContext(userId, sessionId);
        return latest(AGENT_STATE_KEY, 0).isPresent();
    }

    /**
     * 拒绝由 Provider 直接删除 Runtime 权威状态。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     */
    @Override
    public void delete(String userId, String sessionId) {
        requireContext(userId, sessionId);
        throw new UnsupportedOperationException(
            "AgentArk Runtime application owns state deletion");
    }

    /**
     * 拒绝由 Provider 直接删除单个 Runtime 状态键。
     *
     * @param userId    AgentScope 用户标识
     * @param sessionId AgentScope Session 标识
     * @param key       State 键
     */
    @Override
    public void delete(String userId, String sessionId, String key) {
        requireContext(userId, sessionId);
        throw new UnsupportedOperationException(
            "AgentArk Runtime application owns state deletion");
    }

    /**
     * 只返回当前 Run 绑定的 Session，禁止 Provider 扫描租户状态。
     *
     * @param userId AgentScope 用户标识
     * @return 当前 Session 标识集合
     */
    @Override
    public Set<String> listSessionIds(String userId) {
        requireUser(userId);
        return exists(userId, session.id().asString())
            ? Set.of(session.id().asString()) : Set.of();
    }

    /**
     * 持久并提交一个新 State Version。
     *
     * @param key             State 键
     * @param itemIndex       列表下标
     * @param value           AgentScope State
     * @param expectedVersion 预期版本
     * @return 已提交 State Version
     */
    private AgentStateVersion persist(
        String key, int itemIndex, State value, long expectedVersion) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("state key must not be blank");
        }
        Optional<AgentStateVersion> current = latest(key, itemIndex);
        long currentVersion = current.map(AgentStateVersion::stateVersion).orElse(0L);
        if (expectedVersion != UNVERSIONED && expectedVersion != currentVersion) {
            throw persistenceFailure("agent state version conflict");
        }
        String json = jsonCodec.toJson(value);
        Checksum hash = Checksum.sha256(json);
        AgentStateVersion pending = new AgentStateVersion(
            JobId.generate(), session.organizationId(), session.projectId(), session.id(), run.id(),
            agentKey, key, itemIndex, currentVersion + 1, RuntimePayload.inline(json), hash, false,
            run.fencingToken(), clock.instant());
        stateStore.append(pending);
        stateStore.commit(pending, run.fencingToken());
        AgentStateVersion committed = new AgentStateVersion(
            pending.id(), pending.organizationId(), pending.projectId(), pending.sessionId(),
            pending.runId(), pending.agentKey(), pending.stateKey(), pending.itemIndex(),
            pending.stateVersion(), pending.payload(), pending.contentHash(), true,
            pending.fencingToken(), pending.createdAt());
        if (AGENT_STATE_KEY.equals(key)) {
            appendCheckpoint(committed);
        }
        return committed;
    }

    /**
     * 为已提交主 Agent State 追加可恢复 Checkpoint。
     *
     * @param stateVersion 已提交状态版本
     */
    private void appendCheckpoint(AgentStateVersion stateVersion) {
        checkpointStore.append(new Checkpoint(
            JobId.generate(), run.id(), checkpointSequence.incrementAndGet(), stateVersion.id(),
            stateVersion.stateVersion(), Math.max(1, run.eventSequence()),
            stateVersion.contentHash(), true, run.fencingToken(), clock.instant()));
    }

    /**
     * 查找当前 Session 和 Agent 键下的最新已提交 State。
     *
     * @param key       State 键
     * @param itemIndex 列表下标
     * @return 最新已提交版本
     */
    private Optional<AgentStateVersion> latest(String key, int itemIndex) {
        return stateStore.findLatestCommitted(session.id(), agentKey, key, itemIndex);
    }

    /**
     * 反序列化已提交内联 State 载荷。
     *
     * @param version State Version
     * @param type    AgentScope State 类型
     * @param <T>     State 类型
     * @return 恢复后 State
     */
    private <T extends State> T decode(AgentStateVersion version, Class<T> type) {
        String json = version.payload().inlineJson().orElseThrow(() ->
            persistenceFailure("external AgentScope state payload is not supported by this adapter"));
        if (!Checksum.sha256(json).equals(version.contentHash())) {
            throw persistenceFailure("agent state content hash does not match");
        }
        try {
            return jsonCodec.fromJson(json, type);
        } catch (RuntimeException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.STATE_PERSISTENCE_FAILED,
                "agent state payload cannot be decoded", exception);
        }
    }

    /**
     * 校验 AgentScope 调用未跨越当前 Project 和 Session 边界。
     *
     * @param userId    AgentScope 用户标识，固定为 Project ID
     * @param sessionId AgentScope Session 标识
     */
    private void requireContext(String userId, String sessionId) {
        requireUser(userId);
        if (!session.id().asString().equals(sessionId)) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.STATE_PERSISTENCE_FAILED,
                "AgentScope state access crossed the bound session");
        }
    }

    /**
     * 校验 AgentScope userId 为空或固定为当前 Project ID，禁止跨租户状态访问。
     *
     * <p>AgentScope 2.0.2 在首次状态恢复时传入 {@code null} userId；适配器本身已经按单 Run、
     * 固定 Project 与 Session 构造，因此只兼容该空值，不接受其他非空 Project。
     *
     * @param userId AgentScope 用户标识
     */
    private void requireUser(String userId) {
        if (userId != null && !session.projectId().asString().equals(userId)) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.STATE_PERSISTENCE_FAILED,
                "AgentScope state access crossed the bound project");
        }
    }

    /**
     * 创建统一状态持久化异常。
     *
     * @param message 诊断摘要
     * @return Provider 异常
     */
    private AgentScopeProviderException persistenceFailure(String message) {
        return new AgentScopeProviderException(
            ProviderErrorCode.STATE_PERSISTENCE_FAILED, message);
    }

    /**
     * 保存 AgentScope 列表 State 有效长度的局部辅助状态。
     *
     * @param size 列表有效元素数
     * @author refinex
     */
    private record ListLengthState(int size) implements State {

        /**
         * 校验列表长度非负。
         *
         * @param size 列表有效元素数
         */
        @JsonCreator
        private ListLengthState(@JsonProperty("size") int size) {
            if (size < 0) {
                throw new IllegalArgumentException("list state size must not be negative");
            }
            this.size = size;
        }
    }
}
