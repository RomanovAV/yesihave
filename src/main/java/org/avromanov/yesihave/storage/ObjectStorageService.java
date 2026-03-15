package org.avromanov.yesihave.storage;

public interface ObjectStorageService {
    String putImage(String prefix, byte[] bytes);

    byte[] getBytes(String objectKey);
}
