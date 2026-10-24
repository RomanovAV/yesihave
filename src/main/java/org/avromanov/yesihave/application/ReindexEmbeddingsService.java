package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingPipelineDescriptor;
import org.avromanov.yesihave.image.EmbeddingPipelineDescriptorProvider;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImagePairFingerprint;
import org.avromanov.yesihave.persistence.CheckEmbeddingRepository;
import org.avromanov.yesihave.persistence.CoasterReadRepository;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReindexEmbeddingsService {
    private static final int BATCH_SIZE = 100;

    private final CoasterReadRepository coasterReadRepository;
    private final ObjectStorageService objectStorageService;
    private final EmbeddingService embeddingService;
    private final MatchingRepository matchingRepository;
    private final EmbeddingPipelineDescriptorProvider descriptorProvider;
    private final EmbeddingVersionRepository embeddingVersionRepository;
    private final ImageBytesNormalizer imageBytesNormalizer;
    private final CheckEmbeddingRepository checkEmbeddingRepository;

    public ReindexEmbeddingsService(CoasterReadRepository coasterReadRepository,
                                    ObjectStorageService objectStorageService,
                                    EmbeddingService embeddingService,
                                    MatchingRepository matchingRepository,
                                    EmbeddingPipelineDescriptorProvider descriptorProvider,
                                    EmbeddingVersionRepository embeddingVersionRepository,
                                    ImageBytesNormalizer imageBytesNormalizer,
                                    CheckEmbeddingRepository checkEmbeddingRepository) {
        this.coasterReadRepository = coasterReadRepository;
        this.objectStorageService = objectStorageService;
        this.embeddingService = embeddingService;
        this.matchingRepository = matchingRepository;
        this.descriptorProvider = descriptorProvider;
        this.embeddingVersionRepository = embeddingVersionRepository;
        this.imageBytesNormalizer = imageBytesNormalizer;
        this.checkEmbeddingRepository = checkEmbeddingRepository;
    }

    public int reindexAll() {
        EmbeddingPipelineDescriptor descriptor = descriptorProvider.descriptor();
        embeddingVersionRepository.beginBuild(descriptor);
        int processed = 0;
        UUID afterCoasterId = null;
        while (true) {
            List<CoasterReadRepository.CoasterImagePair> pairs =
                    coasterReadRepository.findPairsNeedingReindex(
                            descriptor.modelVersion(),
                            afterCoasterId,
                            BATCH_SIZE
                    );
            if (pairs.isEmpty()) {
                break;
            }
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
            afterCoasterId = pairs.getLast().coasterId();
        }
        processed += reindexConfirmed(descriptor.modelVersion());
        embeddingVersionRepository.activate(descriptor);
        return processed;
    }

    private int reindexConfirmed(String modelVersion) {
        int processed = 0;
        UUID afterId = null;
        while (true) {
            var pairs = checkEmbeddingRepository.findConfirmedNeedingReindex(modelVersion, afterId, BATCH_SIZE);
            if (pairs.isEmpty()) {
                return processed;
            }
            for (var pair : pairs) {
                byte[] front = normalize(objectStorageService.getBytes(pair.frontKey()));
                byte[] back = normalize(objectStorageService.getBytes(pair.backKey()));
                checkEmbeddingRepository.upsert(pair.checkRequestId(), modelVersion,
                        ImagePairFingerprint.of(front, back),
                        embeddingService.toEmbedding(front), embeddingService.toEmbedding(back));
                processed++;
            }
            afterId = pairs.getLast().checkRequestId();
        }
    }

    private byte[] normalize(byte[] image) {
        try {
            return imageBytesNormalizer.normalize(image);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to normalize image", e);
        }
    }
}
