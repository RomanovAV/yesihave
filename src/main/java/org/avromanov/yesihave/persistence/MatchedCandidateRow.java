package org.avromanov.yesihave.persistence;

import java.util.UUID;

public record MatchedCandidateRow(
        UUID coasterId,
        String name,
        double scoreFront,
        double scoreBack
) {
}
