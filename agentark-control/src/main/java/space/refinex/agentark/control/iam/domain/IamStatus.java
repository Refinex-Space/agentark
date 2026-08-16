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
 * 定义 IAM 可变聚合使用的稳定状态编码，数据库保存名称而非 ordinal。
 *
 * @author refinex
 */
public enum IamStatus {

    /**
     * 资源可以被授权流程正常使用。
     */
    ACTIVE,

    /**
     * 资源暂时不可用于新增访问，但保留历史关系。
     */
    SUSPENDED,

    /**
     * 资源已经撤销或停用，不能恢复凭据能力。
     */
    DISABLED
}
