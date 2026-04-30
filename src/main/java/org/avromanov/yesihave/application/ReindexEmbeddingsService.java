package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
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
    private final ImageBytesNormalizer imageBytesNormalizer;

    public ReindexEmbeddingsService(CoasterReadRepository coasterReadRepository,
                                    ObjectStorageService objectStorageService,
                                    EmbeddingService embeddingService,
                                    MatchingRepository matchingRepository,
                                    EmbeddingProperties embeddingProperties,
                                    ImageBytesNormalizer imageBytesNormalizer) {
        this.coasterReadRepository = coasterReadRepository;
        this.objectStorageService = objectStorageService;
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.embeddingProperties = embeddingProperties;
        this.imageBytesNormalizer = imageBytesNormalizer;
    }

    @Transactional
    public int reindexAll() {
        List<CoasterReadRepository.CoasterImagePair> pairs =
                coasterReadRepository.findPairsNeedingReindex(embeddingProperties.modelVersion());
        int processed = 0;
        for (CoasterReadRepository.CoasterImagePair pair : pairs) {
            byte[] frontBytes = normalize(objectStorageService.getBytes(pair.frontKey()));
            byte[] backBytes = normalize(objectStorageService.getBytes(pair.backKey()));

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

    private byte[] normalize(byte[] image) {
        try {
            return imageBytesNormalizer.normalize(image);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to normalize image", e);
        }
    }
}
