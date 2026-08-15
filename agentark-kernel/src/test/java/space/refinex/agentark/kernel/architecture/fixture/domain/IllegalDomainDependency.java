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

package space.refinex.agentark.kernel.architecture.fixture.domain;

import space.refinex.agentark.kernel.architecture.fixture.adapter.TestAdapter;

/**
 * 故意依赖适配器的领域类型，用于证明 ArchUnit 规则能够发现违规依赖。
 *
 * @author refinex
 * @param adapter 被错误引用的适配器实例
 */
public record IllegalDomainDependency(TestAdapter adapter) {}
