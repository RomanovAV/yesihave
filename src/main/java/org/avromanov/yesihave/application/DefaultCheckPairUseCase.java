package org.avromanov.yesihave.application;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImagePairFingerprint;
import org.avromanov.yesihave.persistence.CheckAuditRepository;
import org.avromanov.yesihave.persistence.CheckEmbeddingRepository;
import org.avromanov.yesihave.persistence.MatchedCandidateRow;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageWriteBatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DefaultCheckPairUseCase implements CheckPairUseCase {
    private final EmbeddingService embeddingService;
    private final MatchingRepository matchingRepository;
    private final CheckAuditRepository checkAuditRepository;
    private final CheckEmbeddingRepository checkEmbeddingRepository;
    private final ObjectStorageWriteBatch objectStorageWriteBatch;
    private final MatchingProperties matchingProperties;
    private final EmbeddingProperties embeddingProperties;
    private final ImageBytesNormalizer imageBytesNormalizer;
    private final TransactionTemplate transactionTemplate;

    public DefaultCheckPairUseCase(EmbeddingService embeddingService,
                                   MatchingRepository matchingRepository,
                                   CheckAuditRepository checkAuditRepository,
                                   CheckEmbeddingRepository checkEmbeddingRepository,
                                   ObjectStorageWriteBatch objectStorageWriteBatch,
                                   MatchingProperties matchingProperties,
                                   EmbeddingProperties embeddingProperties,
                                   ImageBytesNormalizer imageBytesNormalizer,
                                   PlatformTransactionManager transactionManager) {
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.checkAuditRepository = checkAuditRepository;
        this.checkEmbeddingRepository = checkEmbeddingRepository;
        this.objectStorageWriteBatch = objectStorageWriteBatch;
        this.matchingProperties = matchingProperties;
        this.embeddingProperties = embeddingProperties;
        this.imageBytesNormalizer = imageBytesNormalizer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public MatchResultDto check(long telegramUserId, byte[] frontImage, byte[] backImage) {
        frontImage = normalize(frontImage);
        backImage = normalize(backImage);

        float[] frontEmbedding = embeddingService.toEmbedding(frontImage);
        float[] backEmbedding = embeddingService.toEmbedding(backImage);

        List<MatchedCandidateRow> scoredRows = fetchScoredCandidates(frontEmbedding, backEmbedding);
        List<CandidateDto> rankedCandidates = rankCandidates(scoredRows);

        CandidateDto best = rankedCandidates.isEmpty() ? null : rankedCandidates.getFirst();
        MatchDecision decision = MatchClassifier.classify(rankedCandidates, matchingProperties);

        var matchedCoasterId = decision == MatchDecision.MATCH && best != null ? best.coasterId() : null;
        byte[] normalizedFront = frontImage;
        byte[] normalizedBack = backImage;
        return objectStorageWriteBatch.execute(uploads -> {
            String frontImageKey = uploads.putImage("checks/front", normalizedFront);
            String backImageKey = uploads.putImage("checks/back", normalizedBack);

            MatchResultDto result = transactionTemplate.execute(status -> {
                uploads.registerRollbackCleanup();
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
                checkEmbeddingRepository.upsert(requestId, embeddingProperties.modelVersion(),
                        ImagePairFingerprint.of(normalizedFront, normalizedBack), frontEmbedding, backEmbedding);

                return new MatchResultDto(
                        decision,
                        best == null ? null : best.scoreFront(),
                        best == null ? null : best.scoreBack(),
                        best == null ? null : best.pairScore(),
                        matchedCoasterId,
                        rankedCandidates,
                        requestId
                );
            });
            return Objects.requireNonNull(result, "Check transaction returned no result");
        });
    }

    private byte[] normalize(byte[] image) {
        try {
            return imageBytesNormalizer.normalize(image);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to normalize image", e);
        }
    }

    private List<MatchedCandidateRow> fetchScoredCandidates(float[] frontEmbedding, float[] backEmbedding) {
        String modelVersion = embeddingProperties.modelVersion();
        List<java.util.UUID> frontIds = matchingRepository.findTopFrontIds(
                modelVersion,
                frontEmbedding,
                matchingProperties.topKSide()
        );
        List<java.util.UUID> backIds = matchingRepository.findTopBackIds(
                modelVersion,
                backEmbedding,
                matchingProperties.topKSide()
        );

        Set<java.util.UUID> merged = new LinkedHashSet<>(frontIds);
        merged.addAll(backIds);
        // A user may photograph the two sides in either order. Retrieve candidates
        // for both orientations before scoring, including those absent from the
        // original front/front and back/back shortlists.
        merged.addAll(matchingRepository.findTopBackIds(
                modelVersion, frontEmbedding, matchingProperties.topKSide()));
        merged.addAll(matchingRepository.findTopFrontIds(
                modelVersion, backEmbedding, matchingProperties.topKSide()));

        return matchingRepository.scoreCandidates(
                modelVersion,
                new ArrayList<>(merged),
                frontEmbedding,
                backEmbedding
        );
    }

    private List<CandidateDto> rankCandidates(List<MatchedCandidateRow> scoredRows) {
        List<MatchedCandidateRow> sorted = scoredRows.stream()
                .sorted(Comparator.comparingDouble(this::pairScore).reversed())
                .limit(matchingProperties.topKResponse())
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
                    rank++,
                    row.referenceCheckRequestId()
            ));
        }
        return result;
    }

    private double pairScore(MatchedCandidateRow row) {
        return (row.scoreFront() + row.scoreBack()) / 2.0;
    }
}
