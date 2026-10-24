package org.avromanov.yesihave.persistence;

import java.util.UUID;

public record MatchedCandidateRow(
        UUID coasterId,
        String name,
        double scoreFront,
        double scoreBack,
        UUID referenceCheckRequestId
) {
    public MatchedCandidateRow(UUID coasterId, String name, double scoreFront, double scoreBack) {
        this(coasterId, name, scoreFront, scoreBack, null);
    }

    public static MatchedCandidateRow bestOrientation(UUID coasterId, String name,
                                                      double frontToFront, double backToBack,
                                                      double frontToBack, double backToFront) {
        return bestOrientation(coasterId, name, frontToFront, backToBack, frontToBack, backToFront, null);
    }

    public static MatchedCandidateRow bestOrientation(UUID coasterId, String name,
                                                      double frontToFront, double backToBack,
                                                      double frontToBack, double backToFront,
                                                      UUID referenceCheckRequestId) {
        // Choose one consistent pairing. Taking each side's maximum independently
        // could match both query photos against the same stored side.
        if (frontToBack + backToFront > frontToFront + backToBack) {
            return new MatchedCandidateRow(coasterId, name, frontToBack, backToFront, referenceCheckRequestId);
        }
        return new MatchedCandidateRow(coasterId, name, frontToFront, backToBack, referenceCheckRequestId);
    }
}
