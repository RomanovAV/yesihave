package org.avromanov.yesihave.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@ConditionalOnProperty(prefix = "app.embedding", name = "provider", havingValue = "deterministic", matchIfMissing = true)
public class StubEmbeddingService implements EmbeddingService {
    private final int dimension;

    public StubEmbeddingService(EmbeddingProperties properties) {
        this.dimension = properties.dimension();
    }

    @Override
    public float[] toEmbedding(byte[] image) {
        byte[] digest = sha256(image);
        float[] vector = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            int unsigned = digest[i % digest.length] & 0xFF;
            vector[i] = (unsigned / 255.0f) - 0.5f;
        }

        return normalize(vector);
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private float[] normalize(float[] vector) {
        double normSquared = 0.0;
        for (float v : vector) {
            normSquared += v * v;
        }
        double norm = Math.sqrt(normSquared);
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
