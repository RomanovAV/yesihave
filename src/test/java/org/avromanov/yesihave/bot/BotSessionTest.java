package org.avromanov.yesihave.bot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotSessionTest {

    @Test
    void feedbackStateReleasesPhotoBuffers() {
        BotSession session = new BotSession();
        session.setFrontImage(new byte[]{1});
        session.setBackImage(new byte[]{2});
        UUID checkId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        session.prepareFeedback(checkId, Set.of(candidateId));

        assertThat(session.state()).isEqualTo(TelegramSessionState.WAIT_FEEDBACK);
        assertThat(session.frontImage()).isNull();
        assertThat(session.backImage()).isNull();
        assertThat(session.checkRequestId()).isEqualTo(checkId);
        assertThat(session.candidateIds()).containsExactly(candidateId);
    }

    @Test
    void reportsIdleSessionAsExpired() {
        BotSession session = new BotSession();

        assertThat(session.isExpired(Instant.now().plusSeconds(60), Duration.ofSeconds(30))).isTrue();
    }
}
