package org.avromanov.yesihave.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Tracks uploaded objects and deletes them if the surrounding operation fails.
 * When called inside an existing Spring transaction, it also compensates a
 * rollback that happens after the operation has returned to its caller.
 */
@Service
public class ObjectStorageWriteBatch {
    private static final Logger log = LoggerFactory.getLogger(ObjectStorageWriteBatch.class);

    private final ObjectStorageService objectStorageService;

    public ObjectStorageWriteBatch(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public <T> T execute(Function<UploadScope, T> operation) {
        UploadBatch batch = new UploadBatch();
        try {
            return operation.apply(batch);
        } catch (RuntimeException | Error failure) {
            batch.cleanup(failure);
            throw failure;
        }
    }

    public interface UploadScope {
        String putImage(String prefix, byte[] bytes);

        void registerRollbackCleanup();
    }

    private final class UploadBatch implements UploadScope {
        private final List<String> uploadedKeys = new ArrayList<>();
        private boolean rollbackCleanupRegistered;
        private boolean transactionCommitted;
        private boolean transactionOutcomeUnknown;
        private boolean cleaned;

        @Override
        public String putImage(String prefix, byte[] bytes) {
            String objectKey = objectStorageService.putImage(prefix, bytes);
            uploadedKeys.add(objectKey);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                registerRollbackCleanup();
            }
            return objectKey;
        }

        @Override
        public synchronized void registerRollbackCleanup() {
            if (rollbackCleanupRegistered) {
                return;
            }
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("No active transaction synchronization for object-storage writes");
            }
            rollbackCleanupRegistered = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        markTransactionCommitted();
                    } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        cleanup(null);
                    } else {
                        markTransactionOutcomeUnknown();
                        log.error(
                                "Database transaction outcome is unknown; retaining uploaded objects for reconciliation: {}",
                                uploadedKeys
                        );
                    }
                }
            });
        }

        private synchronized void cleanup(Throwable originalFailure) {
            if (cleaned) {
                return;
            }
            if (transactionCommitted || transactionOutcomeUnknown) {
                log.error(
                        "Not compensating object-storage writes because the database transaction committed or its outcome is unknown: {}",
                        uploadedKeys,
                        originalFailure
                );
                return;
            }
            cleaned = true;
            for (int i = uploadedKeys.size() - 1; i >= 0; i--) {
                String objectKey = uploadedKeys.get(i);
                try {
                    objectStorageService.delete(objectKey);
                } catch (RuntimeException cleanupFailure) {
                    log.error("Failed to compensate object-storage write for {}", objectKey, cleanupFailure);
                    if (originalFailure != null) {
                        originalFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
        }

        private synchronized void markTransactionCommitted() {
            transactionCommitted = true;
        }

        private synchronized void markTransactionOutcomeUnknown() {
            transactionOutcomeUnknown = true;
        }
    }
}
