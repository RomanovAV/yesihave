package org.avromanov.yesihave.application;

import org.avromanov.yesihave.image.EmbeddingProperties;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImagePairFingerprint;
import org.avromanov.yesihave.persistence.CheckAuditRepository;
import org.avromanov.yesihave.persistence.CheckEmbeddingRepository;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Handles late confirmations of checks made before query caching or a model upgrade. */
@Component
public class ConfirmedReferenceIndexer {
    private static final Logger log = LoggerFactory.getLogger(ConfirmedReferenceIndexer.class);
    private final CheckEmbeddingRepository checks;
    private final CheckAuditRepository audit;
    private final ObjectStorageService storage;
    private final ImageBytesNormalizer normalizer;
    private final EmbeddingService embeddings;
    private final EmbeddingProperties properties;
    private final EmbeddingVersionRepository versions;
    private final TransactionTemplate transaction;

    public ConfirmedReferenceIndexer(CheckEmbeddingRepository checks, CheckAuditRepository audit,
                                     ObjectStorageService storage, ImageBytesNormalizer normalizer,
                                     EmbeddingService embeddings, EmbeddingProperties properties,
                                     EmbeddingVersionRepository versions, PlatformTransactionManager manager) {
        this.checks = checks;
        this.audit = audit;
        this.storage = storage;
        this.normalizer = normalizer;
        this.embeddings = embeddings;
        this.properties = properties;
        this.versions = versions;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    public void onConfirmed(ConfirmedCheck event) {
        try {
            if (checks.exists(event.checkRequestId(), properties.modelVersion())) {
                return;
            }
            var pair = audit.requireImageKeys(event.checkRequestId());
            byte[] front = normalizer.normalize(storage.getBytes(pair.frontKey()));
            byte[] back = normalizer.normalize(storage.getBytes(pair.backKey()));
            float[] fv = embeddings.toEmbedding(front);
            float[] bv = embeddings.toEmbedding(back);
            // AFTER_COMMIT must save in a new transaction; feedback remains valid
            // if indexing fails and can be recovered by the reindex CLI.
            transaction.executeWithoutResult(status -> {
                versions.requireActiveForWrite(properties.modelVersion());
                checks.upsert(event.checkRequestId(), properties.modelVersion(),
                        ImagePairFingerprint.of(front, back), fv, bv);
            });
        } catch (Exception failure) {
            log.warn("Confirmed check {} needs reference reindex: {}", event.checkRequestId(),
                    failure.getClass().getSimpleName());
        }
    }

    public record ConfirmedCheck(UUID checkRequestId) {
    }
}
