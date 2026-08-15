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

package space.refinex.agentark.scheduling.adapter.out.persistence;

import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;

/**
 * 验证 Scheduler 只迁移 agentark_scheduler，并拒绝读取 Runtime Schema。
 *
 * @author refinex
 */
class SchedulerMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 创建 Scheduler MySQL 迁移测试实例。 */
    SchedulerMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 返回 Scheduler 独占 Schema 名称。
     *
     * @return Scheduler Schema 名称
     */
    @Override
    protected String schemaName() {
        return "agentark_scheduler";
    }

    /**
     * 返回用于证明越权失败的 Runtime Schema 名称。
     *
     * @return Runtime Schema 名称
     */
    @Override
    protected String forbiddenSchemaName() {
        return "agentark_runtime";
    }

    /**
     * 返回 Scheduler 独占 Flyway Location。
     *
     * @return Scheduler Migration classpath Location
     */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/scheduler";
    }
}
