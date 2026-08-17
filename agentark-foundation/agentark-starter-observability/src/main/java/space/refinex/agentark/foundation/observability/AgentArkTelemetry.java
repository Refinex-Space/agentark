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

package space.refinex.agentark.foundation.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 通过统一白名单向 Micrometer 和 OpenTelemetry 记录低基数指标与安全 Span 属性。
 *
 * @author refinex
 */
public final class AgentArkTelemetry {

    /**
     * 创建无 Registry/SDK 的安全 No-op 实例，供纯单元测试和显式禁用配置使用。
     *
     * @return 仍执行参数校验但不导出 Telemetry 的实例
     */
    public static AgentArkTelemetry noop() {
        return new AgentArkTelemetry(
            Optional.empty(), Optional.empty(),
            new MetricTagPolicy(java.util.Set.of(
                "service", "environment", "provider", "model.family", "tool.family", "status",
                "job.type", "operation", "outcome", "error.category", "runtime.provider",
                "usage.type")));
    }

    /**
     * 可选 Micrometer Registry；不存在时不创建孤立的本地 Registry。
     */
    private final Optional<MeterRegistry> meterRegistry;

    /**
     * 可选 OpenTelemetry 实例；不存在时返回无效 Span。
     */
    private final Optional<OpenTelemetry> openTelemetry;

    /**
     * Metric 和 Span 属性共享的低基数白名单。
     */
    private final MetricTagPolicy tagPolicy;

    /**
     * 创建 AgentArk 可观测记录器。
     *
     * @param meterRegistry 可选 Micrometer Registry
     * @param openTelemetry 可选 OpenTelemetry 实例
     * @param tagPolicy     低基数标签白名单
     */
    public AgentArkTelemetry(
        Optional<MeterRegistry> meterRegistry,
        Optional<OpenTelemetry> openTelemetry,
        MetricTagPolicy tagPolicy) {
        this.meterRegistry =
            java.util.Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.openTelemetry =
            java.util.Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.tagPolicy = java.util.Objects.requireNonNull(tagPolicy, "tagPolicy must not be null");
    }

    /**
     * 将一次操作计入 Micrometer Counter；未配置 Registry 时安全跳过。
     *
     * @param metricName    以 {@code agentark.} 开头的稳定指标名
     * @param candidateTags 候选低基数标签
     * @throws IllegalArgumentException 当指标名不合法时抛出
     */
    public void increment(String metricName, Map<String, String> candidateTags) {
        if (metricName == null || !metricName.matches("agentark\\.[a-z][a-z0-9.]{0,94}")) {
            throw new IllegalArgumentException("metricName must use the agentark namespace");
        }
        Map<String, String> tags = tagPolicy.filter(candidateTags);
        meterRegistry.ifPresent(
            registry -> {
                try {
                var flattened = new ArrayList<String>(tags.size() * 2);
                tags.forEach(
                    (key, value) -> {
                        flattened.add(key);
                        flattened.add(value);
                    });
                Counter.builder(metricName)
                    .tags(flattened.toArray(String[]::new))
                    .register(registry)
                    .increment();
                } catch (RuntimeException ignored) {
                    // 指标后端故障不得阻断业务；非法指标名和标签已在进入 Registry 前拒绝。
                }
            });
    }

    /**
     * 创建具有稳定命名和安全低基数属性的 OpenTelemetry Span。
     *
     * @param convention          Span 类型约定
     * @param operation           稳定操作名
     * @param candidateAttributes 候选低基数属性
     * @return 已启动 Span；未配置 OpenTelemetry 时返回无效 Span
     */
    public Span startSpan(
        SpanConvention convention, String operation, Map<String, String> candidateAttributes) {
        Map<String, String> attributes = tagPolicy.filter(candidateAttributes);
        return openTelemetry
            .map(
                telemetry -> {
                    var builder =
                        telemetry
                            .getTracer("space.refinex.agentark", "0.1.0")
                            .spanBuilder(convention.spanName(operation));
                    attributes.forEach(builder::setAttribute);
                    return builder.startSpan();
                })
            .orElseGet(Span::getInvalid);
    }

