package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingPipelineDescriptor;
import org.avromanov.yesihave.image.EmbeddingPipelineDescriptorProvider;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReindexEmbeddingsService {
    private final CoasterReadRepository coasterReadRepository;
    private final ObjectStorageService objectStorageService;
    private final EmbeddingService embeddingService;
    private final MatchingRepository matchingRepository;
    private final EmbeddingPipelineDescriptorProvider descriptorProvider;
    private final EmbeddingVersionRepository embeddingVersionRepository;
    private final ImageBytesNormalizer imageBytesNormalizer;

    public ReindexEmbeddingsService(CoasterReadRepository coasterReadRepository,
                                    ObjectStorageService objectStorageService,
                                    EmbeddingService embeddingService,
                                    MatchingRepository matchingRepository,
                                    EmbeddingPipelineDescriptorProvider descriptorProvider,
                                    EmbeddingVersionRepository embeddingVersionRepository,
                                    ImageBytesNormalizer imageBytesNormalizer) {
        this.coasterReadRepository = coasterReadRepository;
        this.objectStorageService = objectStorageService;
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.descriptorProvider = descriptorProvider;
        this.embeddingVersionRepository = embeddingVersionRepository;
        this.imageBytesNormalizer = imageBytesNormalizer;
    }

    public int reindexAll() {
        EmbeddingPipelineDescriptor descriptor = descriptorProvider.descriptor();
        embeddingVersionRepository.beginBuild(descriptor);
        List<CoasterReadRepository.CoasterImagePair> pairs =
                coasterReadRepository.findPairsNeedingReindex(descriptor.modelVersion());
        int processed = 0;
        for (CoasterReadRepository.CoasterImagePair pair : pairs) {
            byte[] frontBytes = normalize(objectStorageService.getBytes(pair.frontKey()));
            byte[] backBytes = normalize(objectStorageService.getBytes(pair.backKey()));

            float[] frontEmbedding = embeddingService.toEmbedding(frontBytes);
            float[] backEmbedding = embeddingService.toEmbedding(backBytes);

            matchingRepository.upsertEmbedding(
                    pair.coasterId(),
                    descriptor.modelVersion(),
                    frontEmbedding,
                    backEmbedding
            );
            processed++;
        }
        embeddingVersionRepository.activate(descriptor);
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
