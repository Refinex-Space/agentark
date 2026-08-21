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

import space.refinex.agentark.kernel.id.UserIdentityId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 表示外部身份提供方的 Issuer 与 Subject 映射，不保存外部 Token。
 *
 * @param id          映射标识
 * @param issuer      已验证 Issuer
 * @param subject     Issuer 内稳定 Subject
 * @param displayName 可选展示名称
 * @param email       可选展示邮箱，不作为授权键
 * @param status      生命周期状态
 * @param lastSeenAt  最近成功认证时刻
 * @param version     乐观锁版本
 * @param createdAt   创建时刻
 * @param updatedAt   最近更新时间
 * @author refinex
 */
public record UserIdentity(
    UserIdentityId id,
    String issuer,
    String subject,
    Optional<String> displayName,
    Optional<String> email,
    IamStatus status,
    Instant lastSeenAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验外部身份映射不变量并规范化可选展示字段。
     *
     * @param id          映射标识
     * @param issuer      Issuer
     * @param subject     Subject
     * @param displayName 可选展示名称
     * @param email       可选邮箱
     * @param status      状态
     * @param lastSeenAt  最近认证时刻
     * @param version     非负版本
     * @param createdAt   创建时刻
     * @param updatedAt   更新时间
     */
    public UserIdentity {
        Objects.requireNonNull(id, "id must not be null");
        issuer = IamFieldPolicy.text(issuer, "issuer", 255);
        subject = IamFieldPolicy.text(subject, "subject", 255);
        displayName = normalize(displayName, "displayName", 128);
        email = normalize(email, "email", 320);
        Objects.requireNonNull(status, "status must not be null");
        lastSeenAt = IamFieldPolicy.instant(lastSeenAt, "lastSeenAt");
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
    }

    /**
     * 创建首次认证的活动身份映射。
     *
     * @param issuer      已验证 Issuer
     * @param subject     稳定 Subject
     * @param displayName 可选展示名称
     * @param email       可选展示邮箱
     * @param now         当前时刻
     * @return 新身份映射
     */
    public static UserIdentity create(
        String issuer,
        String subject,
        Optional<String> displayName,
        Optional<String> email,
        Instant now) {

        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new UserIdentity(
            UserIdentityId.generate(),
            issuer,
            subject,
            displayName,
            email,
            IamStatus.ACTIVE,
            timestamp,
            0,
            timestamp,
            timestamp);
    }

    /**
     * 使用内置 Identity 已生成的同一 UUIDv7 创建预置身份投影，便于登录前授权。
     *
     * @param id          与 Gateway Identity Account 相同的 UUIDv7
     * @param issuer      内置 Identity Issuer
     * @param subject     内置账号 Subject
     * @param displayName 可选展示名称
     * @param email       可选邮箱
     * @param status      Gateway 账号状态投影
     * @param now         投影创建时刻；首次真实登录会再次刷新 lastSeenAt
     * @return 预置身份投影
     */
    public static UserIdentity provision(
        UserIdentityId id,
        String issuer,
        String subject,
        Optional<String> displayName,
        Optional<String> email,
        IamStatus status,
        Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new UserIdentity(
            id, issuer, subject, displayName, email, status,
            timestamp, 0, timestamp, timestamp);
    }

    /**
     * 规范化可选展示文本；空白值按缺失处理。
     *
     * @param value     可选值容器
     * @param name      字段名
     * @param maxLength 最大字符数
     * @return 防御性规范化后的可选值
     */
    private static Optional<String> normalize(
        Optional<String> value, String name, int maxLength) {
        Objects.requireNonNull(value, name + " must not be null");
        return value.filter(item -> !item.isBlank())
            .map(item -> IamFieldPolicy.text(item, name, maxLength));
    }
}