    /**
     * 记录一次低基数 Timer；Registry 不存在或后端失败时安全跳过。
     *
     * @param metricName    以 {@code agentark.} 开头的稳定指标名
     * @param duration      非负耗时
     * @param candidateTags 候选低基数标签
     */
    public void recordDuration(
        String metricName, Duration duration, Map<String, String> candidateTags) {
        if (metricName == null || !metricName.matches("agentark\\.[a-z][a-z0-9.]{0,94}")) {
            throw new IllegalArgumentException("metricName must use the agentark namespace");
        }
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        Map<String, String> tags = tagPolicy.filter(candidateTags);
        meterRegistry.ifPresent(registry -> {
            try {
                var flattened = new ArrayList<String>(tags.size() * 2);
                tags.forEach((key, value) -> {
                    flattened.add(key);
                    flattened.add(value);
                });
                io.micrometer.core.instrument.Timer.builder(metricName)
                    .tags(flattened.toArray(String[]::new))
                    .register(registry)
                    .record(duration);
            } catch (RuntimeException ignored) {
                // Metric Exporter 故障不得改变业务结果。
            }
        });
    }

    /**
     * 在当前上下文创建并结束 Span，记录异常类型但不采集异常消息或业务正文。
     *
     * @param convention          Span 命名约定
     * @param operation           稳定操作名
     * @param candidateAttributes 候选低基数属性
     * @param action              受追踪业务动作
     * @param <T>                 返回值类型
     * @return 业务动作结果
     */
    public <T> T inSpan(
        SpanConvention convention,
        String operation,
        Map<String, String> candidateAttributes,
        Supplier<T> action) {
        java.util.Objects.requireNonNull(action, "action must not be null");
        long startedAt = System.nanoTime();
        Span span;
        try {
            span = startSpan(convention, operation, candidateAttributes);
        } catch (RuntimeException ignored) {
            try {
                T result = action.get();
                recordOperationDuration(
                    convention, operation, candidateAttributes, "succeeded", startedAt);
                return result;
            } catch (RuntimeException exception) {
                recordOperationDuration(
                    convention, operation, candidateAttributes, "failed", startedAt);
                throw exception;
            }
        }
        try (Scope scope = span.makeCurrent()) {
            T result = action.get();
            span.setStatus(StatusCode.OK);
            recordOperationDuration(
                convention, operation, candidateAttributes, "succeeded", startedAt);
            return result;
        } catch (RuntimeException exception) {
            span.setAttribute("error.category", exception.getClass().getSimpleName());
            span.setStatus(StatusCode.ERROR);
            recordOperationDuration(
                convention, operation, candidateAttributes, "failed", startedAt);
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 将 Span 生命周期同步投影为低基数 Micrometer Timer，Exporter 故障不影响业务。
     *
     * @param convention          Span 命名约定
     * @param operation           稳定操作名
     * @param candidateAttributes 候选低基数属性
     * @param outcome             succeeded 或 failed
     * @param startedAt           {@link System#nanoTime()} 起点
     */
    private void recordOperationDuration(
        SpanConvention convention,
        String operation,
        Map<String, String> candidateAttributes,
        String outcome,
        long startedAt) {
        Map<String, String> tags = new HashMap<>(candidateAttributes);
        tags.put("outcome", outcome);
        long elapsed = Math.max(0L, System.nanoTime() - startedAt);
        recordDuration(
            "agentark." + convention.spanName(operation) + ".duration",
            Duration.ofNanos(elapsed), tags);
    }

    /**
     * 返回当前有效 W3C Trace ID，供 Runtime Event/Audit 关联；无有效 Span 时为空。
     *
     * @return 当前 Trace ID
     */
    public Optional<String> currentTraceId() {
        var context = Span.current().getSpanContext();
        return context.isValid() ? Optional.of(context.getTraceId()) : Optional.empty();
    }
}
