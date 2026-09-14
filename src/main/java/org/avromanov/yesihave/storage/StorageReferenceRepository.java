package org.avromanov.yesihave.storage;

import java.util.List;

public interface StorageReferenceRepository {
    boolean isReferenced(String objectKey);

    List<String> findReferencedKeysAfter(String afterObjectKey, int limit);
}
