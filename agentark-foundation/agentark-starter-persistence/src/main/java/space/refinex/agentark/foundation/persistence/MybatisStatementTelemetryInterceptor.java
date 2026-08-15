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

package space.refinex.agentark.foundation.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统计 MyBatis Mapper 语句耗时并输出脱敏慢查询告警，始终拒绝记录 SQL 正文和参数对象。
 *
 * @author refinex
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}),
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
        type = Executor.class,
        method = "query",
        args = {
            MappedStatement.class,
            Object.class,
            RowBounds.class,
            ResultHandler.class,
            CacheKey.class,
            BoundSql.class
        })
})
public final class MybatisStatementTelemetryInterceptor implements Interceptor {

    /**
     * 持久化遥测日志记录器。
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(MybatisStatementTelemetryInterceptor.class);

    /**
     * Mapper 语句标识允许使用的安全字符和最大长度。
     */
    private static final String SAFE_STATEMENT_ID = "[A-Za-z0-9_.$]{1,240}";

    /**
     * 触发慢查询告警的纳秒阈值。
     */
    private final long slowQueryThresholdNanos;

    /**
     * 可选的指标注册表；未装配 Observability 时只保留脱敏慢查询告警。
     */
    private final Optional<MeterRegistry> meterRegistry;

    /**
     * 创建脱敏 MyBatis 语句遥测插件。
     *
     * @param slowQueryThreshold 非空且非负的慢查询阈值
     * @param meterRegistry      可选 Micrometer 指标注册表
     */
    public MybatisStatementTelemetryInterceptor(
        Duration slowQueryThreshold, Optional<MeterRegistry> meterRegistry) {
        Objects.requireNonNull(slowQueryThreshold, "slowQueryThreshold must not be null");
        if (slowQueryThreshold.isNegative()) {
            throw new IllegalArgumentException("slowQueryThreshold must not be negative");
        }
        this.slowQueryThresholdNanos = slowQueryThreshold.toNanos();
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    /**
     * 计时执行 Mapper 语句；日志和指标只包含受控语句类别、结果与耗时。
     *
     * @param invocation MyBatis 调用上下文
     * @return 被拦截语句的原始返回值
     * @throws Throwable 被拦截语句抛出的原始异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return invocation.proceed();
        } catch (Throwable error) {
            outcome = "error";
            throw error;
        } finally {
            long durationNanos = System.nanoTime() - startedAt;
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            String operation = statement.getSqlCommandType().name().toLowerCase(Locale.ROOT);
            recordMetric(operation, outcome, durationNanos);
            if (durationNanos >= slowQueryThresholdNanos) {
                LOGGER.warn(
                    "Slow MyBatis statement: statementId={}, operation={}, outcome={}, durationMs={}",
                    safeStatementId(statement.getId()),
                    operation,
                    outcome,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos));
            }
        }
    }

    /**
     * 记录低基数语句耗时指标，不使用 Mapper 标识、表名或租户作为标签。
     *
     * @param operation     受控 SQL 操作类别
     * @param outcome       success 或 error
     * @param durationNanos 执行时长，单位纳秒
     */
    private void recordMetric(String operation, String outcome, long durationNanos) {
        meterRegistry.ifPresent(
            registry ->
                Timer.builder("agentark.persistence.statement.duration")
                    .description("AgentArk 持久化语句执行耗时，不包含 SQL 或参数正文")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS));
    }

    /**
     * 将 Mapper 语句标识限制为代码生成的安全字符，拒绝把动态内容带入日志。
     *
     * @param statementId MyBatis Mapper 语句标识
     * @return 安全标识；非法时返回固定占位符
     */
    static String safeStatementId(String statementId) {
        if (statementId == null || !statementId.matches(SAFE_STATEMENT_ID)) {
            return "unknown";
        }
        return statementId;
    }
}
