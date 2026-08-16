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

package space.refinex.agentark.runtime.provider.agentscope.knowledge;

import java.util.Map;
import java.util.Objects;

/**
 * 表示 READY Knowledge Revision 和已冻结的检索参数。
 *
 * @param revisionId       Knowledge Revision 标识
 * @param retrievalProfile 供应商中立检索参数
 * @author refinex
 */
public record KnowledgeBinding(String revisionId, Map<String, Object> retrievalProfile) {

    /**
     * 校验 Knowledge 绑定完整。
     */
    public KnowledgeBinding {
        if (revisionId == null || revisionId.isBlank()) {
            throw new IllegalArgumentException("knowledge revisionId must not be blank");
        }
        retrievalProfile = Map.copyOf(Objects.requireNonNull(
            retrievalProfile, "retrievalProfile must not be null"));
    }
}
