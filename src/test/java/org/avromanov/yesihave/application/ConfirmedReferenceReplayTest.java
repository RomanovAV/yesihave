package org.avromanov.yesihave.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmedReferenceReplayTest {
    @Test
    void chronologicallyAvailableReferencesImproveRecognitionWithoutNewFalseMatches() throws Exception {
        var properties = new MatchingProperties(.90, .86, .80, .75, .05, 20, 3);
        int repeats = 0, newCoasters = 0, automatic = 0, missed = 0, reviews = 0;
        try (var input = getClass().getResourceAsStream("/matching/reference-replay-20261024.json")) {
            assertThat(input).isNotNull();
            for (var row : new ObjectMapper().readTree(input)) {
                var candidates = new ArrayList<CandidateDto>();
                int rank = 1;
                for (var scores : row.get("references")) {
                    double front = scores.get(0).asDouble(), back = scores.get(1).asDouble();
                    candidates.add(new CandidateDto(new UUID(0, rank), "Candidate", front, back,
                            (front + back) / 2, rank++));
                }
                var decision = MatchClassifier.classify(candidates, properties);
                if (row.get("actual").asText().equals("MATCH")) {
                    repeats++;
                    if (decision == MatchDecision.MATCH) automatic++;
                    if (decision == MatchDecision.NO_MATCH) missed++;
                } else {
                    newCoasters++;
                    assertThat(decision).isNotEqualTo(MatchDecision.MATCH);
                    if (decision == MatchDecision.UNCERTAIN) reviews++;
                }
            }
        }
        assertThat(repeats).isEqualTo(59);
        assertThat(newCoasters).isEqualTo(104);
        assertThat(automatic).isGreaterThanOrEqualTo(12);
        assertThat(missed).isLessThanOrEqualTo(1);
        assertThat(reviews).isLessThanOrEqualTo(11);
    }
}
