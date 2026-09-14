package org.avromanov.yesihave.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class StorageReconciliationServiceTest {

    @Test
    void deletesOnlyOldUnreferencedObjectsAndReportsMissingReferences() {
        Instant now = Instant.now();
        FakeObjectStorage storage = new FakeObjectStorage(List.of(
                new ObjectStorageService.StoredObject("coasters/orphan-old", now.minus(Duration.ofDays(2)), 10),
                new ObjectStorageService.StoredObject("coasters/orphan-recent", now.minus(Duration.ofMinutes(5)), 10),
                new ObjectStorageService.StoredObject("checks/referenced", now.minus(Duration.ofDays(2)), 10)
        ));
        FakeReferences references = new FakeReferences(Set.of(
                "checks/referenced",
                "checks/missing"
        ));
        StorageReconciliationService service = new StorageReconciliationService(
                storage,
                references,
                new StorageReconciliationProperties(true, true, Duration.ofHours(24), 2)
        );

        StorageReconciliationService.ReconciliationReport report = service.reconcile();

        assertThat(storage.deleted).containsExactly("coasters/orphan-old");
        assertThat(report.scannedObjects()).isEqualTo(3);
        assertThat(report.orphanObjects()).isEqualTo(1);
        assertThat(report.deletedObjects()).isEqualTo(1);
        assertThat(report.deleteFailures()).isZero();
        assertThat(report.missingReferences()).isEqualTo(1);
    }

    private static class FakeObjectStorage implements ObjectStorageService {
        private final List<StoredObject> objects;
        private final List<String> deleted = new ArrayList<>();

        private FakeObjectStorage(List<StoredObject> objects) {
            this.objects = objects;
        }

        @Override
        public String putImage(String prefix, byte[] bytes) {
            throw new UnsupportedOperationException();
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
            objects.stream()
                    .filter(object -> object.objectKey().startsWith(prefix))
                    .forEach(consumer);
        }

        @Override
        public boolean exists(String objectKey) {
            return objects.stream().anyMatch(object ->
                    object.objectKey().equals(objectKey) && !deleted.contains(objectKey)
            );
        }
    }

    private static class FakeReferences implements StorageReferenceRepository {
        private final Set<String> keys;

        private FakeReferences(Set<String> keys) {
            this.keys = new HashSet<>(keys);
        }

        @Override
        public boolean isReferenced(String objectKey) {
            return keys.contains(objectKey);
        }

        @Override
        public List<String> findReferencedKeysAfter(String afterObjectKey, int limit) {
            return keys.stream()
                    .filter(key -> afterObjectKey == null || key.compareTo(afterObjectKey) > 0)
                    .sorted(Comparator.naturalOrder())
                    .limit(limit)
                    .toList();
        }
    }
}
