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

package space.refinex.agentark.server.runtime;

import org.springframework.scheduling.annotation.Scheduled;
import space.refinex.agentark.runtime.port.UsageGovernanceClient;
import space.refinex.agentark.runtime.port.UsageGovernanceStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 以短 Claim 事务和幂等 Control Command 汇聚 Runtime Usage，Control 不可用时保留权威明细。
 *
 * @author refinex
 */
public final class RuntimeUsageGovernanceWorker {

    /** 单轮最大汇聚数量。 */
    private static final int BATCH_SIZE = 50;

    /** 单条 Usage 最大自动重试次数。 */
    private static final int MAX_ATTEMPTS = 8;

    /** 运行用量治理存储端口。 */
    private final UsageGovernanceStore store;

    /** 控制面治理客户端。 */
    private final UsageGovernanceClient client;

    /** UTC 时钟。 */
    private final Clock clock;

    /**
     * 创建 Usage Governance Worker。
     *
     * @param store  Runtime Usage Store
     * @param client Control Governance Client
     * @param clock  UTC 时钟
     */
    public RuntimeUsageGovernanceWorker(
        UsageGovernanceStore store, UsageGovernanceClient client, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 周期汇聚一批 Usage；单条失败不会阻塞同批其他明细。
     */
    @Scheduled(fixedDelayString = "${agentark.runtime.usage-governance-delay:5s}")
    public void exportPendingUsage() {
        Instant now = Instant.now(clock);
        for (var record : store.claimUsageForGovernance(now, BATCH_SIZE)) {
            try {
                client.export(record);
                store.markUsageExported(record.id(), Instant.now(clock));
            } catch (RuntimeException exception) {
                if (record.attempts() >= MAX_ATTEMPTS) {
                    store.markUsageExportFailed(record.id());
                }
            }
        }
    }
}
