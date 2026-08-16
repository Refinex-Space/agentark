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

package space.refinex.agentark.control.iam.application.port;

import space.refinex.agentark.control.iam.application.IamAuditRecord;

/**
 * 定义必须真实接收 IAM 审计记录的输出端口，实现不得静默丢弃事件。
 *
 * @author refinex
 */
@FunctionalInterface
public interface IamAuditPort {

    /**
     * 追加一条不含 Secret 的审计记录；失败必须向调用方显式抛出。
     *
     * @param record 待追加记录
     */
    void append(IamAuditRecord record);
}
