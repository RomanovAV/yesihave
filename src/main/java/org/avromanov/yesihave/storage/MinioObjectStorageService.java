package org.avromanov.yesihave.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.avromanov.yesihave.image.ImageProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private final MinioClient minioClient;
    private final StorageProperties properties;
    private final ImageProperties imageProperties;

    public MinioObjectStorageService(MinioClient minioClient,
                                     StorageProperties properties,
                                     ImageProperties imageProperties) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.imageProperties = imageProperties;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.bucket()).build()
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure MinIO bucket", e);
        }
    }

    @Override
    public String putImage(String prefix, byte[] bytes) {
        String objectKey = prefix + "/" + UUID.randomUUID() + ".jpg";
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .stream(inputStream, bytes.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );
            return objectKey;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload image to MinIO", e);
        }
    }

    @Override
    public byte[] getBytes(String objectKey) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(objectKey)
                        .build()
        )) {
            byte[] bytes = inputStream.readNBytes((int) imageProperties.maxFileBytes() + 1);
            if (bytes.length > imageProperties.maxFileBytes()) {
                throw new IllegalStateException(
                        "Stored image exceeds configured size limit: " + objectKey
                );
            }
            return bytes;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read image from MinIO", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete image from MinIO: " + objectKey, e);
        }
    }

    @Override
    public void forEachObject(String prefix, Consumer<StoredObject> consumer) {
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(properties.bucket())
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                consumer.accept(new StoredObject(
                        item.objectName(),
                        item.lastModified().toInstant(),
                        item.size()
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list MinIO objects under " + prefix, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code)) {
                return false;
            }
            throw new IllegalStateException("Failed to inspect MinIO object: " + objectKey, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect MinIO object: " + objectKey, e);
        }
    }
}
