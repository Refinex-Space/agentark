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

package space.refinex.agentark.runtime.provider.agentscope.compiler;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 仅缓存无 Secret、无 Session 状态编译计划的内存 Single Flight 缓存。
 *
 * @author refinex
 */
public final class SnapshotCompilationCache {

    /**
     * 默认最多保留的无敏感编译计划数量。
     */
    private static final int DEFAULT_MAXIMUM_ENTRIES = 1024;

    /**
     * 同一缓存键共享的编译 Future。
     */
    private final ConcurrentMap<CacheKey, CompletableFuture<AgentScopeCompilationPlan>> plans =
        new ConcurrentHashMap<>();

    /**
     * 当前进程允许保留的最大计划数量。
     */
    private final int maximumEntries;

    /**
     * 使用默认上限创建可丢失重建的编译缓存。
     */
    public SnapshotCompilationCache() {
        this(DEFAULT_MAXIMUM_ENTRIES);
    }

    /**
     * @param maximumEntries 当前进程最多保留的计划数量
     */
    public SnapshotCompilationCache(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
    }

    /**
     * 返回已缓存计划，或使并发请求等待同一次编译。
     *
     * @param key      缓存键
     * @param compiler 无副作用的计划编译器
     * @return 编译计划
     */
    public AgentScopeCompilationPlan getOrCompile(
        CacheKey key, Supplier<AgentScopeCompilationPlan> compiler) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(compiler, "compiler must not be null");
        evictOneCompletedEntryWhenFull(key);
        CompletableFuture<AgentScopeCompilationPlan> future = plans.computeIfAbsent(
            key, ignored -> compile(compiler));
        try {
            return future.join();
        } catch (CompletionException exception) {
            plans.remove(key, future);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    /**
     * 丢弃所有可重建编译计划。
     */
    public void clear() {
        plans.clear();
    }

    /**
     * 返回当前缓存条目数，仅供健康度量和测试使用。
     *
     * @return 缓存条目数
     */
    public int size() {
        return plans.size();
    }

    /**
     * 新键进入满缓存前移除一个已完成条目，绝不取消正在进行的 Single Flight。
     *
     * @param requestedKey 当前请求缓存键
     */
    private void evictOneCompletedEntryWhenFull(CacheKey requestedKey) {
        if (plans.containsKey(requestedKey) || plans.size() < maximumEntries) {
            return;
        }
        plans.entrySet().stream()
            .filter(entry -> entry.getValue().isDone())
            .findFirst()
            .ifPresent(entry -> plans.remove(entry.getKey(), entry.getValue()));
    }

    /**
     * 在当前线程完成首次编译，并将结果包装为可共享 Future。
     *
     * @param compiler 计划编译器
     * @return 已完成或已失败 Future
     */
    private CompletableFuture<AgentScopeCompilationPlan> compile(
        Supplier<AgentScopeCompilationPlan> compiler) {
        CompletableFuture<AgentScopeCompilationPlan> result = new CompletableFuture<>();
        try {
            result.complete(compiler.get());
        } catch (Throwable throwable) {
            result.completeExceptionally(throwable);
        }
        return result;
    }

    /**
     * @param providerId      Runtime Provider 标识
     * @param schemaVersion   Snapshot Schema 版本
     * @param snapshotHash    Snapshot 内容 Hash
     * @param compilerVersion Compiler 语义版本
     * @author refinex
     */
    public record CacheKey(
        String providerId, int schemaVersion, String snapshotHash, String compilerVersion) {

        /**
         * 校验缓存键完整。
         */
        public CacheKey {
            if (providerId == null || providerId.isBlank() || schemaVersion < 1
                || snapshotHash == null || snapshotHash.isBlank()
                || compilerVersion == null || compilerVersion.isBlank()) {
                throw new IllegalArgumentException("snapshot compilation cache key is invalid");
            }
        }
    }
}
