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

package space.refinex.agentark.foundation.web;

import java.util.Optional;

/**
 * 在同步请求线程内按作用域保存请求上下文，关闭作用域后保证清理 ThreadLocal。
 *
 * @author refinex
 */
public final class RequestContextAccessor {

    /**
     * 当前线程的请求上下文，不允许跨线程隐式传播。
     */
    private final ThreadLocal<RequestContext> current = new ThreadLocal<>();

    /**
     * 创建一个初始为空的请求上下文访问器。
     */
    public RequestContextAccessor() {
        // 显式构造器用于说明该对象没有共享全局状态。
    }

    /**
     * 返回当前线程绑定的请求上下文。
     *
     * @return 未进入请求作用域时为空
     */
    public Optional<RequestContext> current() {
        return Optional.ofNullable(current.get());
    }

    /**
     * 将上下文绑定到当前线程，并返回必须关闭的清理句柄。
     *
     * @param context 待绑定的请求上下文
     * @return 关闭时恢复先前上下文的句柄
     * @throws NullPointerException 当上下文为 {@code null} 时抛出
     */
    public Scope open(RequestContext context) {
        RequestContext previous = current.get();
        current.set(java.util.Objects.requireNonNull(context, "context must not be null"));
        return () -> {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        };
    }

    /**
     * 表示请求上下文绑定的可关闭作用域。
     *
     * @author refinex
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * 关闭作用域并恢复进入前的线程上下文，不抛出受检异常。
         */
        @Override
        void close();
    }
}
