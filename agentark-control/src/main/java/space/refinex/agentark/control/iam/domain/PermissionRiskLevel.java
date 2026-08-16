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

package space.refinex.agentark.control.iam.domain;

/**
 * 标记权限执行风险，供审计、审批和后续策略治理使用。
 *
 * @author refinex
 */
public enum PermissionRiskLevel {

    /**
     * 只读且不返回高敏信息的低风险操作。
     */
    LOW,

    /**
     * 改变普通配置或成员关系的中风险操作。
     */
    MEDIUM,

    /**
     * 改变授权、凭据或删除关键资源的高风险操作。
     */
    HIGH
}
