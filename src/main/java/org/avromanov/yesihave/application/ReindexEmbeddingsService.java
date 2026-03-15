package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReindexEmbeddingsService {
    private final CoasterReadRepository coasterReadRepository;
    private final ObjectStorageService objectStorageService;
    private final EmbeddingService embeddingService;
    private final MatchingRepository matchingRepository;
    private final EmbeddingProperties embeddingProperties;

    public ReindexEmbeddingsService(CoasterReadRepository coasterReadRepository,
                                    ObjectStorageService objectStorageService,
                                    EmbeddingService embeddingService,
                                    MatchingRepository matchingRepository,
                                    EmbeddingProperties embeddingProperties) {
        this.coasterReadRepository = coasterReadRepository;
        this.objectStorageService = objectStorageService;
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.embeddingProperties = embeddingProperties;
    }

    @Transactional
    public int reindexAll() {
        List<CoasterReadRepository.CoasterImagePair> pairs = coasterReadRepository.findAllPairs();
        int processed = 0;
        for (CoasterReadRepository.CoasterImagePair pair : pairs) {
            byte[] frontBytes = objectStorageService.getBytes(pair.frontKey());
            byte[] backBytes = objectStorageService.getBytes(pair.backKey());

            float[] frontEmbedding = embeddingService.toEmbedding(frontBytes);
            float[] backEmbedding = embeddingService.toEmbedding(backBytes);

            matchingRepository.upsertEmbedding(
                    pair.coasterId(),
                    embeddingProperties.modelVersion(),
                    frontEmbedding,
                    backEmbedding
            );
            processed++;
        }
        return processed;
    }
}
