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

package space.refinex.agentark.server.gateway;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.*;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 使用 Gateway 独占 DataSource 持久化账号、凭据、锁定、安全事件和 Outbox。
 *
 * @author refinex
 */
public final class GatewayIdentityRepository {

    /**
     * 固定 Bootstrap 管理员 UUIDv7，与旧本地身份 Subject 保持一致。
     */
    public static final UUID BOOTSTRAP_ADMIN_ID =
        UUID.fromString("019d0000-0000-7000-8000-000000000001");

    /**
     * 内置平台管理员角色 UUIDv7。
     */
    private static final UUID PLATFORM_ADMIN_ROLE_ID =
        UUID.fromString("019d0000-0000-7000-8000-000000000101");

    /**
     * JDBC 访问器，只连接 agentark_identity。
     */
    private final JdbcTemplate jdbc;

    /**
     * 本地事务模板。
     */
    private final TransactionTemplate transactions;

    /**
     * Jackson 3 安全 JSON 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 内置 Identity 稳定 Issuer。
     */
    private final String issuer;

    /**
     * 创建 Identity Repository。
     *
     * @param jdbc         Gateway Identity JDBC 访问器
     * @param transactions Gateway Identity 本地事务
     * @param objectMapper JSON 映射器
     */
    public GatewayIdentityRepository(
        JdbcTemplate jdbc,
        TransactionTemplate transactions,
        ObjectMapper objectMapper,
        GatewayIdentityProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.issuer = Objects.requireNonNull(properties, "properties must not be null")
            .getIssuer().toString();
    }

    /**
     * 在 Identity 单库事务中执行幂等记录与账号写入，保证成功响应对应一个持久提交点。
     *
     * @param work 不得返回空值的事务工作
     * @param <T>  事务结果类型
     * @return 已提交的事务结果
     */
    public <T> T transaction(java.util.function.Supplier<T> work) {
        T result = transactions.execute(status -> work.get());
        return Objects.requireNonNull(result, "identity transaction returned null");
    }

    /**
     * 首次启动时原子创建平台管理员、密码、锁定行、角色绑定、安全事件和 Outbox。
     *
     * @param username     规范用户名
     * @param email        管理员邮箱
     * @param displayName  展示名称
     * @param passwordHash Argon2id 摘要
     * @param now          UTC 当前时间
     */
    public void bootstrap(
        String username, String email, String displayName, String passwordHash, Instant now) {
        transactions.executeWithoutResult(status -> {
            String state = jdbc.queryForObject(
                "SELECT state FROM identity_bootstrap_state WHERE singleton_key = 'built-in-identity' FOR UPDATE",
                String.class);
            if ("INITIALIZED".equals(state)) {
                return;
            }
            jdbc.update("UPDATE identity_bootstrap_state SET state = 'INITIALIZING', version = version + 1, updated_at = ? WHERE singleton_key = 'built-in-identity'",
                timestamp(now));
            jdbc.update("""
                    INSERT INTO identity_account
                        (id, username, username_normalized, email, email_normalized, display_name,
                         status, password_change_required, auth_version, last_login_at, version,
                         created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, 0, NULL, 0,
                            'agentark-bootstrap', 'agentark-bootstrap', ?, ?)
                    """, bytes(BOOTSTRAP_ADMIN_ID), username, normalizeUsername(username), email,
                normalizeEmail(email), displayName, timestamp(now), timestamp(now));
            jdbc.update("""
                INSERT INTO identity_password_credential
                    (account_id, password_hash, hash_algorithm, hash_version, pepper_version,
                     temporary, changed_at, version)
                VALUES (?, ?, 'ARGON2ID', 1, 0, TRUE, ?, 0)
                """, bytes(BOOTSTRAP_ADMIN_ID), passwordHash, timestamp(now));
            jdbc.update("INSERT INTO identity_login_guard (account_id, failure_count, version) VALUES (?, 0, 0)",
                bytes(BOOTSTRAP_ADMIN_ID));
            jdbc.update("INSERT INTO identity_account_role (account_id, role_id, created_by, created_at) VALUES (?, ?, 'agentark-bootstrap', ?)",
                bytes(BOOTSTRAP_ADMIN_ID), bytes(PLATFORM_ADMIN_ROLE_ID), timestamp(now));
            event(BOOTSTRAP_ADMIN_ID, "ACCOUNT_CREATED", "SUCCESS", "agentark-bootstrap", "BOOTSTRAP", now);
            outbox(BOOTSTRAP_ADMIN_ID, "IDENTITY_ACCOUNT_CREATED", username, email, displayName, "ACTIVE", now);
            jdbc.update("""
                UPDATE identity_bootstrap_state
                SET state = 'INITIALIZED', admin_account_id = ?, initialized_at = ?,
                    version = version + 1, updated_at = ?
                WHERE singleton_key = 'built-in-identity'
                """, bytes(BOOTSTRAP_ADMIN_ID), timestamp(now), timestamp(now));
        });
    }

