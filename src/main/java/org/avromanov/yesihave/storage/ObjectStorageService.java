package org.avromanov.yesihave.storage;

import java.time.Instant;
import java.util.function.Consumer;

public interface ObjectStorageService {
    String putImage(String prefix, byte[] bytes);

    byte[] getBytes(String objectKey);

    void delete(String objectKey);

    void forEachObject(String prefix, Consumer<StoredObject> consumer);

    boolean exists(String objectKey);

    record StoredObject(String objectKey, Instant lastModified, long size) {
    }
}
