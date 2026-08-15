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

package space.refinex.agentark.kernel.snapshot;

/**
 * 定义 AgentArk 自有权限决策；禁止把 Provider 特有的透传决策带入平台契约。
 *
 * @author refinex
 */
public enum PermissionDecision {
    /**
     * 无需人工介入即可执行。
     */
    ALLOW,

    /**
     * 执行前必须进入人工审批流程。
     */
    ASK,

    /**
     * 明确拒绝执行。
     */
    DENY
}
