package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DefaultCheckPairUseCase implements CheckPairUseCase {
    @Override
    public MatchResultDto check(byte[] frontImage, byte[] backImage) {
        CandidateDto first = new CandidateDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Stub Coaster",
                0.91,
                0.88,
                0.895,
                1
        );

        return new MatchResultDto(
                MatchDecision.UNCERTAIN,
                first.scoreFront(),
                first.scoreBack(),
                first.pairScore(),
                null,
                List.of(first)
        );
    }
}
