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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;

/**
 * 通过 Control Internal Contract 加载不可变 Snapshot，禁止 Runtime 读取 Control 数据库。
 *
 * @author refinex
 */
public interface SnapshotLoader {

    /**
     * 加载并校验指定 Revision 的 Snapshot、Hash、Schema 与 Runtime Provider。
     *
     * @param revisionId Session 固定的 Revision 标识
     * @return 不可变 Snapshot 描述
     */
    SnapshotDescriptor load(RevisionId revisionId);
}
