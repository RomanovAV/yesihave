package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.persistence.CheckAuditRepository;
import org.avromanov.yesihave.persistence.MatchedCandidateRow;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultCheckPairUseCase implements CheckPairUseCase {
    private static final double MATCH_PAIR_THRESHOLD = 0.90;
    private static final double MATCH_MIN_SIDE_THRESHOLD = 0.86;
    private static final double UNCERTAIN_PAIR_THRESHOLD = 0.84;
    private static final int TOP_K_SIDE = 20;
    private static final int TOP_K_RESPONSE = 3;

    private final EmbeddingService embeddingService;
    private final MatchingRepository matchingRepository;
    private final CheckAuditRepository checkAuditRepository;
    private final ObjectStorageService objectStorageService;

    public DefaultCheckPairUseCase(EmbeddingService embeddingService,
                                   MatchingRepository matchingRepository,
                                   CheckAuditRepository checkAuditRepository,
                                   ObjectStorageService objectStorageService) {
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.checkAuditRepository = checkAuditRepository;
        this.objectStorageService = objectStorageService;
    }

    @Override
    @Transactional
    public MatchResultDto check(long telegramUserId, byte[] frontImage, byte[] backImage) {
        float[] frontEmbedding = embeddingService.toEmbedding(frontImage);
        float[] backEmbedding = embeddingService.toEmbedding(backImage);

        List<MatchedCandidateRow> scoredRows = fetchScoredCandidates(frontEmbedding, backEmbedding);
        List<CandidateDto> rankedCandidates = rankCandidates(scoredRows);

        CandidateDto best = rankedCandidates.isEmpty() ? null : rankedCandidates.getFirst();
        MatchDecision decision = classify(best);

        String frontImageKey = objectStorageService.putImage("checks/front", frontImage);
        String backImageKey = objectStorageService.putImage("checks/back", backImage);

        var matchedCoasterId = decision == MatchDecision.MATCH && best != null ? best.coasterId() : null;
        var requestId = checkAuditRepository.insertCheckRequest(
                telegramUserId,
                frontImageKey,
                backImageKey,
                best == null ? null : best.scoreFront(),
                best == null ? null : best.scoreBack(),
                best == null ? null : best.pairScore(),
                decision,
                matchedCoasterId
        );
        checkAuditRepository.insertCandidates(requestId, rankedCandidates);

        return new MatchResultDto(
                decision,
                best == null ? null : best.scoreFront(),
                best == null ? null : best.scoreBack(),
                best == null ? null : best.pairScore(),
                matchedCoasterId,
                rankedCandidates
        );
    }

    private List<MatchedCandidateRow> fetchScoredCandidates(float[] frontEmbedding, float[] backEmbedding) {
        List<java.util.UUID> frontIds = matchingRepository.findTopFrontIds(frontEmbedding, TOP_K_SIDE);
        List<java.util.UUID> backIds = matchingRepository.findTopBackIds(backEmbedding, TOP_K_SIDE);

        Set<java.util.UUID> merged = new LinkedHashSet<>(frontIds);
        merged.addAll(backIds);

        return matchingRepository.scoreCandidates(new ArrayList<>(merged), frontEmbedding, backEmbedding);
    }

    private List<CandidateDto> rankCandidates(List<MatchedCandidateRow> scoredRows) {
        List<MatchedCandidateRow> sorted = scoredRows.stream()
                .sorted(Comparator.comparingDouble(this::pairScore).reversed())
                .limit(TOP_K_RESPONSE)
                .toList();

        List<CandidateDto> result = new ArrayList<>(sorted.size());
        int rank = 1;
        for (MatchedCandidateRow row : sorted) {
            result.add(new CandidateDto(
                    row.coasterId(),
                    row.name(),
                    row.scoreFront(),
                    row.scoreBack(),
                    pairScore(row),
                    rank++
            ));
        }
        return result;
    }

    private MatchDecision classify(CandidateDto best) {
        if (best == null) {
            return MatchDecision.NO_MATCH;
        }
        double minSide = Math.min(best.scoreFront(), best.scoreBack());
        if (best.pairScore() >= MATCH_PAIR_THRESHOLD && minSide >= MATCH_MIN_SIDE_THRESHOLD) {
            return MatchDecision.MATCH;
        }
        if (best.pairScore() >= UNCERTAIN_PAIR_THRESHOLD) {
            return MatchDecision.UNCERTAIN;
        }
        return MatchDecision.NO_MATCH;
    }

    private double pairScore(MatchedCandidateRow row) {
        return (row.scoreFront() + row.scoreBack()) / 2.0;
    }
}
