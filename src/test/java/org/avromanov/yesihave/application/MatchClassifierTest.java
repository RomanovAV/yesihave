package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.avromanov.yesihave.application.model.MatchDecision.*;

class MatchClassifierTest {
    private final MatchingProperties properties = new MatchingProperties(0.90, 0.86, 0.80, 0.75, 0.05, 20, 3);

    @Test
    void asksForConfirmationOfModerateButDistinctLeader() {
        assertThat(MatchClassifier.classify(List.of(candidate(0.779, 0.769), candidate(0.669, 0.669)), properties))
                .isEqualTo(UNCERTAIN);
    }

    @Test
    void highSimilarityOrMarginAloneCannotProduceAutomaticMatch() {
        assertThat(MatchClassifier.classify(List.of(candidate(0.99, 0.83), candidate(0.50, 0.50)), properties))
                .isEqualTo(UNCERTAIN);
        assertThat(MatchClassifier.classify(List.of(candidate(0.80, 0.79), candidate(0.60, 0.60)), properties))
                .isEqualTo(UNCERTAIN);
        assertThat(MatchClassifier.classify(List.of(candidate(0.91, 0.89)), properties)).isEqualTo(MATCH);
    }

    @Test
    void retainsNoMatchForWeakOrIndistinctCandidates() {
        assertThat(MatchClassifier.classify(List.of(candidate(0.70, 0.60), candidate(0.30, 0.30)), properties))
                .isEqualTo(NO_MATCH);
        assertThat(MatchClassifier.classify(List.of(candidate(0.79, 0.79), candidate(0.77, 0.77)), properties))
                .isEqualTo(NO_MATCH);
        assertThat(MatchClassifier.classify(List.of(), properties)).isEqualTo(NO_MATCH);
    }

    @Test
    void doesNotInventMarginWhenThereIsOnlyOneCandidate() {
        assertThat(MatchClassifier.classify(List.of(candidate(0.78, 0.78)), properties)).isEqualTo(NO_MATCH);
        assertThat(MatchClassifier.classify(List.of(candidate(0.81, 0.81)), properties)).isEqualTo(UNCERTAIN);
    }

    private CandidateDto candidate(double front, double back) {
        return new CandidateDto(UUID.randomUUID(), "Candidate", front, back, (front + back) / 2, 1);
    }
}