    /**
     * 按用户名或邮箱读取账号与凭据，错误登录键返回空。
     */
    public Optional<Account> findByLogin(String login) {
        String normalized = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
        List<Account> accounts = jdbc.query("""
            SELECT a.id, a.username, a.email, a.display_name, a.status,
                   a.password_change_required, a.auth_version, a.version, a.last_login_at,
                   c.password_hash, c.temporary, g.locked_until
            FROM identity_account a
            JOIN identity_password_credential c ON c.account_id = a.id
            LEFT JOIN identity_login_guard g ON g.account_id = a.id
            WHERE a.username_normalized = ? OR a.email_normalized = ?
            LIMIT 1
            """, (result, rowNumber) -> account(result), normalized, normalized);
        return accounts.stream().findFirst();
    }

    /**
     * 按 UUID 读取账号与凭据。
     */
    public Optional<Account> findById(UUID accountId) {
        List<Account> accounts = jdbc.query("""
            SELECT a.id, a.username, a.email, a.display_name, a.status,
                   a.password_change_required, a.auth_version, a.version, a.last_login_at,
                   c.password_hash, c.temporary, g.locked_until
            FROM identity_account a
            JOIN identity_password_credential c ON c.account_id = a.id
            LEFT JOIN identity_login_guard g ON g.account_id = a.id
            WHERE a.id = ?
            """, (result, rowNumber) -> account(result), bytes(accountId));
        return accounts.stream().findFirst();
    }

    /**
     * 锁定当前凭据行并读取账号，用于把旧密码校验与新摘要写入放在同一事务。
     *
     * @param accountId 当前登录账号 UUIDv7
     * @return 账号与当前凭据；账号不存在时为空
     */
    public Optional<Account> findByIdForPasswordChange(UUID accountId) {
        List<byte[]> locked = jdbc.query(
            "SELECT account_id FROM identity_password_credential WHERE account_id = ? FOR UPDATE",
            (result, rowNumber) -> result.getBytes("account_id"),
            bytes(accountId));
        return locked.isEmpty() ? Optional.empty() : findById(accountId);
    }

    /**
     * 返回最近五个历史摘要，用于阻止短期密码复用。
     */
    public List<String> recentPasswordHashes(UUID accountId) {
        return jdbc.queryForList("""
            SELECT password_hash
            FROM identity_password_history
            WHERE account_id = ?
            ORDER BY history_sequence DESC
            LIMIT 5
            """, String.class, bytes(accountId));
    }

    /**
     * 记录失败并在达到阈值时设置 MySQL 权威锁定。
     */
    public void recordFailure(Account account, int threshold, Duration lockDuration, Instant now) {
        transactions.executeWithoutResult(status -> {
            Integer failures = jdbc.queryForObject(
                "SELECT failure_count FROM identity_login_guard WHERE account_id = ? FOR UPDATE",
                Integer.class, bytes(account.id()));
            int next = (failures == null ? 0 : failures) + 1;
            Instant lockedUntil = next >= threshold ? now.plus(lockDuration) : null;
            jdbc.update("""
                UPDATE identity_login_guard
                SET failure_count = ?, window_started_at = COALESCE(window_started_at, ?),
                    last_failure_at = ?, locked_until = ?, version = version + 1
                WHERE account_id = ?
                """, next, timestamp(now), timestamp(now), nullableTimestamp(lockedUntil), bytes(account.id()));
            event(account.id(), next >= threshold ? "ACCOUNT_LOCKED" : "LOGIN_FAILED",
                "FAILURE", null, next >= threshold ? "FAILURE_THRESHOLD" : "BAD_CREDENTIALS", now);
        });
    }

