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

package space.refinex.agentark.scheduling.adapter.out.webhook;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证外发 Webhook 响应摘要的流式读取上限，不把 Provider 正文载入内存。
 *
 * @author refinex
 */
class JdkOutboundWebhookClientTest {

    /** 创建 Webhook Client 测试实例。 */
    JdkOutboundWebhookClientTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明超过 4096 字节的响应只读取判定溢出所需的一个额外字节。 */
    @Test
    void boundsResponseBodyRead() {
        TrackingInputStream body = new TrackingInputStream(new byte[16_384]);

        String summary = JdkOutboundWebhookClient.boundedResponseSummary(200, body);

        assertThat(summary).isEqualTo("http-status:200;body-bytes:>4096");
        assertThat(body.bytesRead()).isEqualTo(4097);
        assertThat(body.closed()).isTrue();
    }

    /**
     * 记录实际读取和关闭行为的内存流。
     *
     * @author refinex
     */
    private static final class TrackingInputStream extends ByteArrayInputStream {

        /** 已读取字节数。 */
        private int bytesRead;

        /** 是否已经关闭。 */
        private boolean closed;

        /**
         * 创建跟踪输入流。
         *
         * @param bytes 响应字节
         */
        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        /** 记录单字节读取。 */
        @Override
        public synchronized int read() {
            int value = super.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        /** 记录批量读取。 */
        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        /** 记录关闭状态。 */
        @Override
        public void close() {
            closed = true;
        }

        /**
         * 返回已读取字节数。
         *
         * @return 已读取字节数
         */
        private int bytesRead() {
            return bytesRead;
        }

        /**
         * 返回关闭状态。
         *
         * @return 已关闭时为 true
         */
        private boolean closed() {
            return closed;
        }
    }
}
