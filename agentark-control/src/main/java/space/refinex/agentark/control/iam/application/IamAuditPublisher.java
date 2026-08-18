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

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.refinex.agentark.control.iam.application.port.IamAuditPort;

import java.util.Objects;

/**
 * 在业务事务提交前调用真实审计端口，使 Control 变更和持久 Audit 同成同败。
 *
 * @author refinex
 */
public final class IamAuditPublisher {

    /**
     * 真实审计输出端口。
     */
    private final IamAuditPort auditPort;

    /**
     * 创建事务感知审计发布器。
     *
     * @param auditPort 不得为空或静默吞事件的审计端口
     */
    public IamAuditPublisher(IamAuditPort auditPort) {
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort must not be null");
    }

    /**
     * 在当前事务提交前追加记录；没有事务时立即追加。
     *
     * @param record 不含 Secret 的审计记录
     */
    public void append(IamAuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditPort.append(record);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {

                /**
                 * 在提交前把记录交给真实审计 Sink，使数据库实现加入同一事务。
                 */
                @Override
                public void beforeCommit(boolean readOnly) {
                    if (readOnly) {
                        throw new IllegalStateException("audit append requires a write transaction");
                    }
                    auditPort.append(record);
                }
            });
    }
}
