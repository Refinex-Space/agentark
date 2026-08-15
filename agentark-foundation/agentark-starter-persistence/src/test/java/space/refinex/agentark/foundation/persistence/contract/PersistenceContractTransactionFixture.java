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

package space.refinex.agentark.foundation.persistence.contract;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供契约测试专用事务边界，验证异常会回滚已经执行的 Mapper 写入。
 *
 * @author refinex
 */
@Service
public class PersistenceContractTransactionFixture {

    /**
     * 契约测试 Mapper。
     */
    private final PersistenceContractMapper mapper;

    /**
     * 创建事务测试夹具。
     *
     * @param mapper 契约测试 Mapper
     */
    public PersistenceContractTransactionFixture(PersistenceContractMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在事务内写入后故意失败，用于证明 Spring 事务会回滚该写入。
     *
     * @param record 待写入记录
     * @throws IllegalStateException 始终抛出以触发回滚
     */
    @Transactional
    public void insertThenFail(PersistenceContractRecordDO record) {
        mapper.insert(record);
        throw new IllegalStateException("intentional contract rollback");
    }
}
