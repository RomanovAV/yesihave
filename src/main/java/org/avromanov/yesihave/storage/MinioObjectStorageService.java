package org.avromanov.yesihave.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
public class MinioObjectStorageService implements ObjectStorageService {
    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioObjectStorageService(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
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
}
