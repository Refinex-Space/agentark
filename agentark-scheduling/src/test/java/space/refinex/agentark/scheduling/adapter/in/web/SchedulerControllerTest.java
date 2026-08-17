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

package space.refinex.agentark.scheduling.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.scheduling.application.SchedulerApplicationService;
import space.refinex.agentark.scheduling.application.SchedulerAuthorizationService;
import space.refinex.agentark.scheduling.application.WebhookIngressService;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService;
import space.refinex.agentark.scheduling.domain.SchedulerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 Scheduler Web 层在读取和验签前实施入站正文上限。
 *
 * @author refinex
 */
class SchedulerControllerTest {

    /** 创建 Controller 测试实例。 */
    SchedulerControllerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明声明超过 1 MiB 的 Webhook 不会进入领域接单服务。 */
    @Test
    void rejectsOversizedWebhookBeforeIngress() {
        SchedulerApplicationService service = mock(SchedulerApplicationService.class);
        SchedulerAuthorizationService authorization = mock(SchedulerAuthorizationService.class);
        WebhookIngressService webhook = mock(WebhookIngressService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(1_048_577L);
        SchedulerController controller = new SchedulerController(
            service, authorization, webhook, mock(TriggerDefinitionService.class));

        assertThatThrownBy(() -> controller.webhook(
            "01990f72-9e84-7000-8000-000000000001", "1786874400",
            "nonce-0123456789", "v1=" + "0".repeat(64), request))
            .isInstanceOfSatisfying(
                SchedulerException.class,
                exception -> assertThat(exception.code())
                    .isEqualTo("WEBHOOK_PAYLOAD_TOO_LARGE"));
        verifyNoInteractions(webhook);
    }
}
