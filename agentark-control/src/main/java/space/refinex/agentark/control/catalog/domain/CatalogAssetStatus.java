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

package space.refinex.agentark.control.catalog.domain;

/**
 * 定义稳定资产身份生命周期。
 *
 * @author refinex
 */
public enum CatalogAssetStatus {
    /** 允许创建新版本和引用。 */
    ACTIVE,
    /** 仅保留历史读取和既有引用，不允许创建新版本。 */
    ARCHIVED
}

