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

package space.refinex.agentark.runtime.provider.agentscope.error;

/**
 * 定义 AgentScope Provider 对 Runtime 暴露的稳定错误码。
 *
 * @author refinex
 */
public enum ProviderErrorCode {
    /**
     * Snapshot Schema 版本不受支持。
     */
    SNAPSHOT_SCHEMA_UNSUPPORTED,
    /**
     * Snapshot 指定了其他 Runtime Provider。
     */
    SNAPSHOT_PROVIDER_MISMATCH,
    /**
     * Snapshot 内容 Hash 与契约不一致。
     */
    SNAPSHOT_HASH_MISMATCH,
    /**
     * Snapshot 结构或字段值非法。
     */
    SNAPSHOT_INVALID,
    /**
     * Snapshot 要求的能力不受支持。
     */
    CAPABILITY_UNSUPPORTED,
    /**
     * 运行时无法解析所需 SecretRef。
     */
    SECRET_UNAVAILABLE,
    /**
     * Model 组件无法创建或能力不匹配。
     */
    MODEL_CONFIGURATION_INVALID,
    /**
     * MCP Tool 名冲突或 MCP 组件配置失败。
     */
    MCP_CONFIGURATION_INVALID,
    /**
     * Skill 制品不存在或完整性检查失败。
     */
    SKILL_ARTIFACT_INVALID,
    /**
     * AgentScope Harness 执行失败。
     */
    EXECUTION_FAILED,
    /**
     * Provider 调用超过 Snapshot 固定超时。
     */
    PROVIDER_TIMEOUT,
    /**
     * Provider 明确返回限流，调用方只能按策略创建新 Attempt。
     */
    PROVIDER_RATE_LIMITED,
    /**
     * Turn 输入只有 ObjectRef，但当前未注入载荷解析端口。
     */
    INPUT_PAYLOAD_UNAVAILABLE,
    /**
     * 暂停 Run 缺少可恢复的 HITL 状态。
     */
    RESUME_STATE_UNAVAILABLE,
    /**
     * AgentScope State 与 AgentArk 持久化端口之间转换失败。
     */
    STATE_PERSISTENCE_FAILED
}
