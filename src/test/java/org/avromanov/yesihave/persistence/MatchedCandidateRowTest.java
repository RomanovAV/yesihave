package org.avromanov.yesihave.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchedCandidateRowTest {
    private final UUID id = UUID.randomUUID();

    @Test
    void recognizesPhotosSubmittedInReverseOrder() {
        var candidate = MatchedCandidateRow.bestOrientation(id, "Two-sided coaster",
                0.69, 0.60, 0.96, 0.94);

        assertThat(candidate).isEqualTo(new MatchedCandidateRow(id, "Two-sided coaster", 0.96, 0.94));
    }

    @Test
    void preservesDirectPairingWhenItIsBetterOrTied() {
        assertThat(MatchedCandidateRow.bestOrientation(id, "Direct", 0.94, 0.96, 0.69, 0.60))
                .isEqualTo(new MatchedCandidateRow(id, "Direct", 0.94, 0.96));
        assertThat(MatchedCandidateRow.bestOrientation(id, "Tie", 0.9, 0.8, 0.8, 0.9))
                .isEqualTo(new MatchedCandidateRow(id, "Tie", 0.9, 0.8));
    }

    @Test
    void cannotUseOneStoredSideForBothQueryPhotos() {
        // Both query photos resemble the stored front; the stored back differs.
        var candidate = MatchedCandidateRow.bestOrientation(id, "Different back",
                0.98, 0.40, 0.35, 0.97);

        assertThat(candidate.scoreFront()).isEqualTo(0.98);
        assertThat(candidate.scoreBack()).isEqualTo(0.40);
    }
}
