package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.RecordCheckFeedbackUseCase;
import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.FeedbackVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckFeedbackControllerTest {
    private static final UUID CHECK_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID COASTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private RecordingFeedbackUseCase feedback;
    private CheckFeedbackController controller;

    @BeforeEach
    void setUp() {
        feedback = new RecordingFeedbackUseCase();
        controller = new CheckFeedbackController(feedback);
    }

    @Test
    void recordsMatchAsApiFeedback() {
        var response = controller.record(
                CHECK_ID,
                new CheckFeedbackController.FeedbackRequest(FeedbackVerdict.MATCH, COASTER_ID)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(feedback.checkRequestId).isEqualTo(CHECK_ID);
        assertThat(feedback.coasterId).isEqualTo(COASTER_ID);
        assertThat(feedback.source).isEqualTo(FeedbackSource.API);
        assertThat(feedback.verdict).isEqualTo(FeedbackVerdict.MATCH);
    }

    @Test
    void rejectsMatchWithoutCoasterId() {
        assertThatThrownBy(() -> controller.record(
                CHECK_ID,
                new CheckFeedbackController.FeedbackRequest(FeedbackVerdict.MATCH, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("coasterId must be set");
    }

    @Test
    void recordsNoMatchWithOptionalAddedCoasterId() {
        controller.record(
                CHECK_ID,
                new CheckFeedbackController.FeedbackRequest(FeedbackVerdict.NO_MATCH, COASTER_ID)
        );

        assertThat(feedback.checkRequestId).isEqualTo(CHECK_ID);
        assertThat(feedback.coasterId).isEqualTo(COASTER_ID);
        assertThat(feedback.source).isEqualTo(FeedbackSource.API);
        assertThat(feedback.verdict).isEqualTo(FeedbackVerdict.NO_MATCH);
    }

    private static class RecordingFeedbackUseCase implements RecordCheckFeedbackUseCase {
        private UUID checkRequestId;
        private UUID coasterId;
        private FeedbackSource source;
        private FeedbackVerdict verdict;

        @Override
        public void confirmMatch(UUID checkRequestId, UUID coasterId, FeedbackSource source) {
            this.checkRequestId = checkRequestId;
            this.coasterId = coasterId;
            this.source = source;
            this.verdict = FeedbackVerdict.MATCH;
        }

        @Override
        public void confirmNoMatch(UUID checkRequestId, UUID addedCoasterId, FeedbackSource source) {
            this.checkRequestId = checkRequestId;
            this.coasterId = addedCoasterId;
            this.source = source;
            this.verdict = FeedbackVerdict.NO_MATCH;
        }
    }
}
