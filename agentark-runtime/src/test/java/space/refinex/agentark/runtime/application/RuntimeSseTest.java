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

package space.refinex.agentark.runtime.application;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import space.refinex.agentark.kernel.id.EventId;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

import java.time.Duration;

/**
 * 验证 SSE 以持久 Event Log 回放、使用 Session Sequence 追平且断线不影响 Run。
 *
 * @author refinex
 */
class RuntimeSseTest {

    /**
     * 证明 Last-Event-ID 之后的事实先回放，并可由提交后提示低延迟追平。
     */
    @Test
    void replaysAfterCursorAndFollowsCommittedNotification() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Session session = fixture.createSession("sse-session");
        Turn turn = fixture.acceptTurn(session, "sse-turn");
        Run run = fixture.store.findRun(turn.currentRunId().orElseThrow()).orElseThrow();
        RuntimeEvent second = fixture.store.append(
            EventId.generate(), fixture.organizationId, fixture.projectId, session.id(),
            turn.id(), run.id(), "run.observed", 1,
            run.id().asString().replace("-", ""), RuntimePayload.inline("{}"),
            fixture.clock.instant(), run.fencingToken());
        RuntimeEventStreamService stream = new RuntimeEventStreamService(
            fixture.store, fixture.notifier);

        StepVerifier.create(stream.stream(run.id(), 1).take(2))
            .expectNextMatches(event -> event.id().equals(second.id())
                && event.sessionSequence() == 2)
            .then(() -> {
                RuntimeEvent third = fixture.store.append(
                    EventId.generate(), fixture.organizationId, fixture.projectId,
                    session.id(), turn.id(), run.id(), "run.progressed", 1,
                    run.id().asString().replace("-", ""), RuntimePayload.inline("{}"),
                    fixture.clock.instant(), run.fencingToken());
                fixture.notifier.publish(session.id(), third.sessionSequence());
            })
            .expectNextMatches(event -> event.sessionSequence() == 3)
            .expectComplete()
            .verify(Duration.ofSeconds(3));

        org.assertj.core.api.Assertions.assertThat(
            fixture.store.findRun(run.id()).orElseThrow().status())
            .isEqualTo(RunStatus.CREATED);
    }
}
