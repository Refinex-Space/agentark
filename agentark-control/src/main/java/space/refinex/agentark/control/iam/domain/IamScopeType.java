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
 * 定义角色与角色绑定可约束的资源层级。
 *
 * @author refinex
 */
public enum IamScopeType {

    /**
     * 授权覆盖单个组织以及其允许继承的子资源。
     */
    ORGANIZATION,

    /**
     * 授权覆盖单个项目以及其允许继承的环境。
     */
    PROJECT,

    /**
     * 授权只覆盖单个环境。
     */
    ENVIRONMENT
}
