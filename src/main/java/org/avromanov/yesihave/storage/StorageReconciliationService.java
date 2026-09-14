package org.avromanov.yesihave.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StorageReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(StorageReconciliationService.class);
    private static final List<String> MANAGED_PREFIXES = List.of("coasters/", "checks/");

    private final ObjectStorageService objectStorageService;
    private final StorageReferenceRepository storageReferenceRepository;
    private final StorageReconciliationProperties properties;

    public StorageReconciliationService(ObjectStorageService objectStorageService,
                                        StorageReferenceRepository storageReferenceRepository,
                                        StorageReconciliationProperties properties) {
        this.objectStorageService = objectStorageService;
        this.storageReferenceRepository = storageReferenceRepository;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${app.storage-reconciliation.initial-delay:5m}",
            fixedDelayString = "${app.storage-reconciliation.interval:24h}"
    )
    public void scheduledReconciliation() {
        if (!properties.enabled()) {
            return;
        }
        try {
            ReconciliationReport report = reconcile();
            log.info(
                    "Storage reconciliation complete: scanned={}, orphans={}, deleted={}, deleteFailures={}, missingReferences={}",
                    report.scannedObjects(),
                    report.orphanObjects(),
                    report.deletedObjects(),
                    report.deleteFailures(),
                    report.missingReferences()
            );
        } catch (RuntimeException e) {
            log.error("Storage reconciliation failed", e);
        }
    }

    public ReconciliationReport reconcile() {
        Instant orphanCutoff = Instant.now().minus(properties.orphanGracePeriod());
        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger orphans = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();
        AtomicInteger deleteFailures = new AtomicInteger();

        for (String prefix : MANAGED_PREFIXES) {
            objectStorageService.forEachObject(prefix, object -> {
                scanned.incrementAndGet();
                if (!object.lastModified().isBefore(orphanCutoff)
                        || storageReferenceRepository.isReferenced(object.objectKey())) {
                    return;
                }
                orphans.incrementAndGet();
                if (!properties.deleteOrphans()) {
                    log.warn("Unreferenced MinIO object: {}", object.objectKey());
                    return;
                }
                try {
                    objectStorageService.delete(object.objectKey());
                    deleted.incrementAndGet();
                } catch (RuntimeException e) {
                    deleteFailures.incrementAndGet();
                    log.error("Failed to delete orphan MinIO object {}", object.objectKey(), e);
                }
            });
        }

        int missingReferences = countMissingReferences();
        return new ReconciliationReport(
                scanned.get(),
                orphans.get(),
                deleted.get(),
                deleteFailures.get(),
                missingReferences
        );
    }

    private int countMissingReferences() {
        int missing = 0;
        String afterObjectKey = null;
        while (true) {
            List<String> keys = storageReferenceRepository.findReferencedKeysAfter(
                    afterObjectKey,
                    properties.referenceBatchSize()
            );
            if (keys.isEmpty()) {
                return missing;
            }
            for (String key : keys) {
                if (!objectStorageService.exists(key)) {
                    missing++;
                    log.error("Database references missing MinIO object {}", key);
                }
            }
            afterObjectKey = keys.getLast();
        }
    }

    public record ReconciliationReport(
            int scannedObjects,
            int orphanObjects,
            int deletedObjects,
            int deleteFailures,
            int missingReferences
    ) {
    }
}
