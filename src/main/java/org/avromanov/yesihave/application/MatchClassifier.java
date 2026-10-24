package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;

import java.util.List;

/** Classifies candidates sorted by descending pair score. */
public final class MatchClassifier {
    private MatchClassifier() {
    }

    public static MatchDecision classify(List<CandidateDto> candidates, MatchingProperties properties) {
        if (candidates.isEmpty()) {
            return MatchDecision.NO_MATCH;
        }
        CandidateDto best = candidates.getFirst();
        double minSide = Math.min(best.scoreFront(), best.scoreBack());
        if (best.pairScore() >= properties.matchPairThreshold()
                && minSide >= properties.matchMinSideThreshold()) {
            return MatchDecision.MATCH;
        }
        if (best.pairScore() >= properties.uncertainPairThreshold()) {
            return MatchDecision.UNCERTAIN;
        }
        // A clear leader with moderate similarity merits human review, not an
        // automatic match. No runner-up means there is no measured margin.
        if (candidates.size() >= 2
                && best.pairScore() >= properties.uncertainLeaderPairThreshold()
                && best.pairScore() - candidates.get(1).pairScore() >= properties.uncertainLeaderMargin()) {
            return MatchDecision.UNCERTAIN;
        }
        return MatchDecision.NO_MATCH;
    }
}
