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

package space.refinex.agentark.control.catalog.application;

import space.refinex.agentark.kernel.id.StrongId;

import java.util.List;

/**
 * 表示两个不可变版本间发生变化的 JSON Pointer 路径，不回显 Secret 值。
 *
 * @param baseVersionId 基准版本
 * @param targetVersionId 目标版本
 * @param changedPaths 发生变化的 JSON Pointer 路径
 * @author refinex
 */
public record CatalogVersionDiff(
    StrongId baseVersionId, StrongId targetVersionId, List<String> changedPaths) {

    /** 防御性复制差异路径。 */
    public CatalogVersionDiff {
        java.util.Objects.requireNonNull(baseVersionId, "baseVersionId must not be null");
        java.util.Objects.requireNonNull(targetVersionId, "targetVersionId must not be null");
        changedPaths = List.copyOf(changedPaths);
    }
}

