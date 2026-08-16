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

package space.refinex.agentark.control.iam.application;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.iam.application.port.IdentityRepository;
import space.refinex.agentark.control.iam.domain.UserIdentity;

import java.time.Clock;
import java.util.Optional;

/**
 * 在独立写事务中建立首次出现的外部 Issuer/Subject 身份映射。
 *
 * @author refinex
 */
public class IamIdentityMappingService {

    /**
     * 身份持久化端口。
     */
    private final IdentityRepository identityRepository;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建外部身份映射服务。
     *
     * @param identityRepository 身份持久化端口
     * @param clock              UTC 时钟
     */
    public IamIdentityMappingService(IdentityRepository identityRepository, Clock clock) {
        this.identityRepository = java.util.Objects.requireNonNull(
            identityRepository, "identityRepository must not be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 幂等创建外部身份；独立事务避免污染调用方只读事务。
     *
     * @param issuer  已验证身份签发方
     * @param subject 稳定外部主体
     * @return 数据库中的唯一身份映射
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserIdentity resolveOrCreate(String issuer, String subject) {
        return identityRepository.findUserIdentity(issuer, subject)
            .orElseGet(() -> identityRepository.upsertUserIdentity(UserIdentity.create(
                issuer,
                subject,
                Optional.empty(),
                Optional.empty(),
                clock.instant())));
    }
}