    /**
     * 记录未知登录键失败，账号 ID 为空且不保存原始用户名或邮箱。
     */
    public void recordUnknownFailure(Instant now) {
        event(null, "LOGIN_FAILED", "FAILURE", null, "BAD_CREDENTIALS", now);
    }

    /**
     * 记录已认证用户提交错误当前密码的拒绝事件，不记录密码或请求正文。
     *
     * @param accountId 当前账号 UUIDv7
     * @param now       UTC 当前时间
     */
    public void recordPasswordChangeDenied(UUID accountId, Instant now) {
        event(accountId, "PASSWORD_CHANGED", "DENIED", accountId.toString(),
            "CURRENT_PASSWORD_MISMATCH", now);
    }

    /**
     * 成功取得完整会话后清除失败状态并刷新最近登录。
     */
    public void recordSuccess(UUID accountId, Instant now) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE identity_login_guard SET failure_count = 0, window_started_at = NULL, last_failure_at = NULL, locked_until = NULL, version = version + 1 WHERE account_id = ?",
                bytes(accountId));
            jdbc.update("UPDATE identity_account SET last_login_at = ?, updated_at = ? WHERE id = ?",
                timestamp(now), timestamp(now), bytes(accountId));
            event(accountId, "LOGIN_SUCCEEDED", "SUCCESS", accountId.toString(), null, now);
        });
    }

    /**
     * 修改当前密码、追加历史、清除强制改密并递增认证版本。
     */
    public Account changePassword(UUID accountId, String passwordHash, String actor, Instant now) {
        transactions.executeWithoutResult(status -> {
            String previous = jdbc.queryForObject(
                "SELECT password_hash FROM identity_password_credential WHERE account_id = ? FOR UPDATE",
                String.class, bytes(accountId));
            Long sequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(history_sequence), 0) + 1 FROM identity_password_history WHERE account_id = ?",
                Long.class, bytes(accountId));
            jdbc.update("INSERT INTO identity_password_history (account_id, history_sequence, password_hash, hash_version, pepper_version, changed_at) VALUES (?, ?, ?, 1, 0, ?)",
                bytes(accountId), sequence, previous, timestamp(now));
            jdbc.update("UPDATE identity_password_credential SET password_hash = ?, temporary = FALSE, changed_at = ?, version = version + 1 WHERE account_id = ?",
                passwordHash, timestamp(now), bytes(accountId));
            jdbc.update("UPDATE identity_account SET password_change_required = FALSE, auth_version = auth_version + 1, version = version + 1, updated_by = ?, updated_at = ? WHERE id = ?",
                actor, timestamp(now), bytes(accountId));
            jdbc.update("UPDATE identity_login_guard SET failure_count = 0, window_started_at = NULL, last_failure_at = NULL, locked_until = NULL, version = version + 1 WHERE account_id = ?",
                bytes(accountId));
            event(accountId, "PASSWORD_CHANGED", "SUCCESS", actor, null, now);
        });
        return findById(accountId).orElseThrow();
    }

    /**
     * 创建普通本地账号、临时密码、锁定行和 Outbox。
     */
    public Account createAccount(
        String username, String email, String displayName, String passwordHash, String actor, Instant now) {
        UUID accountId = GatewayIdentityIds.generate();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO identity_account
                            (id, username, username_normalized, email, email_normalized, display_name,
                             status, password_change_required, auth_version, last_login_at, version,
                             created_by, updated_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, 0, NULL, 0, ?, ?, ?, ?)
                        """, bytes(accountId), username, normalizeUsername(username), email,
                    normalizeEmail(email), displayName, actor, actor, timestamp(now), timestamp(now));
                jdbc.update("INSERT INTO identity_password_credential (account_id, password_hash, hash_algorithm, hash_version, pepper_version, temporary, changed_at, version) VALUES (?, ?, 'ARGON2ID', 1, 0, TRUE, ?, 0)",
                    bytes(accountId), passwordHash, timestamp(now));
                jdbc.update("INSERT INTO identity_login_guard (account_id, failure_count, version) VALUES (?, 0, 0)",
                    bytes(accountId));
                event(accountId, "ACCOUNT_CREATED", "SUCCESS", actor, null, now);
                outbox(accountId, "IDENTITY_ACCOUNT_CREATED", username, email, displayName, "ACTIVE", now);
            });
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("username or email already exists", exception);
        }
        return findById(accountId).orElseThrow();
    }

    /**
     * 预留创建账号幂等键；已成功同参重放返回原账号，执行中或异参拒绝。
     */
    public Optional<UUID> reserveCreateIdempotency(
        String actor, String key, String requestHash, Instant now) {
        return reserveIdempotency(actor, "CREATE_ACCOUNT", key, requestHash, now);
    }

    /**
     * 预留密码重置幂等键；成功重放只返回目标账号，不再次返回临时密码。
     */
    public Optional<UUID> reserveResetPasswordIdempotency(
        String actor, String key, String requestHash, Instant now) {
        return reserveIdempotency(actor, "RESET_PASSWORD", key, requestHash, now);
    }

    /**
     * 按操作类型预留幂等键并拒绝同键异参或并发执行。
     */
    private Optional<UUID> reserveIdempotency(
        String actor, String operation, String key, String requestHash, Instant now) {
        try {
            UUID id = GatewayIdentityIds.generate();
            jdbc.update("""
                    INSERT INTO identity_idempotency
                        (id, actor_subject, operation_type, idempotency_key, request_hash,
                         target_account_id, status, created_at, completed_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, NULL, 'STARTED', ?, NULL, ?)
                    """, bytes(id), actor, operation, key, requestHash,
                timestamp(now), timestamp(now.plus(Duration.ofHours(24))));
            return Optional.empty();
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> existing = jdbc.queryForMap("""
                SELECT request_hash, status, target_account_id
                FROM identity_idempotency
                WHERE actor_subject = ? AND operation_type = ? AND idempotency_key = ?
                """, actor, operation, key);
            if (!requestHash.equals(existing.get("request_hash"))) {
                throw new IllegalArgumentException("idempotency key was reused with different input");
            }
            if ("FAILED".equals(existing.get("status"))) {
                jdbc.update("""
                    UPDATE identity_idempotency
                    SET status = 'STARTED', completed_at = NULL
                    WHERE actor_subject = ? AND operation_type = ?
                      AND idempotency_key = ? AND status = 'FAILED'
                    """, actor, operation, key);
                return Optional.empty();
            }
            if (!"SUCCEEDED".equals(existing.get("status")) || existing.get("target_account_id") == null) {
                throw new IllegalStateException("identity operation is already in progress");
            }
            return Optional.of(uuid((byte[]) existing.get("target_account_id")));
        }
    }

    /**
     * 将创建账号幂等记录绑定成功资源。
     */
    public void completeCreateIdempotency(String actor, String key, UUID accountId, Instant now) {
        completeIdempotency(actor, "CREATE_ACCOUNT", key, accountId, now);
    }

    /**
     * 将密码重置幂等记录绑定目标账号。
     */
    public void completeResetPasswordIdempotency(
        String actor, String key, UUID accountId, Instant now) {
        completeIdempotency(actor, "RESET_PASSWORD", key, accountId, now);
    }

    /**
     * 将指定操作的幂等记录绑定成功资源。
     */
    private void completeIdempotency(
        String actor, String operation, String key, UUID accountId, Instant now) {
        jdbc.update("""
            UPDATE identity_idempotency
            SET target_account_id = ?, status = 'SUCCEEDED', completed_at = ?
            WHERE actor_subject = ? AND operation_type = ?
              AND idempotency_key = ? AND status = 'STARTED'
            """, bytes(accountId), timestamp(now), actor, operation, key);
    }

    /**
     * 将失败创建记录置为可同参重试状态，不保存异常正文。
     */
    public void failCreateIdempotency(String actor, String key, Instant now) {
        failIdempotency(actor, "CREATE_ACCOUNT", key, now);
    }

    /**
     * 将失败密码重置记录置为可同参重试状态。
     */
    public void failResetPasswordIdempotency(String actor, String key, Instant now) {
        failIdempotency(actor, "RESET_PASSWORD", key, now);
    }

    /**
     * 将指定失败操作置为可同参重试状态，不保存异常正文。
     */
    private void failIdempotency(String actor, String operation, String key, Instant now) {
        jdbc.update("""
            UPDATE identity_idempotency
            SET status = 'FAILED', completed_at = ?
            WHERE actor_subject = ? AND operation_type = ?
              AND idempotency_key = ? AND status = 'STARTED'
            """, timestamp(now), actor, operation, key);
    }

    /**
     * 列出最多一百个账号安全视图。
     */
    public List<AccountView> listAccounts() {
        return jdbc.query("""
            SELECT a.id, a.username, a.email, a.display_name, a.status,
                   a.password_change_required, a.auth_version, a.version, a.last_login_at,
                   c.password_hash, c.temporary, g.locked_until
            FROM identity_account a
            JOIN identity_password_credential c ON c.account_id = a.id
            LEFT JOIN identity_login_guard g ON g.account_id = a.id
            ORDER BY a.created_at, a.id
            LIMIT 100
            """, (result, rowNumber) -> view(account(result)));
    }

    /**
     * 列出最近一百条身份安全事件，不返回请求正文、地址或 User-Agent 原文。
     */
    public List<SecurityEventView> listSecurityEvents() {
        return jdbc.query("""
            SELECT id, account_id, event_type, result, actor_subject, detail_code, occurred_at
            FROM identity_security_event
            ORDER BY occurred_at DESC, id DESC
            LIMIT 100
            """, (result, rowNumber) -> {
            byte[] accountId = result.getBytes("account_id");
            return new SecurityEventView(
                uuid(result.getBytes("id")).toString(),
                accountId == null ? null : uuid(accountId).toString(),
                result.getString("event_type"),
                result.getString("result"),
                result.getString("actor_subject"),
                result.getString("detail_code"),
                result.getTimestamp("occurred_at").toInstant());
        });
    }

    /**
     * 按乐观锁改变账号状态并递增认证版本。
     */
    public Account updateStatus(UUID accountId, AccountStatus newStatus, long expectedVersion, String actor, Instant now) {
        Account updated = transactions.execute(status -> {
            int changed = jdbc.update("""
                UPDATE identity_account
                SET status = ?, auth_version = auth_version + 1, version = version + 1,
                    updated_by = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """, newStatus.name(), actor, timestamp(now), bytes(accountId), expectedVersion);
            if (changed != 1) {
                throw new IllegalStateException("identity account version conflict or account missing");
            }
            event(accountId, switch (newStatus) {
                case ACTIVE -> "ACCOUNT_CREATED";
                case SUSPENDED -> "ACCOUNT_SUSPENDED";
                case DISABLED -> "ACCOUNT_DISABLED";
            }, "SUCCESS", actor, null, now);
            Account account = findById(accountId).orElseThrow();
            outbox(accountId, newStatus == AccountStatus.DISABLED
                    ? "IDENTITY_ACCOUNT_DISABLED" : "IDENTITY_ACCOUNT_UPDATED",
                account.username(), account.email(), account.displayName(), account.status().name(), now);
            return account;
        });
        return Objects.requireNonNull(updated, "identity status transaction returned null");
    }

    /**
     * 重置为临时密码并使所有旧会话版本失效。
     */
    public Account resetPassword(UUID accountId, String passwordHash, String actor, Instant now) {
        transactions.executeWithoutResult(status -> {
            String previous = jdbc.queryForObject(
                "SELECT password_hash FROM identity_password_credential WHERE account_id = ? FOR UPDATE",
                String.class, bytes(accountId));
            Long sequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(history_sequence), 0) + 1 FROM identity_password_history WHERE account_id = ?",
                Long.class, bytes(accountId));
            jdbc.update("INSERT INTO identity_password_history (account_id, history_sequence, password_hash, hash_version, pepper_version, changed_at) VALUES (?, ?, ?, 1, 0, ?)",
                bytes(accountId), sequence, previous, timestamp(now));
            jdbc.update("UPDATE identity_password_credential SET password_hash = ?, temporary = TRUE, changed_at = ?, version = version + 1 WHERE account_id = ?",
                passwordHash, timestamp(now), bytes(accountId));
            jdbc.update("UPDATE identity_account SET password_change_required = TRUE, auth_version = auth_version + 1, version = version + 1, updated_by = ?, updated_at = ? WHERE id = ?",
                actor, timestamp(now), bytes(accountId));
            jdbc.update("UPDATE identity_login_guard SET failure_count = 0, window_started_at = NULL, last_failure_at = NULL, locked_until = NULL, version = version + 1 WHERE account_id = ?",
                bytes(accountId));
            event(accountId, "PASSWORD_RESET", "SUCCESS", actor, null, now);
        });
        return findById(accountId).orElseThrow();
    }

    /**
     * 清除账号锁定但不改变密码或状态。
     */
    public Account unlock(UUID accountId, String actor, Instant now) {
        jdbc.update("UPDATE identity_login_guard SET failure_count = 0, window_started_at = NULL, last_failure_at = NULL, locked_until = NULL, version = version + 1 WHERE account_id = ?",
            bytes(accountId));
        event(accountId, "ACCOUNT_UNLOCKED", "SUCCESS", actor, null, now);
        return findById(accountId).orElseThrow();
    }

    /**
     * 领取一条到期 Identity Outbox；多副本通过行锁与租约避免并发重复领取。
     */
    public Optional<OutboxItem> claimOutbox(String owner, Instant now) {
        return transactions.execute(status -> {
            List<OutboxItem> items = jdbc.query("""
                    SELECT id, aggregate_id, event_type, CAST(payload_json AS CHAR) AS payload_json, attempts
                    FROM identity_outbox
                    WHERE (status = 'PENDING' AND available_at <= ?)
                       OR (status = 'CLAIMED' AND claimed_until < ?)
                    ORDER BY available_at, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """, (result, rowNumber) -> new OutboxItem(
                    uuid(result.getBytes("id")),
                    uuid(result.getBytes("aggregate_id")),
                    result.getString("event_type"),
                    result.getString("payload_json"),
                    result.getInt("attempts")),
                timestamp(now), timestamp(now));
            if (items.isEmpty()) {
                return Optional.empty();
            }
            OutboxItem item = items.getFirst();
            jdbc.update("""
                UPDATE identity_outbox
                SET status = 'CLAIMED', claimed_by = ?, claimed_until = ?, attempts = attempts + 1
                WHERE id = ?
                """, owner, timestamp(now.plusSeconds(30)), bytes(item.id()));
            return Optional.of(new OutboxItem(
                item.id(), item.aggregateId(), item.eventType(), item.payloadJson(), item.attempts() + 1));
        });
    }

    /**
     * 标记 Outbox 已被 Control 幂等接收。
     */
    public void markOutboxPublished(UUID outboxId, Instant now) {
        jdbc.update("""
            UPDATE identity_outbox
            SET status = 'PUBLISHED', claimed_by = NULL, claimed_until = NULL, published_at = ?
            WHERE id = ? AND status = 'CLAIMED'
            """, timestamp(now), bytes(outboxId));
    }

    /**
     * 以有界退避重试失败 Outbox，十次后进入可审计终态。
     */
    public void markOutboxFailed(UUID outboxId, int attempts, Instant now) {
        String status = attempts >= 10 ? "FAILED" : "PENDING";
        long delaySeconds = Math.min(60, 1L << Math.min(attempts, 6));
        jdbc.update("""
            UPDATE identity_outbox
            SET status = ?, available_at = ?, claimed_by = NULL, claimed_until = NULL
            WHERE id = ? AND status = 'CLAIMED'
            """, status, timestamp(now.plusSeconds(delaySeconds)), bytes(outboxId));
    }

    /**
     * 将内部账号转换为不含凭据的管理视图。
     */
    public AccountView view(Account account) {
        return new AccountView(
            account.id().toString(), account.username(), account.email(), account.displayName(),
            account.status().name(), account.passwordChangeRequired(), account.lockedUntil(),
            account.lastLoginAt(), account.authorities(), account.version());
    }

    /**
     * 从结果行和角色权限构造内部账号。
     */
    private Account account(ResultSet result) throws SQLException {
        UUID id = uuid(result.getBytes("id"));
        Timestamp login = result.getTimestamp("last_login_at");
        Timestamp locked = result.getTimestamp("locked_until");
        return new Account(
            id,
            result.getString("username"),
            result.getString("email"),
            result.getString("display_name"),
            AccountStatus.valueOf(result.getString("status")),
            result.getBoolean("password_change_required"),
            result.getLong("auth_version"),
            result.getLong("version"),
            login == null ? null : login.toInstant(),
            result.getString("password_hash"),
            result.getBoolean("temporary"),
            locked == null ? null : locked.toInstant(),
            authorities(id));
    }

    /**
     * 查询账号所有活动平台角色聚合权限。
     */
    private Set<String> authorities(UUID accountId) {
        return Set.copyOf(jdbc.queryForList("""
            SELECT rp.permission_key
            FROM identity_account_role ar
            JOIN identity_role r ON r.id = ar.role_id AND r.status = 'ACTIVE'
            JOIN identity_role_permission rp ON rp.role_id = r.id
            WHERE ar.account_id = ?
            ORDER BY rp.permission_key
            """, String.class, bytes(accountId)));
    }

    /**
     * 追加安全事件，不记录请求正文或凭据。
     */
    private void event(
        UUID accountId, String eventType, String result, String actor, String detailCode, Instant now) {
        jdbc.update("""
                INSERT INTO identity_security_event
                    (id, account_id, event_type, result, actor_subject, request_id,
                     remote_address_hash, user_agent_hash, detail_code, occurred_at)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
                """, bytes(GatewayIdentityIds.generate()), nullableBytes(accountId), eventType, result,
            actor, detailCode, timestamp(now));
    }

    /**
     * 在账号本地事务内追加只含非敏感身份投影的 Outbox。
     */
    private void outbox(
        UUID accountId, String eventType, String username, String email,
        String displayName, String status, Instant now) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "issuer", issuer,
                "subject", accountId.toString(),
                "username", username,
                "email", email == null ? "" : email,
                "displayName", displayName,
                "status", status));
            jdbc.update("""
                    INSERT INTO identity_outbox
                        (id, aggregate_id, event_type, payload_json, status, attempts,
                         available_at, claimed_by, claimed_until, published_at, created_at)
                    VALUES (?, ?, ?, CAST(? AS JSON), 'PENDING', 0, ?, NULL, NULL, NULL, ?)
                    """, bytes(GatewayIdentityIds.generate()), bytes(accountId), eventType, payload,
                timestamp(now), timestamp(now));
        } catch (Exception exception) {
            throw new IllegalStateException("identity outbox serialization failed", exception);
        }
    }

    /**
     * 规范化用户名并限制允许字符。
     */
    public static String normalizeUsername(String username) {
        String normalized = Objects.requireNonNull(username, "username must not be null")
            .strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("username must match the local identity policy");
        }
        return normalized;
    }

    /**
     * 规范化可空邮箱。
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalArgumentException("email is invalid");
        }
        return normalized;
    }

    /**
     * 将 UUID 写成 MySQL BINARY(16)。
     */
    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16)
            .putLong(value.getMostSignificantBits())
            .putLong(value.getLeastSignificantBits())
            .array();
    }

    /**
     * 允许可空账号 ID 写入安全事件。
     */
    private static byte[] nullableBytes(UUID value) {
        return value == null ? null : bytes(value);
    }

    /**
     * 从 MySQL BINARY(16) 读取 UUID。
     */
    private static UUID uuid(byte[] value) {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * 将 Instant 写成 UTC Timestamp。
     */
    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    /**
     * 允许可空时间写入锁定列。
     */
    private static Timestamp nullableTimestamp(Instant value) {
        return value == null ? null : timestamp(value);
    }
}
