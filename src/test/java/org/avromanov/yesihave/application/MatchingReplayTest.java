package org.avromanov.yesihave.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.avromanov.yesihave.application.model.MatchDecision.*;

class MatchingReplayTest {
    @Test
    void confirmedRepeatsAreReviewedAndNewCoastersAreNeverAutomaticallyMatched() throws Exception {
        // Calibration regression set, not an independent estimate of generalization.
        // Scores replay both orientations against the collection as it existed at each check.
        var properties = new MatchingProperties(0.90, 0.86, 0.80, 0.75, 0.05, 20, 3);
        int repeats = 0;
        int newCoasters = 0;
        int automaticMatches = 0;
        int extraReviews = 0;
        try (var input = getClass().getResourceAsStream("/matching/confirmed-scores-20261024.json")) {
            assertThat(input).isNotNull();
            var rows = new ObjectMapper().readTree(input);
            for (var row : rows) {
                var candidates = new ArrayList<CandidateDto>();
                int rank = 1;
                for (var scores : row.get("scores")) {
                    double front = scores.get(0).asDouble();
                    double back = scores.get(1).asDouble();
                    candidates.add(new CandidateDto(new UUID(0, rank), "Candidate", front, back,
                            (front + back) / 2, rank++));
                }
                var decision = MatchClassifier.classify(candidates, properties);
                if (row.get("actual").asText().equals("MATCH")) {
                    repeats++;
                    assertThat(row.get("correctTopCandidate").asBoolean()).isTrue();
                    assertThat(decision).as("confirmed repeat %s", repeats).isNotEqualTo(NO_MATCH);
                    if (decision == MATCH) {
                        automaticMatches++;
                    }
                } else {
                    newCoasters++;
                    assertThat(decision).as("new coaster %s", newCoasters).isNotEqualTo(MATCH);
                    if (decision == UNCERTAIN) {
                        extraReviews++;
                    }
                }
            }
        }
        assertThat(repeats).isEqualTo(23);
        assertThat(newCoasters).isEqualTo(60);
        assertThat(automaticMatches).isGreaterThanOrEqualTo(6);
        assertThat(extraReviews).isLessThanOrEqualTo(4);
    }
}
