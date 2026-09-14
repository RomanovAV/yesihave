package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.persistence.CoasterWriteRepository;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.ObjectStorageWriteBatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class CoasterCreationService {
    private final ObjectStorageWriteBatch objectStorageWriteBatch;
    private final CoasterWriteRepository coasterWriteRepository;
    private final MatchingRepository matchingRepository;
    private final EmbeddingVersionRepository embeddingVersionRepository;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties embeddingProperties;
    private final ImageBytesNormalizer imageBytesNormalizer;
    private final TransactionTemplate transactionTemplate;

    public CoasterCreationService(ObjectStorageWriteBatch objectStorageWriteBatch,
                                  CoasterWriteRepository coasterWriteRepository,
                                  MatchingRepository matchingRepository,
                                  EmbeddingVersionRepository embeddingVersionRepository,
                                  EmbeddingService embeddingService,
                                  EmbeddingProperties embeddingProperties,
                                  ImageBytesNormalizer imageBytesNormalizer,
                                  PlatformTransactionManager transactionManager) {
        this.objectStorageWriteBatch = objectStorageWriteBatch;
        this.coasterWriteRepository = coasterWriteRepository;
        this.matchingRepository = matchingRepository;
        this.embeddingVersionRepository = embeddingVersionRepository;
        this.embeddingService = embeddingService;
        this.embeddingProperties = embeddingProperties;
        this.imageBytesNormalizer = imageBytesNormalizer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public UUID create(String name,
                       byte[] frontImage,
                       byte[] backImage,
                       Consumer<UUID> afterPersist) {
        Objects.requireNonNull(afterPersist, "afterPersist");
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Coaster name must not be blank");
        }
        if (normalizedName.length() > 200) {
            throw new IllegalArgumentException("Coaster name must not exceed 200 characters");
        }

        byte[] normalizedFront = normalize(frontImage);
        byte[] normalizedBack = normalize(backImage);
        String frontSha256 = sha256(normalizedFront);
        String backSha256 = sha256(normalizedBack);
        String imagePairKey = imagePairKey(frontSha256, backSha256);
        float[] frontEmbedding = embeddingService.toEmbedding(normalizedFront);
        float[] backEmbedding = embeddingService.toEmbedding(normalizedBack);
        UUID coasterId = UUID.randomUUID();

        return objectStorageWriteBatch.execute(uploads -> {
            String frontKey = uploads.putImage("coasters/front", normalizedFront);
            String backKey = uploads.putImage("coasters/back", normalizedBack);

            UUID result = transactionTemplate.execute(status -> {
                uploads.registerRollbackCleanup();
                embeddingVersionRepository.requireActiveForWrite(embeddingProperties.modelVersion());
                coasterWriteRepository.insertCoaster(coasterId, normalizedName);
                coasterWriteRepository.insertImage(
                        UUID.randomUUID(),
                        coasterId,
                        "FRONT",
                        frontKey,
                        frontSha256
                );
                coasterWriteRepository.insertImage(
                        UUID.randomUUID(),
                        coasterId,
                        "BACK",
                        backKey,
                        backSha256
                );
                coasterWriteRepository.claimImagePair(imagePairKey, coasterId);
                matchingRepository.upsertEmbedding(
                        coasterId,
                        embeddingProperties.modelVersion(),
                        frontEmbedding,
                        backEmbedding
                );
                afterPersist.accept(coasterId);
                return coasterId;
            });
            return Objects.requireNonNull(result, "Coaster transaction returned no result");
        });
    }

    private byte[] normalize(byte[] image) {
        try {
            return imageBytesNormalizer.normalize(image);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to normalize image", e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String imagePairKey(String frontSha256, String backSha256) {
        if (frontSha256.compareTo(backSha256) <= 0) {
            return frontSha256 + ":" + backSha256;
        }
        return backSha256 + ":" + frontSha256;
    }
}
