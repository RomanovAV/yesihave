package org.avromanov.yesihave.application.model;

import java.util.List;
import java.util.UUID;

public record MatchResultDto(
        MatchDecision decision,
        Double scoreFront,
        Double scoreBack,
        Double pairScore,
        UUID matchedCoasterId,
        List<CandidateDto> topCandidates
) {
}
