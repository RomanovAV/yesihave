package org.avromanov.yesihave.application.model;

import java.util.UUID;

public record CandidateDto(
        UUID coasterId,
        String name,
        double scoreFront,
        double scoreBack,
        double pairScore,
        int rank
) {
}
