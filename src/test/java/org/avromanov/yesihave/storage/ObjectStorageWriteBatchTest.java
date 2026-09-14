package org.avromanov.yesihave.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStorageWriteBatchTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesSuccessfulUploadsWhenLaterUploadFails() {
        FakeObjectStorage storage = new FakeObjectStorage(2);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);

        assertThatThrownBy(() -> batch.execute(uploads -> {
            uploads.putImage("front", new byte[]{1});
            uploads.putImage("back", new byte[]{2});
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("upload 2");

        assertThat(storage.uploaded).containsExactly("front/1");
        assertThat(storage.deleted).containsExactly("front/1");
    }

    @Test
    void deletesUploadsInReverseOrderWhenOperationFails() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);

        assertThatThrownBy(() -> batch.execute(uploads -> {
            uploads.putImage("front", new byte[]{1});
            uploads.putImage("back", new byte[]{2});
            throw new IllegalArgumentException("database write failed");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(storage.deleted).containsExactly("back/2", "front/1");
    }

    @Test
    void retainsUploadsAfterSuccessfulOperation() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);

        String result = batch.execute(uploads -> uploads.putImage("front", new byte[]{1}));

        assertThat(result).isEqualTo("front/1");
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void deletesUploadsAfterSurroundingTransactionRollsBack() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);
        TransactionSynchronizationManager.initSynchronization();

        batch.execute(uploads -> uploads.putImage("front", new byte[]{1}));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.deleted).containsExactly("front/1");
    }

    @Test
    void deletesEarlierUploadsWhenLaterDatabaseTransactionRollsBack() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);

        batch.execute(uploads -> {
            uploads.putImage("front", new byte[]{1});
            TransactionSynchronizationManager.initSynchronization();
            uploads.registerRollbackCleanup();
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
            return null;
        });

        assertThat(storage.deleted).containsExactly("front/1");
    }

    @Test
    void retainsUploadsAfterSurroundingTransactionCommits() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);
        TransactionSynchronizationManager.initSynchronization();

        batch.execute(uploads -> uploads.putImage("front", new byte[]{1}));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void retainsUploadsWhenTransactionOutcomeIsUnknown() {
        FakeObjectStorage storage = new FakeObjectStorage(0);
        ObjectStorageWriteBatch batch = new ObjectStorageWriteBatch(storage);
        TransactionSynchronizationManager.initSynchronization();

        batch.execute(uploads -> uploads.putImage("front", new byte[]{1}));
        completeTransaction(TransactionSynchronization.STATUS_UNKNOWN);

        assertThat(storage.deleted).isEmpty();
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(status);
        }
    }

    private static class FakeObjectStorage implements ObjectStorageService {
        private final int failOnUpload;
        private final List<String> uploaded = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private int uploadAttempts;

        private FakeObjectStorage(int failOnUpload) {
            this.failOnUpload = failOnUpload;
        }

        @Override
        public String putImage(String prefix, byte[] bytes) {
            uploadAttempts++;
            if (uploadAttempts == failOnUpload) {
                throw new IllegalStateException("failed upload " + uploadAttempts);
            }
            String key = prefix + "/" + uploadAttempts;
            uploaded.add(key);
            return key;
        }

        @Override
        public byte[] getBytes(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            deleted.add(objectKey);
        }

        @Override
        public void forEachObject(String prefix, Consumer<StoredObject> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }
}
