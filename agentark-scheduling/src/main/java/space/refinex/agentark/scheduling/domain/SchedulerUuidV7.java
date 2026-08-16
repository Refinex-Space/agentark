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

package space.refinex.agentark.scheduling.domain;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * 为 Scheduler 内部 Attempt、Delivery、Dead Letter 与 Outbox 事实生成 UUIDv7。
 *
 * @author refinex
 */
public final class SchedulerUuidV7 {

    /**
     * 生产随机位使用的密码学安全随机源。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 禁止实例化 UUID 生成器。
     */
    private SchedulerUuidV7() {
    }

    /**
     * 使用给定时间和安全随机位生成 RFC 9562 UUIDv7。
     *
     * @param instant UUID 内嵌的 UTC 毫秒时间
     * @return UUIDv7
     */
    public static UUID generate(Instant instant) {
        long millis = instant.toEpochMilli();
        if (millis < 0 || millis > 0x0000FFFFFFFFFFFFL) {
            throw new IllegalArgumentException("instant is outside UUIDv7 range");
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        long most = 0;
        long least = 0;
        for (int index = 0; index < 8; index++) {
            most = (most << 8) | (bytes[index] & 0xFFL);
            least = (least << 8) | (bytes[index + 8] & 0xFFL);
        }
        return new UUID(most, least);
    }
}
